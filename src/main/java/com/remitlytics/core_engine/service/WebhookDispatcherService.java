package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.WebhookDeliveryResult;
import com.remitlytics.core_engine.dto.WebhookPayload;
import com.remitlytics.core_engine.event.WebhookDispatchEvent;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface WebhookDispatcherService {
    WebhookDeliveryResult dispatchWithRetry(UUID tenantId,String eventType, String targetUrl, WebhookPayload payload);
    void handleWebhookEvent(WebhookDispatchEvent event);

}
