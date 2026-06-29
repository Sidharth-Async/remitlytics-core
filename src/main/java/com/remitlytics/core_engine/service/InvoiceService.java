package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public interface InvoiceService {
    Invoice createDraftInvoice(UUID clientId, Long amountCents, LocalDate dueDate);

    Invoice updateInvoiceStatus(UUID invoiceId, InvoiceStatus newStatus, String reason);
}