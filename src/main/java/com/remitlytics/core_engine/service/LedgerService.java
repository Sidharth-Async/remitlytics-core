package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.model.entities.Invoice;

public interface LedgerService {
    void recordInvoicePayment(Invoice invoice);
}