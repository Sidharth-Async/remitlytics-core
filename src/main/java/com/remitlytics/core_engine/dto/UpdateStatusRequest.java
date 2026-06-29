package com.remitlytics.core_engine.dto;

import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull InvoiceStatus status,
        @NotNull String reason
) {}
