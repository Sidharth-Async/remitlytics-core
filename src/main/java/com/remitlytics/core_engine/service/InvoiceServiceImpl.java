package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.entities.InvoiceAuditLog;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import com.remitlytics.core_engine.repository.ClientRepository;
import com.remitlytics.core_engine.repository.InvoiceAuditLogRepository;
import com.remitlytics.core_engine.repository.InvoiceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements  InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository  clientRepository;
    private final InvoiceAuditLogRepository invoiceAuditLogRepository;

    @Transactional
    public Invoice createDraftInvoice(UUID clientId, Long amountCents, java.time.LocalDate dueDate) {
        clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        Invoice invoice = new Invoice();
        invoice.setAmountCents(amountCents);
        invoice.setDueDate(dueDate);
        invoice.setStatus(InvoiceStatus.DRAFT);
        Invoice savedInvoice = invoiceRepository.save(invoice);
        InvoiceAuditLog auditLog = new InvoiceAuditLog();
        auditLog.setInvoice(invoice);
        auditLog.setPreviousStatus(null);
        auditLog.setNewStatus(InvoiceStatus.DRAFT);
        auditLog.setReason("Invoice initialized as Draft via API");
        invoiceAuditLogRepository.save(auditLog);
        return invoice;
    }

}
