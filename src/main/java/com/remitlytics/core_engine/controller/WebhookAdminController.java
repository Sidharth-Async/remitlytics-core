package com.remitlytics.core_engine.controller;

import com.remitlytics.core_engine.dto.WebhookDeliveryResult;
import com.remitlytics.core_engine.service.WebhookDispatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/webhooks")
@RequiredArgsConstructor
public class WebhookAdminController {

    private final WebhookDispatcherService webhookDispatcherService;

    @PostMapping("/{id}/replay")
    public ResponseEntity<WebhookDeliveryResult> replayWebhook(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable("id") UUID id) {

        WebhookDeliveryResult result = webhookDispatcherService.replayWebhook(tenantId, id);
        return ResponseEntity.ok(result);
    }
}