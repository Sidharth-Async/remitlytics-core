package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.InvoiceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceAuditLogRepository extends JpaRepository<InvoiceAuditLog, UUID> {
    List<InvoiceAuditLog> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
}