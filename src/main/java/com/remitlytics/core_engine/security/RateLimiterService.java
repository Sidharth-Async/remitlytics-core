package com.remitlytics.core_engine.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final Map<UUID, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(UUID tenantId) {
        return cache.computeIfAbsent(tenantId, this::newBucket);
    }

    private Bucket newBucket(UUID tenantId) {
        // Allow 100 requests per minute per tenant
        Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}