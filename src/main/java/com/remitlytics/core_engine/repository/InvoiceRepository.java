package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<Invoice> findByTenantId(UUID tenantId, Pageable pageable);
    List<Invoice> findByTenantId(UUID tenantId); // Added non-paginated lookup
}