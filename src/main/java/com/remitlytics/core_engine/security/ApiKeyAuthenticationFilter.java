package com.remitlytics.core_engine.security;

import com.remitlytics.core_engine.model.entities.ApiKey;
import com.remitlytics.core_engine.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final RateLimiterService rateLimiterService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Skip API key authentication for browser preflight OPTIONS requests
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-KEY");
        log.info("Incoming Raw Header: '{}'", apiKey);

        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Missing X-API-KEY header\"}");
            return;
        }

        String hashedKey = hashApiKey(apiKey);
        log.info("Computed Hash for Query: '{}'", hashedKey);
        Optional<ApiKey> keyEntity = apiKeyRepository.findByKeyHashAndActiveTrue(hashedKey);
        log.info("DB Lookup Result isPresent: {}", keyEntity.isPresent());

        if (keyEntity.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid or revoked API Key\"}");
            return;
        }

        UUID tenantId = keyEntity.get().getTenant().getId();
        var bucket = rateLimiterService.resolveBucket(tenantId);

        if (!bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", "60");
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Maximum 100 requests per minute allowed.\"}");
            return;
        }

        try {
            TenantContext.setCurrentTenant(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    public static String hashApiKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}