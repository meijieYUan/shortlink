package com.shortlink.openapi.auth;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory API key store. Maps AppKey to AppSecret.
 * Phase 6 MVP: hardcoded dev keys. Production should use DB-backed storage.
 */
@Slf4j
@Component
public class ApiKeyStore {

    private final Map<String, String> keyStore = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        // Dev keys — replace with DB query in production
        keyStore.put("sk_test_001", "sec_001_secret_key_32_chars_here!");
        keyStore.put("sk_test_002", "sec_002_secret_key_32_chars_here!");
        log.info("API key store initialized with {} keys", keyStore.size());
    }

    /**
     * Look up the secret for a given app key.
     */
    public Optional<String> getSecret(String appKey) {
        return Optional.ofNullable(keyStore.get(appKey));
    }
}