package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.InvoiceBreakdown;
import com.remitlytics.core_engine.dto.InvoiceResponse;
import com.remitlytics.core_engine.dto.WebhookEvent;
import com.remitlytics.core_engine.dto.WebhookPayload;
import com.remitlytics.core_engine.event.InvoiceSentEvent;
import com.remitlytics.core_engine.event.WebhookDispatchEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final LedgerService ledgerService;
    private final ApplicationEventPublisher eventPublisher;
    private final WebhookDispatcherService webhookDispatcherService;

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found with ID: " + id));

        return mapToResponse(invoice);
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

        InvoiceBreakdown breakdown = invoiceCalculationService.calculate(amountCents, 1.5, 18.0);

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

        // Audit Log: Creation
        InvoiceAuditLog auditLog = new InvoiceAuditLog();
        auditLog.setInvoice(savedInvoice);
        auditLog.setPreviousStatus(null);
        auditLog.setNewStatus(InvoiceStatus.DRAFT);
        auditLog.setReason("Invoice initialized as Draft via API for tenant: " + tenant.getCompanyName());
        invoiceAuditLogRepository.save(auditLog);

        return savedInvoice;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getAllInvoicesForTenant(String apiKey) {
        UUID currentTenantId = TenantContext.getCurrentTenant();
        List<Invoice> invoices = (currentTenantId != null)
                ? invoiceRepository.findByTenantId(currentTenantId)
                : invoiceRepository.findAll();

        return invoices.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public InvoiceResponse updateInvoiceStatus(UUID invoiceId, InvoiceStatus newStatus, String reason) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        InvoiceStatus currentStatus = invoice.getStatus();

        if (currentStatus == newStatus) {
            return mapToResponse(invoice);
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

        // Audit Log: State transition
        InvoiceAuditLog auditLog = new InvoiceAuditLog();
        auditLog.setInvoice(updatedInvoice);
        auditLog.setPreviousStatus(currentStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setReason(reason != null ? reason : "Manual status transition to " + newStatus);
        invoiceAuditLogRepository.save(auditLog);


        // Trigger Ledger double-entry postings when moving to PAID
        if (newStatus == InvoiceStatus.PAID) {

            ledgerService.recordInvoicePayment(updatedInvoice);
            String targetUrl = updatedInvoice.getClient().getWebhookUrl();

            // CORRECTED LOGIC: If it is NOT null and NOT blank
            if (targetUrl != null && !targetUrl.isBlank()) {
                WebhookPayload payload = new WebhookPayload(
                        updatedInvoice.getStatus().name(),
                        updatedInvoice.getId(),
                        updatedInvoice.getAmountCents()
                );

                WebhookDispatchEvent event = new WebhookDispatchEvent(
                        updatedInvoice.getTenant().getId(),
                        "invoice.paid",
                        targetUrl,
                        payload
                );
                eventPublisher.publishEvent(event);
            }
        }

        if (newStatus == InvoiceStatus.SENT) {
            InvoiceResponse response = mapToResponse(invoice);
            eventPublisher.publishEvent(new InvoiceSentEvent(
                    response,
                    invoice.getClient().getEmail(),
                    invoice.getClient().getName()
            ));
        }

        return mapToResponse(updatedInvoice);
    }

    @Override
    @Transactional
    public InvoiceResponse processPaymentWebhook(WebhookEvent event, Long amountCents) {
        UUID invoiceId = UUID.fromString(event.invoiceId());
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return mapToResponse(invoice);
        }

        if (event.amountReceivedCents() == (amountCents)) {
            return updateInvoiceStatus(invoiceId, InvoiceStatus.PAID, "Payment confirmed via webhook. Ref: " + event.transactionId());
        }

        return mapToResponse(invoice);
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
}