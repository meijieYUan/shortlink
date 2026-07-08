package com.shortlink.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shortlink.cache.model.CacheLinkInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private static final String KEY_PREFIX = "shortlink:";
    private static final int AVALANCHE_JITTER_MINUTES = 5;

    @Getter
    private final StringRedisTemplate redisTemplate;
    //Json工具类：序列化和反序列化


    private final ObjectMapper objectMapper = new ObjectMapper()
        //在手动给 ObjectMapper 安装一个“插件包”，专门用来解决 Java 8+ 日期时间类
        .registerModule(new JavaTimeModule());

    public Optional<CacheLinkInfo> get(String shortCode) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + shortCode);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CacheLinkInfo.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cache for shortCode={}", shortCode, e);
            return Optional.empty();
        }
    }

    public void put(String shortCode, CacheLinkInfo info) {
        try {
            String json = objectMapper.writeValueAsString(info);
            String key = KEY_PREFIX + shortCode;
            Duration ttl = computeTtl(info);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize cache for shortCode={}", shortCode, e);
        }
    }

    public void delete(String shortCode) {
        redisTemplate.delete(KEY_PREFIX + shortCode);
    }

    private Duration computeTtl(CacheLinkInfo info) {
        if (info.getExpireTime() == null) {
            return Duration.ofDays(90);
        }
        long seconds = java.time.Duration.between(
            java.time.LocalDateTime.now(), info.getExpireTime()).getSeconds();
        if (seconds <= 0) {
            return Duration.ofMinutes(1);
        }
        int jitter = ThreadLocalRandom.current().nextInt(
            -AVALANCHE_JITTER_MINUTES * 60, AVALANCHE_JITTER_MINUTES * 60 + 1);
        long finalSeconds = Math.max(60, seconds + jitter);
        return Duration.ofSeconds(finalSeconds);
    }
}