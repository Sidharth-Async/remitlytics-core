package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.WebhookDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {
    List<WebhookDeliveryLog> findByTenantId(UUID tenantId);


}