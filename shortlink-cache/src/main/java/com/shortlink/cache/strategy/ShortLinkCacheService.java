package com.shortlink.cache.strategy;

import com.github.benmanes.caffeine.cache.Cache;
import com.shortlink.cache.local.CacheMetrics;
import com.shortlink.cache.model.CacheLinkInfo;
import com.shortlink.cache.redis.BloomFilterService;
import com.shortlink.cache.redis.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Multi-level cache orchestrator for short link reads.
 *
 * Read path: L1 Caffeine -> L2 Redis -> DB (with protections)
 * - Penetration: Bloom filter rejects definitely-absent keys before DB
 * - Breakdown: Redis SET NX distributed lock ensures single DB fallback
 * - Avalanche: Redis TTL randomized (+/- 5 min jitter in RedisCacheService)
 *
 * Write path (Cache Aside):
 * - On create: write DB -> delete Redis (lazy load on next read)
 * - On update: write DB -> delete Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkCacheService {

    private final Cache<String, CacheLinkInfo> localCache;
    private final RedisCacheService redisCache;
    private final BloomFilterService bloomFilter;
    private final CacheMetrics metrics;

    // Distributed lock TTL (seconds)
    private static final int LOCK_TTL_SECONDS = 10;
    private static final String LOCK_PREFIX = "shortlink:lock:";

    /**
     * Read short link info through the multi-level cache.
     * @param shortCode the short code to look up
     * @param dbLoader fallback Supplier that queries DB (usually mapper.selectById)
     * @return the CacheLinkInfo, or empty if not found
     */
    public Optional<CacheLinkInfo> get(String shortCode, Supplier<Optional<CacheLinkInfo>> dbLoader) {
        // 1. L1: Caffeine local cache
        CacheLinkInfo cached = localCache.getIfPresent(shortCode);
        if (cached != null) {
            if (isExpired(cached)) {
                localCache.invalidate(shortCode);
            } else {
                metrics.recordL1Hit();
                log.debug("L1 hit: {}", shortCode);
                return Optional.of(cached);
            }
        }
        metrics.recordL1Miss();

        // 2. L2: Redis cache
        Optional<CacheLinkInfo> redisResult = redisCache.get(shortCode);
        if (redisResult.isPresent()) {
            CacheLinkInfo info = redisResult.get();
            if (isExpired(info)) {
                redisCache.delete(shortCode);
            } else {
                metrics.recordL2Hit();
                log.debug("L2 hit: {}", shortCode);
                // Promote to L1
                localCache.put(shortCode, info);
                return Optional.of(info);
            }
        }
        metrics.recordL2Miss();

        // 3. Bloom filter: penetration prevention
        if (!bloomFilter.mightContain(shortCode)) {
            metrics.recordBloomReject();
            log.debug("Bloom filter reject: {}", shortCode);
            return Optional.empty();
        }

        // 4. DB fallback with distributed lock (breakdown prevention)
        return getWithLock(shortCode, dbLoader);
    }

    /**
     * Notify cache that a new short link was created.
     * Adds to Bloom filter. L1/L2 will be populated on next read (lazy).
     */
    public void onCreated(String shortCode, CacheLinkInfo info) {
        bloomFilter.add(shortCode);
        // Eagerly populate L1 and L2 to warm cache
        localCache.put(shortCode, info);
        redisCache.put(shortCode, info);
    }

    /**
     * Notify cache that a short link was updated/deleted (Cache Aside: invalidate).
     */
    public void onUpdated(String shortCode) {
        localCache.invalidate(shortCode);
        redisCache.delete(shortCode);
    }

    // ---- private helpers ----

    private boolean isExpired(CacheLinkInfo info) {
        return info.getExpireTime() != null
            && info.getExpireTime().isBefore(java.time.LocalDateTime.now());
    }

    /**
     * DB fallback with Redis SET NX distributed lock to prevent cache breakdown.
     * Only one thread per shortCode queries DB; others wait briefly or get stale data.
     */
    private Optional<CacheLinkInfo> getWithLock(String shortCode, Supplier<Optional<CacheLinkInfo>> dbLoader) {
        String lockKey = LOCK_PREFIX + shortCode;
        Boolean acquired = redisCache.getRedisTemplate().opsForValue()
            .setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (Boolean.TRUE.equals(acquired)) {
            try {
                metrics.recordDbFallback();
                log.debug("DB fallback: {}", shortCode);
                Optional<CacheLinkInfo> dbResult = dbLoader.get();
                dbResult.ifPresent(info -> {
                    // Populate both caches
                    localCache.put(shortCode, info);
                    redisCache.put(shortCode, info);
                });
                return dbResult;
            } finally {
                redisCache.getRedisTemplate().delete(lockKey);
            }
        } else {
            // Another thread is handling the DB query; retry L2 briefly
            log.debug("Lock contention, retrying L2 for: {}", shortCode);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return redisCache.get(shortCode);
        }
    }
}