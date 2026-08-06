package com.remitlytics.core_engine.model.listeners;

import com.remitlytics.core_engine.exception.ReadOnlyLedgerException;
import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import jakarta.persistence.PreUpdate;

public class InvoiceEntityListener {
    @PreUpdate
    public void verifyImmutability(Invoice invoice){
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.CANCELLED){
            throw new ReadOnlyLedgerException(
                    "Security Violation: Invoice " + invoice.getId() +
                            " is in terminal state [" + invoice.getStatus() + "] and cannot be modified."
            );
        }
    }
}
