package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.WebhookDeliveryResult;
import com.remitlytics.core_engine.dto.WebhookPayload;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface WebhookDispatcherService {
    WebhookDeliveryResult dispatchWithRetry(UUID tenantId, String targetUrl, WebhookPayload payload);
}
