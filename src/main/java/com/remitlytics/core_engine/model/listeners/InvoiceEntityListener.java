package com.remitlytics.core_engine.model.listeners;

import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreUpdate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InvoiceEntityListener {

    private final Map<UUID, InvoiceStatus> loadedStatuses = new ConcurrentHashMap<>();

    @PostLoad
    public void onPostLoad(Invoice invoice) {
        if (invoice.getId() != null) {
            loadedStatuses.put(invoice.getId(), invoice.getStatus());
        }
    }

    @PreUpdate
    public void onPreUpdate(Invoice invoice) {
        if (invoice.getId() == null) {
            return;
        }

        InvoiceStatus originalLoadedStatus = loadedStatuses.get(invoice.getId());

        // Block updates ONLY if it was ALREADY in a terminal state when loaded from the database
        if (originalLoadedStatus == InvoiceStatus.PAID || originalLoadedStatus == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Security Violation: Invoice " + invoice.getId() + " is in terminal state [" + originalLoadedStatus + "] and cannot be modified."
            );
        }

        // Update stored status for any subsequent operations in the same transaction
        loadedStatuses.put(invoice.getId(), invoice.getStatus());
    }
}