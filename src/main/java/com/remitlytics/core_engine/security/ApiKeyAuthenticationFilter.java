package com.remitlytics.core_engine.security;

import com.remitlytics.core_engine.model.entities.ApiKey;
import com.remitlytics.core_engine.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final RateLimiterService rateLimiterService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-KEY");

        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Missing X-API-KEY header\"}");
            return;
        }

        String hashedKey = hashApiKey(apiKey);
        Optional<ApiKey> keyEntity = apiKeyRepository.findByKeyHashAndActiveTrue(hashedKey);

        if (keyEntity.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid or revoked API Key\"}");
            return;
        }

        UUID tenantId = keyEntity.get().getTenant().getId();
        var bucket = rateLimiterService.resolveBucket(tenantId);

        if (!bucket.tryConsume(1)) {
            response.setStatus(429); // 429 Too Many Requests
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", "60");
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Maximum 100 requests per minute allowed.\"}");
            return;
        }

        try {
            TenantContext.setCurrentTenant(keyEntity.get().getTenant().getId());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear(); // Always wipe ThreadLocal state after request ends
        }

    }

    private String hashApiKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}