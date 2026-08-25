package com.remitlytics.core_engine.event;

import com.remitlytics.core_engine.dto.WebhookPayload;
import java.util.UUID;

public record WebhookDispatchEvent(
        UUID tenantId,
        String eventType,
        String targetUrl,
        WebhookPayload payload
) {}