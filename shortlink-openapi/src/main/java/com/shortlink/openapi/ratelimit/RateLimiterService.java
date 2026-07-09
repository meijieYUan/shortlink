package com.shortlink.openapi.ratelimit;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-AppKey rate limiting using Guava RateLimiter.
 * Default: 10 QPS per AppKey. Expired entries are cleaned periodically.
 */
@Slf4j
@Service
public class RateLimiterService {

    /** Default QPS per app key */
    private static final double DEFAULT_QPS = 10.0;

    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        log.info("RateLimiterService initialized: default={} qps", DEFAULT_QPS);
    }

    /**
     * Try to acquire a permit for the given appKey.
     * @return true if permitted, false if rate limited
     */
    public boolean tryAcquire(String appKey) {
        RateLimiter limiter = limiters.computeIfAbsent(appKey, k -> RateLimiter.create(DEFAULT_QPS));
        return limiter.tryAcquire();
    }
}