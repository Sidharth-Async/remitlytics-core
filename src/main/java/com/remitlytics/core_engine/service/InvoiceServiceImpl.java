package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.InvoiceBreakdown;
import com.remitlytics.core_engine.dto.InvoiceResponse;
import com.remitlytics.core_engine.dto.WebhookEvent;
import com.remitlytics.core_engine.model.entities.Client;
import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.entities.InvoiceAuditLog;
import com.remitlytics.core_engine.model.entities.Tenant;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import com.remitlytics.core_engine.repository.ClientRepository;
import com.remitlytics.core_engine.repository.InvoiceAuditLogRepository;
import com.remitlytics.core_engine.repository.InvoiceRepository;
import com.remitlytics.core_engine.repository.TenantRepository;
import com.remitlytics.core_engine.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final InvoiceAuditLogRepository invoiceAuditLogRepository;
    private final InvoiceCalculationService invoiceCalculationService;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found with ID: " + id));

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getClient().getId(),
                invoice.getClient().getName(),
                invoice.getAmountCents(),
                invoice.getPlatformFeeCents(),
                invoice.getTaxCents(),
                invoice.getTotalCents(),
                invoice.getStatus().name(),
                invoice.getDueDate().toString(),
                invoice.getCreatedAt().toString()
        );
    }

    @Override
    @Transactional
    public Invoice createDraftInvoice(UUID clientId, Long amountCents, LocalDate dueDate) {

        UUID currentTenantId = TenantContext.getCurrentTenant();
        if (currentTenantId == null) {
            throw new IllegalStateException("No active tenant context found");
        }

        Tenant tenant = tenantRepository.findById(currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        /* Use injected calculation service spring bean */
        InvoiceBreakdown breakdown = invoiceCalculationService
                .calculate(amountCents, 1.5, 18.0);

        Invoice invoice = new Invoice();
        invoice.setTenant(tenant);
        invoice.setAmountCents(amountCents);
        invoice.setDueDate(dueDate);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setClient(client);

        invoice.setPlatformFeeCents(breakdown.platformFeeCents());
        invoice.setTaxCents(breakdown.taxCents());
        invoice.setTotalCents(breakdown.totalCents());

        Invoice savedInvoice = invoiceRepository.save(invoice);

        InvoiceAuditLog auditLog = new InvoiceAuditLog();
        auditLog.setInvoice(savedInvoice);
        auditLog.setPreviousStatus(null);
        auditLog.setNewStatus(InvoiceStatus.DRAFT);
        auditLog.setReason("Invoice initialized as Draft via API for tenant: " + tenant.getCompanyName());
        invoiceAuditLogRepository.save(auditLog);

        return savedInvoice;
    }

    @Override
    public List<InvoiceResponse> getAllInvoicesForTenant(String apiKey) {
        // Fetch entities from PostgreSQL
        List<Invoice> invoices = invoiceRepository.findAll();

        // Map entities to DTOs
        return invoices.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private InvoiceResponse mapToResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getClient().getId(),
                invoice.getClient().getName(),
                invoice.getAmountCents(),
                invoice.getPlatformFeeCents(),
                invoice.getTaxCents(),
                invoice.getTotalCents(),
                invoice.getStatus().name(),
                invoice.getDueDate().toString(),
                invoice.getCreatedAt().toString()
        );
    }

    @Override
    @Transactional
    public Invoice updateInvoiceStatus(UUID invoiceId, InvoiceStatus newStatus, String reason) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        InvoiceStatus currentStatus = invoice.getStatus();

        if (currentStatus == newStatus) {
            return invoice;
        }

        boolean isValidTransition = switch (currentStatus) {
            case DRAFT -> newStatus == InvoiceStatus.SENT || newStatus == InvoiceStatus.CANCELLED;
            case SENT -> newStatus == InvoiceStatus.PAID || newStatus == InvoiceStatus.OVERDUE || newStatus == InvoiceStatus.CANCELLED;
            case OVERDUE -> newStatus == InvoiceStatus.PAID || newStatus == InvoiceStatus.CANCELLED;
            default -> false;
        };

        if (!isValidTransition) {
            throw new IllegalStateException("Illegal state transition from " + currentStatus + " to " + newStatus);
        }

        invoice.setStatus(newStatus);
        Invoice updatedInvoice = invoiceRepository.save(invoice);

        InvoiceAuditLog auditLog = new InvoiceAuditLog();
        auditLog.setInvoice(updatedInvoice);
        auditLog.setPreviousStatus(currentStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setReason(reason);
        invoiceAuditLogRepository.save(auditLog);

        return updatedInvoice;
    }

    @Transactional
    @Override
    public Invoice processPaymentWebhook(WebhookEvent event, Long amountCents) {
        UUID invoiceId = UUID.fromString(event.invoiceId());
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return invoice;
        }

        if (event.amountReceivedCents() == amountCents) {
            return updateInvoiceStatus(invoiceId, InvoiceStatus.PAID, "Payment confirmed via webhook. Transaction Ref: " + event.transactionId());
        }

        return invoice;
    }

    @Override
    @Transactional
    public int processOverdueInvoices() {
        LocalDate today = LocalDate.now();
        List<Invoice> expiredInvoices = invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.SENT, today);

        int count = 0;
        for (Invoice invoice : expiredInvoices) {
            updateInvoiceStatus(
                    invoice.getId(),
                    InvoiceStatus.OVERDUE,
                    "Automated Cron Engine: Payment due date [" + invoice.getDueDate() + "] passed."
            );
            count++;
        }
        return count;
    }


}