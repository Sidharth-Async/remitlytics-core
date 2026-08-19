package com.remitlytics.core_engine.dto;

public record WebhookDeliveryResult(
        boolean delivered,
        int attemptCount,
        String errorMessage
) {
    public boolean isDelivered() {
        return delivered;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}