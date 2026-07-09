package com.shortlink.openapi.auth;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API key store backed by MySQL t_api_key table with local cache.
 * On startup: loads all active keys into memory. On cache miss: queries DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyStore {

    private final ApiKeyMapper apiKeyMapper;

    /** Local cache: appKey -> appSecret */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        refreshCache();
        log.info("ApiKeyStore initialized: {} keys loaded from DB", cache.size());
    }

    public Optional<String> getSecret(String appKey) {
        // 1. Local cache hit
        String secret = cache.get(appKey);
        if (secret != null) {
            return Optional.of(secret);
        }
        // 2. Cache miss: query DB
        ApiKeyEntity entity = apiKeyMapper.selectById(appKey);
        if (entity != null && entity.getStatus() == 1) {
            cache.put(appKey, entity.getAppSecret());
            return Optional.of(entity.getAppSecret());
        }
        return Optional.empty();
    }

    public void refreshCache() {
        List<ApiKeyEntity> all = apiKeyMapper.selectList(null);
        cache.clear();
        for (ApiKeyEntity entity : all) {
            if (entity.getStatus() == 1) {
                cache.put(entity.getAppKey(), entity.getAppSecret());
            }
        }
        log.debug("Cache refreshed: {} keys", cache.size());
    }
}