package com.remitlytics.core_engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    @Value("${remitlytics.stripe.webhook-secret:whsec_test_secret_123}")
    private String webhookSecret;

    public void processWebhook(String payload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        log.info("Received valid Stripe webhook event: {} [{}]", event.getType(), event.getId());

        if ("checkout.session.completed".equals(event.getType())) {
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

            if (stripeObject instanceof Session session) {
                handleCheckoutSessionCompleted(session);
            } else {
                // Fallback: parse raw JSON data object directly
                try {
                    JsonNode root = objectMapper.readTree(payload);
                    JsonNode dataObject = root.path("data").path("object");
                    handleRawCheckoutSession(dataObject);
                } catch (Exception e) {
                    log.error("Failed to parse fallback Stripe payload: {}", e.getMessage());
                    throw new IllegalArgumentException("Invalid payload structure for checkout.session.completed", e);
                }
            }
        } else {
            log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handleCheckoutSessionCompleted(Session session) {
        String invoiceIdStr = null;
        if (session.getMetadata() != null) {
            invoiceIdStr = session.getMetadata().get("invoice_id");
        }
        if (invoiceIdStr == null || invoiceIdStr.isBlank()) {
            invoiceIdStr = session.getClientReferenceId();
        }

        executeInvoiceSettlement(invoiceIdStr, session.getId());
    }

    private void handleRawCheckoutSession(JsonNode sessionNode) {
        String invoiceIdStr = sessionNode.path("metadata").path("invoice_id").asText(null);
        if (invoiceIdStr == null || invoiceIdStr.isBlank()) {
            invoiceIdStr = sessionNode.path("client_reference_id").asText(null);
        }

        String sessionId = sessionNode.path("id").asText("unknown_session");
        executeInvoiceSettlement(invoiceIdStr, sessionId);
    }

    private void executeInvoiceSettlement(String invoiceIdStr, String sessionId) {
        if (invoiceIdStr == null || invoiceIdStr.isBlank()) {
            log.error("Stripe Session {} missing invoice_id metadata and clientReferenceId", sessionId);
            throw new IllegalArgumentException("Stripe Session missing invoice reference");
        }

        UUID invoiceId = UUID.fromString(invoiceIdStr);
        log.info("Settling invoice {} via Stripe session {}", invoiceId, sessionId);

        invoiceService.updateInvoiceStatus(
                invoiceId,
                InvoiceStatus.PAID,
                "Settled via Stripe Checkout Session: " + sessionId
        );
    }
}