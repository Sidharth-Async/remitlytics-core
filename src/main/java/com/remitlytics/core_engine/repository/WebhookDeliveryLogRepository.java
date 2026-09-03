package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.dto.WebhookDeliveryResult;
import com.remitlytics.core_engine.dto.WebhookPayload;
import com.remitlytics.core_engine.model.entities.WebhookDeliveryLog;
import com.remitlytics.core_engine.model.enums.DeliveryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {
    List<WebhookDeliveryLog> findByTenantId(UUID tenantId);
    List<WebhookDeliveryLog> findByStatusAndAttemptsLessThanOrderByCreatedAtAsc(DeliveryStatus status, int maxAttempts, Pageable pageable);
}