package com.shortlink.openapi.auth;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * API key store backed by MySQL t_api_key with Caffeine local cache.
 *
 * Cache policy:
 * - TTL 5 minutes (keys rarely change; stale keys auto-removed)
 * - Max 1000 entries
 * - LoadingCache: on miss, queries DB; on expiry, auto-refreshes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyStore {

    private final ApiKeyMapper apiKeyMapper;

    private LoadingCache<String, Optional<String>> cache;

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build(this::loadFromDb);

        // Warm up: load all active keys from DB
        apiKeyMapper.selectList(null).stream()
            .filter(e -> e.getStatus() == 1)
            .forEach(e -> cache.put(e.getAppKey(), Optional.of(e.getAppSecret())));

        log.info("ApiKeyStore initialized: Caffeine cache (5min TTL, max 1000)");
    }

    public Optional<String> getSecret(String appKey) {
        return cache.get(appKey);
    }

    /**
     * Invalidate a specific key (e.g., after key rotation / disable).
     */
    public void invalidate(String appKey) {
        cache.invalidate(appKey);
    }

    private Optional<String> loadFromDb(String appKey) {
        ApiKeyEntity entity = apiKeyMapper.selectById(appKey);
        if (entity != null && entity.getStatus() == 1) {
            log.debug("DB hit for appKey: {}", appKey);
            return Optional.of(entity.getAppSecret());
        }
        log.debug("DB miss for appKey: {}", appKey);
        return Optional.empty();
    }
}