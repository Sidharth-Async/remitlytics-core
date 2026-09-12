package com.remitlytics.core_engine.controller;

import com.remitlytics.core_engine.dto.WebhookEvent;
import com.remitlytics.core_engine.model.entities.WebhookDeliveryLog;
import com.remitlytics.core_engine.repository.WebhookDeliveryLogRepository;
import com.remitlytics.core_engine.security.TenantContext; // or your project's tenant context holder
import com.remitlytics.core_engine.service.InvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final InvoiceService invoiceService;
    private final WebhookDeliveryLogRepository webhookDeliveryLogRepository;

    public WebhookController(InvoiceService invoiceService,
                             WebhookDeliveryLogRepository webhookDeliveryLogRepository) {
        this.invoiceService = invoiceService;
        this.webhookDeliveryLogRepository = webhookDeliveryLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<WebhookDeliveryLog>> getTenantWebhooks() {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<WebhookDeliveryLog> logs = webhookDeliveryLogRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId);
        return ResponseEntity.ok(logs);
    }

    @PostMapping("/payments")
    public ResponseEntity<Void> handlePaymentWebhook(@RequestBody WebhookEvent event) {
        invoiceService.processPaymentWebhook(event, event.amountReceivedCents());
        return ResponseEntity.ok().build();
    }
}