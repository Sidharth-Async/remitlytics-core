package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);
}
