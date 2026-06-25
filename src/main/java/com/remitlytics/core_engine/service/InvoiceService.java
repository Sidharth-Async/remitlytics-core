package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.model.entities.Invoice;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public interface InvoiceService{
    Invoice createDraftInvoice(UUID clientId, Long amountCents, LocalDate dueDate);
}
