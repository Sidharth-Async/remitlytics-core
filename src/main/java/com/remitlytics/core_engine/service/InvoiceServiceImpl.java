package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.InvoiceBreakdown;
import com.remitlytics.core_engine.model.entities.Client;
import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.entities.InvoiceAuditLog;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import com.remitlytics.core_engine.repository.ClientRepository;
import com.remitlytics.core_engine.repository.InvoiceAuditLogRepository;
import com.remitlytics.core_engine.repository.InvoiceRepository;
import org.springframework.transaction.annotation.Transactional; // Preferred over jakarta.transaction for Spring features
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final InvoiceAuditLogRepository invoiceAuditLogRepository;
    private final InvoiceCalculationService invoiceCalculationService;

    @Override
    @Transactional
    public Invoice createDraftInvoice(UUID clientId, Long amountCents, LocalDate dueDate) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        /*Invoke the calculation service using the incoming raw amount, a fixed platform fee of
            1.5 (percent), and a default tax rate of 18.0 (percent).*/
        InvoiceBreakdown breakdown = new InvoiceCalculationServiceImpl()
                .calculate(amountCents, 1.5, 18.0);

        Invoice invoice = new Invoice();
        invoice.setAmountCents(amountCents);
        invoice.setDueDate(dueDate);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setClient(client); // <-- FIX: Connects the invoice to your client row

        /*Map the values out of the returned InvoiceBreakdown record directly onto the
            Invoice entity fields before invoking invoiceRepository.save(entity).*/

        invoice.setPlatformFeeCents(breakdown.platformFeeCents());
        invoice.setTaxCents(breakdown.taxCents());
        invoice.setTotalCents(breakdown.totalCents());

        Invoice savedInvoice = invoiceRepository.save(invoice);

        InvoiceAuditLog auditLog = new InvoiceAuditLog();
        auditLog.setInvoice(savedInvoice);
        auditLog.setPreviousStatus(null);
        auditLog.setNewStatus(InvoiceStatus.DRAFT);
        auditLog.setReason("Invoice initialized as Draft via API");
        invoiceAuditLogRepository.save(auditLog);

        return savedInvoice;
    }

    @Override
    @Transactional
    public Invoice updateInvoiceStatus(UUID invoiceId, InvoiceStatus newStatus, String reason) {
        // 1. Fetch the invoice
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        InvoiceStatus currentStatus = invoice.getStatus();

        // 2. Guard Rail: Validate the State Machine transitions
        if (currentStatus == newStatus) {
            return invoice; // No change needed
        }

        boolean isValidTransition = switch (currentStatus) {
            case DRAFT -> newStatus == InvoiceStatus.SENT || newStatus == InvoiceStatus.CANCELLED;
            case SENT -> newStatus == InvoiceStatus.PAID || newStatus == InvoiceStatus.OVERDUE || newStatus == InvoiceStatus.CANCELLED;
            case OVERDUE -> newStatus == InvoiceStatus.PAID || newStatus == InvoiceStatus.CANCELLED;
            default ->  false; // Terminal states cannot change
        };

        if (!isValidTransition) {
            throw new IllegalStateException("Illegal state transition from " + currentStatus + " to " + newStatus);
        }

        // 3. Apply the new status
        invoice.setStatus(newStatus);
        Invoice updatedInvoice = invoiceRepository.save(invoice);

        // 4. Log the change to the Audit Ledger
        InvoiceAuditLog auditLog = new InvoiceAuditLog();
        auditLog.setInvoice(updatedInvoice);
        auditLog.setPreviousStatus(currentStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setReason(reason);
        invoiceAuditLogRepository.save(auditLog);

        return updatedInvoice;
    }
}