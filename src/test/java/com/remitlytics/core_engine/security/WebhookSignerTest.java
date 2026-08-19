package com.remitlytics.core_engine.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private WebhookSigner webhookSigner;
    private static final String SECRET = "whsec_live_55a9b88c3e2101df44a7";

    @BeforeEach
    void setUp() {
        webhookSigner = new WebhookSigner();
    }

    @Test
    @DisplayName("Valid signature with fresh timestamp should verify successfully")
    void shouldVerifyValidSignature() {
        String payload = "{\"eventType\":\"INVOICE.PAID\",\"amountCents\":50000}";
        long timestamp = Instant.now().getEpochSecond();

        String signature = webhookSigner.generateSignature(payload, timestamp, SECRET);

        boolean isValid = webhookSigner.verifySignature(payload, timestamp, signature, SECRET);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Tampered payload body must fail signature verification")
    void shouldRejectTamperedPayload() {
        String originalPayload = "{\"eventType\":\"INVOICE.PAID\",\"amountCents\":50000}";
        String tamperedPayload = "{\"eventType\":\"INVOICE.PAID\",\"amountCents\":99999}";
        long timestamp = Instant.now().getEpochSecond();

        String originalSignature = webhookSigner.generateSignature(originalPayload, timestamp, SECRET);

        boolean isValid = webhookSigner.verifySignature(tamperedPayload, timestamp, originalSignature, SECRET);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Mismatched secret key must fail signature verification")
    void shouldRejectInvalidSecret() {
        String payload = "{\"eventType\":\"INVOICE.PAID\",\"amountCents\":50000}";
        long timestamp = Instant.now().getEpochSecond();

        String signature = webhookSigner.generateSignature(payload, timestamp, SECRET);

        boolean isValid = webhookSigner.verifySignature(payload, timestamp, signature, "whsec_different_secret_key");
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Replay attack: Timestamp older than 5 minutes must be rejected even if signature is valid")
    void shouldRejectExpiredTimestamp() {
        String payload = "{\"eventType\":\"INVOICE.PAID\",\"amountCents\":50000}";
        long expiredTimestamp = Instant.now().minusSeconds(301).getEpochSecond(); // 5 min 1 sec ago

        String signature = webhookSigner.generateSignature(payload, expiredTimestamp, SECRET);

        boolean isValid = webhookSigner.verifySignature(payload, expiredTimestamp, signature, SECRET);
        assertThat(isValid).isFalse();
    }
}