package com.shortlink.openapi.auth;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyStore {

    private final ApiKeyMapper apiKeyMapper;

    private LoadingCache<String, Optional<ApiKeyEntity>> cache;

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build(this::loadFromDb);
    /*
    如果 Wrapper 不为 null，会根据其中的条件拼接 WHERE 子句。

    如果 Wrapper 为 null，不拼接 WHERE 子句。
    */
        apiKeyMapper.selectList(null).stream()
            .filter(e -> e.getStatus() == 1)
            .forEach(e -> cache.put(e.getAppKey(), Optional.of(e)));

        log.info("ApiKeyStore initialized: Caffeine cache (1min TTL, max 1000)");
    }

    public Optional<String> getSecret(String accessKey) {
        return cache.get(accessKey)
            .filter(e -> e.getStatus() == 1)
            .map(ApiKeyEntity::getAppSecret);
    }

    /**
     * Check if client IP matches the configured whitelist.
     * Empty/null whitelist = allow all.
     */
    public boolean matchIpWhitelist(String accessKey, String clientIp) {
        Optional<ApiKeyEntity> entity = cache.get(accessKey);
        if (entity.isEmpty() || entity.get().getStatus() != 1) {
            return true; // let signature check fail, or no restriction if key has no whitelist
        }
        String whitelist = entity.get().getIpWhitelist();
        if (whitelist == null || whitelist.isBlank()) {
            return true; // no restriction
        }

        try {
            InetAddress clientAddr = InetAddress.getByName(clientIp);
            for (String cidr : whitelist.split(",")) {
                cidr = cidr.trim();
                if (cidr.isEmpty()) continue;
                // Simple check: exact IP match or /32
                String ip = cidr.contains("/") ? cidr.substring(0, cidr.indexOf('/')) : cidr;
                if (clientAddr.equals(InetAddress.getByName(ip))) {
                    return true;
                }
                // Full CIDR check can be added with a library like commons-net
            }
        } catch (Exception e) {
            log.warn("IP whitelist check failed: accessKey={}, ip={}", accessKey, clientIp, e);
            return false;
        }
        return false;
    }

        /**
     * Get the quota (requests per minute) for the given accessKey.
     * Falls back to 600 QPM (10 QPS) if not configured.
     */
    public int getQuotaPerMinute(String accessKey) {
        return cache.get(accessKey)
            .filter(e -> e.getStatus() == 1)
            .map(ApiKeyEntity::getQuotaPerMinute)
            .orElse(600);
    }

    public void invalidate(String accessKey) {
        cache.invalidate(accessKey);
    }

    private Optional<ApiKeyEntity> loadFromDb(String accessKey) {
        ApiKeyEntity entity = apiKeyMapper.selectById(accessKey);
        return Optional.ofNullable(entity);
    }
}