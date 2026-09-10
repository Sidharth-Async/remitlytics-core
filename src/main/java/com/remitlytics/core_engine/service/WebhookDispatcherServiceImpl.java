package com.remitlytics.core_engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitlytics.core_engine.dto.WebhookDeliveryResult;
import com.remitlytics.core_engine.dto.WebhookPayload;
import com.remitlytics.core_engine.event.WebhookDispatchEvent;
import com.remitlytics.core_engine.model.entities.WebhookDeliveryLog;
import com.remitlytics.core_engine.model.enums.DeliveryStatus;
import com.remitlytics.core_engine.repository.WebhookDeliveryLogRepository;
import com.remitlytics.core_engine.security.WebhookSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatcherServiceImpl implements WebhookDispatcherService {

    private final RestTemplate restTemplate;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final WebhookSigner webhookSigner;
    private final ObjectMapper objectMapper;

    @Value("${remitlytics.webhook.max-retries:3}")
    private int maxRetries;

    @Value("${remitlytics.webhook.backoff-ms:100}")
    private long backoffMs;

    @Value("${remitlytics.webhook.signing-secret:whsec_test_secret_key_12345}")
    private String webhookSigningSecret;

    @Override
    public WebhookDeliveryResult dispatchWithRetry(UUID tenantId,String eventType, String targetUrl, WebhookPayload payload) {

        String payloadJson = "";
        
        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setTenantId(tenantId);
        deliveryLog.setTargetUrl(targetUrl);
        deliveryLog.setStatus(DeliveryStatus.PENDING);
        deliveryLog.setEventType(eventType);
        deliveryLog.setPayload(payloadJson);
        
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
            deliveryLog.setPayload(payloadJson);
        } catch (JsonProcessingException e) {
            deliveryLog.setStatus(DeliveryStatus.DEAD_LETTER);
            deliveryLog.setLastErrorMessage("JSON Serialization error: " + e.getMessage());
            deliveryLogRepository.save(deliveryLog);
            return new WebhookDeliveryResult(false, 0, e.getMessage());
        }

        long timestamp = Instant.now().getEpochSecond();
        String signature = webhookSigner.generateSignature(payloadJson, timestamp, webhookSigningSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Signature-Timestamp", String.valueOf(timestamp));
        headers.set("X-Signature", signature);

        HttpEntity<String> httpEntity = new HttpEntity<>(payloadJson, headers);

        int attempts = 0;
        String lastError = null;

        while (attempts < maxRetries) {
            attempts++;
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, httpEntity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    deliveryLog.setAttempts(attempts);
                    deliveryLog.setStatus(DeliveryStatus.DELIVERED);
                    deliveryLogRepository.save(deliveryLog);
                    return new WebhookDeliveryResult(true, attempts, null);
                } else {
                    lastError = "HTTP " + response.getStatusCode().value();
                }
            } catch (Exception ex) {
                lastError = ex.getMessage();
            }

            if (attempts < maxRetries) {
                try {
                    Thread.sleep(backoffMs * (1L << (attempts - 1)));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        deliveryLog.setAttempts(attempts);
        deliveryLog.setStatus(DeliveryStatus.DEAD_LETTER);
        deliveryLog.setLastErrorMessage(lastError);
        deliveryLogRepository.save(deliveryLog);

        return new WebhookDeliveryResult(false, attempts, lastError);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleWebhookEvent(WebhookDispatchEvent event) {
        // This now runs in a completely separate background thread!
        dispatchWithRetry(
                event.tenantId(),
                event.eventType(),
                event.targetUrl(),
                event.payload()
        );
    }

    @Override
    public WebhookDeliveryResult retryExistingLog(WebhookDeliveryLog deliveryLog) {
        String payloadJson = deliveryLog.getPayload();
        long timestamp = Instant.now().getEpochSecond();
        String signature = webhookSigner.generateSignature(payloadJson, timestamp, webhookSigningSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Signature-Timestamp", String.valueOf(timestamp));
        headers.set("X-Signature", signature);

        HttpEntity<String> httpEntity = new HttpEntity<>(payloadJson, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(deliveryLog.getTargetUrl(), httpEntity, String.class);
            deliveryLog.setAttempts(deliveryLog.getAttempts() + 1);

            if (response.getStatusCode().is2xxSuccessful()) {
                deliveryLog.setStatus(DeliveryStatus.DELIVERED);
                deliveryLog.setLastErrorMessage(null);
                deliveryLogRepository.save(deliveryLog);
                return new WebhookDeliveryResult(true, deliveryLog.getAttempts(), null);
            } else {
                String error = "HTTP " + response.getStatusCode().value();
                deliveryLog.setLastErrorMessage(error);
                deliveryLogRepository.save(deliveryLog);
                return new WebhookDeliveryResult(false, deliveryLog.getAttempts(), error);
            }
        } catch (Exception ex) {
            deliveryLog.setAttempts(deliveryLog.getAttempts() + 1);
            deliveryLog.setLastErrorMessage(ex.getMessage());
            deliveryLogRepository.save(deliveryLog);
            return new WebhookDeliveryResult(false, deliveryLog.getAttempts(), ex.getMessage());
        }
    }

    @Override
    public WebhookDeliveryResult replayWebhook(UUID tenantId, UUID webhookLogId) {
        WebhookDeliveryLog deliveryLog = deliveryLogRepository.findByIdAndTenantId(webhookLogId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook log not found with ID: " + webhookLogId));

        log.info("Manual webhook redelivery requested for ID: {}", webhookLogId);


        return retryExistingLog(deliveryLog);
    }
}