package com.remitlytics.core_engine.dto;

public record WebhookEvent(
        String eventType,
        String invoiceId,
        String transactionId,
        long amountReceivedCents
) {}
