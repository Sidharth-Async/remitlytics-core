package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.WebhookDeliveryResult;
import com.remitlytics.core_engine.dto.WebhookPayload;
import com.remitlytics.core_engine.model.entities.WebhookDeliveryLog;
import com.remitlytics.core_engine.model.enums.DeliveryStatus;
import com.remitlytics.core_engine.repository.WebhookDeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookRecoveryService {

    private final WebhookDispatcherService webhookDispatcherService;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60000)
    public void retryDeadLetterWebhooks() {
        Pageable pageRequest = PageRequest.of(0, 50);

        List<WebhookDeliveryLog> failedLogs = deliveryLogRepository
                .findByStatusAndAttemptsLessThanOrderByCreatedAtAsc(
                        DeliveryStatus.DEAD_LETTER,
                        10,
                        pageRequest
                );

        if (failedLogs.isEmpty()) {
            return;
        }

        log.info("Found {} dead-letter webhooks eligible for recovery sweep", failedLogs.size());

        for (WebhookDeliveryLog logEntry : failedLogs) {
            try {
                WebhookPayload payload = objectMapper.readValue(logEntry.getPayload(), WebhookPayload.class);

                WebhookDeliveryResult result = webhookDispatcherService.dispatchWithRetry(
                        logEntry.getTenantId(),
                        logEntry.getEventType(),
                        logEntry.getTargetUrl(),
                        payload
                );

                if (result.isDelivered()) {
                    logEntry.setStatus(DeliveryStatus.DELIVERED);
                }

                logEntry.setAttempts(logEntry.getAttempts() + 1);
                deliveryLogRepository.save(logEntry);

            } catch (Exception ex) {
                log.error("Failed to recover dead letter webhook ID: {}", logEntry.getId(), ex);
            }
        }
    }
}