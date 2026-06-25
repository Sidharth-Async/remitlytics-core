package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.InvoiceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InvoiceAuditLogRepository extends JpaRepository<InvoiceAuditLog, UUID> {
}
