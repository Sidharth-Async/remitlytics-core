package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
}
