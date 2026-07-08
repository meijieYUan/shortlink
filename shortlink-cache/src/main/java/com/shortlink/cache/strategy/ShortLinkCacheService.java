package com.shortlink.cache.strategy;

import com.github.benmanes.caffeine.cache.Cache;
import com.shortlink.cache.local.CacheMetrics;
import com.shortlink.cache.model.CacheLinkInfo;
import com.shortlink.cache.redis.BloomFilterService;
import com.shortlink.cache.redis.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Multi-level cache orchestrator for short link reads.
 *
 * Read path: L1 Caffeine -> L2 Redis -> Bloom filter -> DB (with protections)
 * - Penetration: Redisson RBloomFilter rejects definitely-absent keys before DB
 * - Breakdown: Redisson RLock distributed lock ensures single DB fallback
 * - Avalanche: Redis TTL randomized (+/- 5 min jitter in RedisCacheService)
 *
 * Write path (Cache Aside):
 * - On create: write DB -> populate both caches + bloom filter
 * - On update/delete: invalidate caches (lazy repopulation on next read)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkCacheService {

    private final Cache<String, CacheLinkInfo> localCache;
    private final RedisCacheService redisCache;
    private final BloomFilterService bloomFilter;
    private final CacheMetrics metrics;
    private final RedissonClient redissonClient;

    private static final long LOCK_WAIT_SECONDS = 3;
    //锁的持有时间 -1 开启看门狗机制
    private static final long LOCK_LEASE_SECONDS = -1;
    private static final String LOCK_PREFIX = "shortlink:lock:";

    /**
     * Read short link info through the multi-level cache.
     * @param shortCode the short code to look up
     * @param dbLoader fallback Supplier that queries DB
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
                localCache.put(shortCode, info); // promote to L1
                return Optional.of(info);
            }
        }
        metrics.recordL2Miss();

        // 3. Bloom filter: penetration prevention (Redisson RBloomFilter)
        if (!bloomFilter.mightContain(shortCode)) {
            metrics.recordBloomReject();
            log.debug("Bloom filter reject: {}", shortCode);
            return Optional.empty();
        }

        // 4. DB fallback with Redisson RLock (breakdown prevention)
        return getWithLock(shortCode, dbLoader);
    }

    /**
     * Notify cache that a new short link was created.
     */
    public void onCreated(String shortCode, CacheLinkInfo info) {
        bloomFilter.add(shortCode);
        localCache.put(shortCode, info);
        redisCache.put(shortCode, info);
    }

    /**
     * Notify cache that a short link was updated/deleted (Cache Aside: invalidate).
     */
    public void onUpdated(String shortCode) {
        localCache.invalidate(shortCode);   //删除缓存key
        redisCache.delete(shortCode);
    }

    // ---- private helpers ----

    private boolean isExpired(CacheLinkInfo info) {
        return info.getExpireTime() != null
            && info.getExpireTime().isBefore(java.time.LocalDateTime.now());
    }

    /**
     * DB fallback with Redisson RLock to prevent cache breakdown.
     * Only one thread per shortCode queries DB; others retry L2 after brief wait.
     */
    private Optional<CacheLinkInfo> getWithLock(String shortCode, Supplier<Optional<CacheLinkInfo>> dbLoader) {
        String lockKey = LOCK_PREFIX + shortCode;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Try to acquire the lock; if held by another thread, retry L2
            if (lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                try {
                    // Double-check: another thread might have populated（填充） the cache
                    Optional<CacheLinkInfo> recheck = redisCache.get(shortCode);
                    if (recheck.isPresent()) {
                        return recheck;
                    }

                    metrics.recordDbFallback();
                    log.debug("DB fallback: {}", shortCode);
                    Optional<CacheLinkInfo> dbResult = dbLoader.get();
                    dbResult.ifPresent(info -> {
                        localCache.put(shortCode, info);
                        redisCache.put(shortCode, info);
                    });
                    return dbResult;
                } finally {
                    lock.unlock();
                }
            } else {
                // Could not acquire lock within wait time — another thread is handling DB
                log.debug("Lock contention, retrying L2 for: {}", shortCode);
                return redisCache.get(shortCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock interrupted for: {}", shortCode);
            return redisCache.get(shortCode);
        }
    }
}