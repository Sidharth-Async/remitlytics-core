package com.remitlytics.core_engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitlytics.core_engine.dto.WebhookDeliveryResult;
import com.remitlytics.core_engine.dto.WebhookPayload;
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
import org.springframework.stereotype.Service;
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
    public WebhookDeliveryResult dispatchWithRetry(UUID tenantId, String targetUrl, WebhookPayload payload) {
        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setTenantId(tenantId);
        deliveryLog.setTargetUrl(targetUrl);
        deliveryLog.setStatus(DeliveryStatus.PENDING);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
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
}