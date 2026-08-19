package com.remitlytics.core_engine.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class WebhookSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long MAX_ALLOWED_SKEW_SECONDS = 300; // 5 minutes tolerance

    /**
     * Computes an HMAC-SHA256 hex signature over: "<timestamp>.<payloadJson>"
     */
    public String generateSignature(String payloadJson, long timestamp, String secretKey) {
        String signedContent = timestamp + "." + payloadJson;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 webhook signature", e);
        }
    }

    /**
     * Verifies the incoming signature against the payload, timestamp, and secret using constant-time comparison.
     */
    public boolean verifySignature(String payloadJson, long timestamp, String expectedSignature, String secretKey) {
        // 1. Replay attack protection: reject requests older than tolerance window
        long currentTimestamp = Instant.now().getEpochSecond();
        if (Math.abs(currentTimestamp - timestamp) > MAX_ALLOWED_SKEW_SECONDS) {
            return false;
        }

        // 2. Compute signature for comparison
        String computedSignature = generateSignature(payloadJson, timestamp, secretKey);

        // 3. Constant-time equality check prevents timing attacks
        return MessageDigest.isEqual(
                computedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }
}