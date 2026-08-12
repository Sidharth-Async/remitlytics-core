package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.InvoiceResponse;
import com.remitlytics.core_engine.dto.WebhookEvent;
import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public interface InvoiceService {
    Invoice createDraftInvoice(UUID clientId, Long amountCents, LocalDate dueDate);

    Invoice updateInvoiceStatus(UUID invoiceId, InvoiceStatus newStatus, String reason);

    Invoice processPaymentWebhook(WebhookEvent event, Long amountCents);

    int processOverdueInvoices();

    InvoiceResponse getInvoiceById(UUID id);

    List<InvoiceResponse> getAllInvoicesForTenant(String apiKey);
}