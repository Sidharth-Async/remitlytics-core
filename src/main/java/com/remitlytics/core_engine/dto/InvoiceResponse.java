package com.remitlytics.core_engine.dto;

import com.remitlytics.core_engine.model.enums.InvoiceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID clientId,
        String clientName,
        Long amountCents,
        InvoiceStatus status,
        LocalDate dueDate,
        LocalDateTime createdAt
) {}
