package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.entities.LedgerTransaction;

public interface LedgerService {
    LedgerTransaction recordInvoicePayment(Invoice invoice);
}