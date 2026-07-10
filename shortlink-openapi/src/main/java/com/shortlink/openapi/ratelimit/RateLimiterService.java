package com.shortlink.openapi.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.RateLimiter;
import com.shortlink.openapi.auth.ApiKeyStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Per-AccessKey rate limiting with Caffeine-backed RateLimiter cache.
 *
 * - QPS: read from t_api_key.quota_per_minute (QPM), converted to QPS
 * - Cache: Caffeine with 30min expire-after-access (idle keys auto-evicted)
 * - Eviction: key disappears 30min after last request → next request reloads quota from DB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    /** Default: 600 QPM = 10 QPS */
    private static final double DEFAULT_QPS = 10.0;

    private final ApiKeyStore apiKeyStore;

    private Cache<String, RateLimiter> limiters;

    @PostConstruct
    void init() {
        limiters = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();
        log.info("RateLimiterService initialized: dynamic QPS, 30min idle eviction, max 10000 entries");
    }

    /**
     * Try to acquire a permit for the given accessKey.
     * Quota is read from ApiKeyStore (cached from DB, 5min TTL).
     * @return true if permitted, false if rate limited
     */
    public boolean tryAcquire(String accessKey) {
        RateLimiter limiter = limiters.get(accessKey, key -> {
            int quotaPerMinute = apiKeyStore.getQuotaPerMinute(key);
            double qps = quotaPerMinute / 60.0;
            log.debug("Created RateLimiter for {}: {} QPM = {:.1f} QPS", key, quotaPerMinute, qps);
            return RateLimiter.create(qps);
        });
        return limiter.tryAcquire();
    }
}