package com.shortlink.openapi.service;

import com.shortlink.openapi.auth.ApiKeyEntity;
import com.shortlink.openapi.auth.ApiKeyMapper;
import com.shortlink.openapi.auth.ApiKeyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessKeyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ACCESS_KEY_PREFIX = "AK";

    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyStore apiKeyStore;

    /**
     * Generate a new AccessKey/SecretKey pair.
     */
    @Transactional
    public ApiKeyEntity createKey(String owner, String ipWhitelist, Integer quotaPerMinute) {
        String accessKey = generateAccessKey();
        String secretKey = generateSecretKey();

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setAppKey(accessKey);
        entity.setAppSecret(secretKey);
        entity.setOwner(owner != null ? owner : "");
        entity.setIpWhitelist(ipWhitelist);
        entity.setQuotaPerMinute(quotaPerMinute != null ? quotaPerMinute : 600);
        entity.setStatus(1);

        apiKeyMapper.insert(entity);
        apiKeyStore.invalidate(accessKey); // force reload on next lookup
        log.info("API key created: accessKey={}, owner={}", accessKey, owner);
        return entity;
    }

    /**
     * List all keys (optionally filter by status).
     */
    public List<ApiKeyEntity> listKeys(Integer status) {
        if (status != null) {
            return apiKeyMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiKeyEntity>()
                    .eq(ApiKeyEntity::getStatus, status)
                    .orderByDesc(ApiKeyEntity::getCreateTime));
        }
        return apiKeyMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiKeyEntity>()
                .orderByDesc(ApiKeyEntity::getCreateTime));
    }

    /**
     * Update key status or IP whitelist.
     */
    @Transactional
    public ApiKeyEntity updateKey(String accessKey, Integer status, String ipWhitelist, Integer quota) {
        ApiKeyEntity entity = apiKeyMapper.selectById(accessKey);
        if (entity == null) {
            throw new IllegalArgumentException("AccessKey not found: " + accessKey);
        }
        if (status != null) entity.setStatus(status);
        if (ipWhitelist != null) entity.setIpWhitelist(ipWhitelist);
        if (quota != null) entity.setQuotaPerMinute(quota);
        entity.setUpdateTime(LocalDateTime.now());
        apiKeyMapper.updateById(entity);
        apiKeyStore.invalidate(accessKey);
        log.info("API key updated: accessKey={}, status={}", accessKey, status);
        return entity;
    }

    /**
     * Soft-delete (set status=0).
     */
    @Transactional
    public void deleteKey(String accessKey) {
        updateKey(accessKey, 0, null, null);
        log.info("API key disabled: accessKey={}", accessKey);
    }

    private String generateAccessKey() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return ACCESS_KEY_PREFIX + HexFormat.of().formatHex(bytes);
    }

    private String generateSecretKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}