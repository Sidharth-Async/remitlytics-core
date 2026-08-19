package com.remitlytics.core_engine;

import com.remitlytics.core_engine.dto.WebhookDeliveryResult;
import com.remitlytics.core_engine.dto.WebhookPayload;
import com.remitlytics.core_engine.model.entities.Tenant;
import com.remitlytics.core_engine.model.entities.WebhookDeliveryLog;
import com.remitlytics.core_engine.model.enums.DeliveryStatus;
import com.remitlytics.core_engine.repository.TenantRepository;
import com.remitlytics.core_engine.repository.WebhookDeliveryLogRepository;
import com.remitlytics.core_engine.service.WebhookDispatcherService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import com.remitlytics.core_engine.dto.WebhookDeliveryResult;
import com.remitlytics.core_engine.dto.WebhookPayload;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remitlytics_webhook_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "remitlytics.webhook.max-retries=3",
        "remitlytics.webhook.backoff-ms=100"
})
class WebhookRetryIntegrationTest {

    @MockBean
    private Flyway flyway;

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private WebhookDispatcherService webhookDispatcherService;

    @Autowired
    private WebhookDeliveryLogRepository deliveryLogRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant testTenant;

    @BeforeEach
    void setUp() {
        deliveryLogRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = new Tenant();
        testTenant.setCompanyName("Webhook Test Corp");
        testTenant = tenantRepository.save(testTenant);
    }

    @Test
    @DisplayName("Webhook delivery should retry 3 times on remote 500 errors and succeed on 3rd attempt")
    void shouldRetryOnRemoteFailureAndSucceed() {
        String targetUrl = "https://partner.api.com/webhooks/invoices";
        WebhookPayload payload = new WebhookPayload("INVOICE.PAID", UUID.randomUUID(), 50000L);

        // Simulate 2 failures (500 Server Error) followed by 1 success (200 OK)
        when(restTemplate.postForEntity(eq(targetUrl), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(new ResponseEntity<>("Service Unavailable", HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        WebhookDeliveryResult result = webhookDispatcherService.dispatchWithRetry(testTenant.getId(), targetUrl, payload);

        assertThat(result.isDelivered()).isTrue();
        assertThat(result.getAttemptCount()).isEqualTo(3);

        // Verify RestTemplate was invoked exactly 3 times
        verify(restTemplate, times(3)).postForEntity(eq(targetUrl), any(), eq(String.class));

        // Verify delivery audit record in DB
        WebhookDeliveryLog log = deliveryLogRepository.findByTenantId(testTenant.getId()).getFirst();
        assertThat(log.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(log.getAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("Webhook delivery should exhaust retries on network timeout and transition to DEAD_LETTER")
    void shouldExhaustRetriesAndMarkAsDeadLetter() {
        String targetUrl = "https://unreachable.endpoint.com/webhook";
        WebhookPayload payload = new WebhookPayload("INVOICE.PAYMENT_FAILED", UUID.randomUUID(), 10000L);

        // Simulate complete network failure across all attempts
        when(restTemplate.postForEntity(eq(targetUrl), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        WebhookDeliveryResult result = webhookDispatcherService.dispatchWithRetry(testTenant.getId(), targetUrl, payload);

        assertThat(result.isDelivered()).isFalse();
        assertThat(result.getAttemptCount()).isEqualTo(3);

        verify(restTemplate, times(3)).postForEntity(eq(targetUrl), any(), eq(String.class));

        WebhookDeliveryLog log = deliveryLogRepository.findByTenantId(testTenant.getId()).getFirst();
        assertThat(log.getStatus()).isEqualTo(DeliveryStatus.DEAD_LETTER);
        assertThat(log.getLastErrorMessage()).contains("Connection timed out");
    }
}