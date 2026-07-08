package com.shortlink.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.shortlink.cache.local.CacheMetrics;
import com.shortlink.cache.model.CacheLinkInfo;
import com.shortlink.cache.redis.BloomFilterService;
import com.shortlink.cache.redis.RedisCacheService;
import com.shortlink.cache.strategy.ShortLinkCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortLinkCacheServiceTest {

    @Mock private RedisCacheService redisCache;
    @Mock private BloomFilterService bloomFilter;
    @Mock private CacheMetrics metrics;
    @Mock private Cache<String, CacheLinkInfo> localCache;
    @Mock private RedissonClient redissonClient;

    private ShortLinkCacheService cacheService;

    private final String shortCode = "0000001";
    private final CacheLinkInfo linkInfo = CacheLinkInfo.builder()
        .shortCode(shortCode)
        .originalUrl("https://example.com")
        .expireTime(LocalDateTime.now().plusDays(30))
        .status(1)
        .build();

    @BeforeEach
    void setUp() {
        cacheService = new ShortLinkCacheService(
            localCache, redisCache, bloomFilter, metrics, redissonClient);
    }

    @Nested
    @DisplayName("L1 Caffeine hit")
    class L1Hit {
        @Test
        @DisplayName("should return from L1 without hitting L2, bloom, or DB")
        void fromL1() {
            when(localCache.getIfPresent(shortCode)).thenReturn(linkInfo);

            Optional<CacheLinkInfo> result = cacheService.get(shortCode, dbLoader());

            assertThat(result).isPresent();
            assertThat(result.get().getOriginalUrl()).isEqualTo("https://example.com");
            verify(metrics).recordL1Hit();
            verify(redisCache, never()).get(anyString());
            verify(bloomFilter, never()).mightContain(anyString());
        }
    }

    @Nested
    @DisplayName("L2 Redis hit")
    class L2Hit {
        @Test
        @DisplayName("should fall back to L2 on L1 miss and promote to L1")
        void fromL2() {
            when(localCache.getIfPresent(shortCode)).thenReturn(null);
            when(redisCache.get(shortCode)).thenReturn(Optional.of(linkInfo));

            Optional<CacheLinkInfo> result = cacheService.get(shortCode, dbLoader());

            assertThat(result).isPresent();
            verify(metrics).recordL1Miss();
            verify(metrics).recordL2Hit();
            verify(localCache).put(shortCode, linkInfo);
        }
    }

    @Nested
    @DisplayName("Bloom filter rejection")
    class BloomReject {
        @Test
        @DisplayName("should return empty without hitting DB when Bloom filter says no")
        void bloomRejects() {
            when(localCache.getIfPresent(shortCode)).thenReturn(null);
            when(redisCache.get(shortCode)).thenReturn(Optional.empty());
            when(bloomFilter.mightContain(shortCode)).thenReturn(false);

            @SuppressWarnings("unchecked")
            Supplier<Optional<CacheLinkInfo>> db = org.mockito.Mockito.mock(Supplier.class);

            Optional<CacheLinkInfo> result = cacheService.get(shortCode, db);

            assertThat(result).isEmpty();
            verify(metrics).recordL1Miss();
            verify(metrics).recordL2Miss();
            verify(metrics).recordBloomReject();
            verify(db, never()).get();
        }
    }

    @Nested
    @DisplayName("OnCreated")
    class OnCreated {
        @Test
        @DisplayName("should add to bloom filter and populate both caches")
        void populateCaches() {
            cacheService.onCreated(shortCode, linkInfo);

            verify(bloomFilter).add(shortCode);
            verify(localCache).put(shortCode, linkInfo);
            verify(redisCache).put(shortCode, linkInfo);
        }
    }

    @Nested
    @DisplayName("OnUpdated")
    class OnUpdated {
        @Test
        @DisplayName("should invalidate both caches (Cache Aside)")
        void invalidate() {
            cacheService.onUpdated(shortCode);

            verify(localCache).invalidate(shortCode);
            verify(redisCache).delete(shortCode);
        }
    }

    private Supplier<Optional<CacheLinkInfo>> dbLoader() {
        return Optional::empty;
    }
}