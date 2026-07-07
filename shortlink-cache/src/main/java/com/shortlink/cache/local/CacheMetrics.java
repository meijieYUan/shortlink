package com.shortlink.cache.local;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CacheMetrics {

    private final MeterRegistry meterRegistry;

    private Counter l1Hits;
    private Counter l1Misses;
    private Counter l2Hits;
    private Counter l2Misses;
    private Counter dbFallbacks;
    private Counter bloomFilterRejects;

    @PostConstruct
    void init() {
        l1Hits = Counter.builder("shortlink.cache.l1.hits")
            .description("L1 Caffeine cache hits")
            .register(meterRegistry);
        l1Misses = Counter.builder("shortlink.cache.l1.misses")
            .description("L1 Caffeine cache misses")
            .register(meterRegistry);
        l2Hits = Counter.builder("shortlink.cache.l2.hits")
            .description("L2 Redis cache hits")
            .register(meterRegistry);
        l2Misses = Counter.builder("shortlink.cache.l2.misses")
            .description("L2 Redis cache misses")
            .register(meterRegistry);
        dbFallbacks = Counter.builder("shortlink.cache.db.fallbacks")
            .description("DB fallback lookups")
            .register(meterRegistry);
        bloomFilterRejects = Counter.builder("shortlink.cache.bloom.rejects")
            .description("Bloom filter negative results")
            .register(meterRegistry);
    }

    public void recordL1Hit() { l1Hits.increment(); }
    public void recordL1Miss() { l1Misses.increment(); }
    public void recordL2Hit() { l2Hits.increment(); }
    public void recordL2Miss() { l2Misses.increment(); }
    public void recordDbFallback() { dbFallbacks.increment(); }
    public void recordBloomReject() { bloomFilterRejects.increment(); }
}