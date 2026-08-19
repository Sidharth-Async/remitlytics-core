package com.remitlytics.core_engine.dto;

import java.util.UUID;

public record WebhookPayload(
        String eventType,
        UUID eventId,
        Long amountCents
) {}
