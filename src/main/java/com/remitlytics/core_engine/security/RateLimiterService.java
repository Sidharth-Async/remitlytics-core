package com.remitlytics.core_engine.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    @Value("${remitlytics.ratelimit.capacity:10}")
    private long capacity;

    @Value("${remitlytics.ratelimit.refill-tokens:10}")
    private long refillTokens;

    @Value("${remitlytics.ratelimit.refill-duration-seconds:60}")
    private long refillDurationSeconds;

    public Bucket resolveBucket(String apiKeyHash) {
        return bucketCache.computeIfAbsent(apiKeyHash, this::createNewBucket);
    }

    public void clearCache() {
        bucketCache.clear();
    }

    private Bucket createNewBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.greedy(refillTokens, Duration.ofSeconds(refillDurationSeconds))
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}