package com.remitlytics.core_engine.dto;

public record InvoiceBreakdown(
        Long baseAmountCents,
        Long platformFeeCents,
        Long taxCents,
        Long totalCents
){}
