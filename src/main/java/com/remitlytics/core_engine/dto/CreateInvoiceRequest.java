package com.remitlytics.core_engine.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull(message = "Client ID is required")
        UUID clientId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        Long amountCents,

        @NotNull(message = "Due date is required")
        LocalDate dueDate
) {}