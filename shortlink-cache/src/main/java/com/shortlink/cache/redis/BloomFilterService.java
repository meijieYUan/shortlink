package com.shortlink.cache.redis;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redisson RBloomFilter-based bloom filter for cache penetration prevention.
 *
 * Uses Redisson's distributed Bloom filter backed by Redis.
 * - {@code mightContain(shortCode)}: check if a short code possibly exists (false positives OK, false negatives never)
 * - {@code add(shortCode)}: add a short code to the filter on creation
 * - {@code rebuild(codes)}: rebuild from DB on startup
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterService {

    private static final String FILTER_NAME = "shortlink:bloom";
    /** Expected number of insertions ~100M */
    private static final long EXPECTED_INSERTIONS = 100_000_000L;
    /** Acceptable false positive rate: 0.1% */
    private static final double FPP = 0.001;

    private final RedissonClient redissonClient;

    private RBloomFilter<String> bloomFilter;

    @PostConstruct
    void init() {
        this.bloomFilter = redissonClient.getBloomFilter(FILTER_NAME);
        // tryInit is idempotent — only initializes if the filter doesn't exist yet
        boolean initialized = bloomFilter.tryInit(EXPECTED_INSERTIONS, FPP);
        if (initialized) {
            log.info("Bloom filter initialized: name={}, expectedInsertions={}, fpp={}",
                     FILTER_NAME, EXPECTED_INSERTIONS, FPP);
        } else {
            log.info("Bloom filter already exists: name={}", FILTER_NAME);
        }
    }

    /**
     * Check if a shortCode might exist in the filter.
     * @return true if possibly present, false if definitely absent
     */
    public boolean mightContain(String shortCode) {
        return bloomFilter.contains(shortCode);
    }

    /**
     * Add a shortCode to the bloom filter (called when a new short link is created).
     */
    public void add(String shortCode) {
        bloomFilter.add(shortCode);
    }

    /**
     * Rebuild the Bloom filter from a list of all active shortCodes.
     * Called on application startup after initializing.
     */
    @Async
    public void rebuild(List<String> shortCodes) {
        if (shortCodes == null || shortCodes.isEmpty()) {
            log.info("No short codes to rebuild Bloom filter");
            return;
        }
        log.info("Rebuilding Bloom filter: {} shortCodes...", shortCodes.size());
        bloomFilter.delete();
        bloomFilter.tryInit(EXPECTED_INSERTIONS, FPP);
        shortCodes.forEach(bloomFilter::add);
        log.info("Bloom filter rebuild complete: {} shortCodes added", shortCodes.size());
    }
}