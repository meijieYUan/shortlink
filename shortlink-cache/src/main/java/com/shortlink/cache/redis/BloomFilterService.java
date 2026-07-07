package com.shortlink.cache.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Redis Bitmap-based Bloom filter for cache penetration prevention.
 *
 * Uses multiple hash functions (via SHA-256 variants) to set/check bits in a Redis Bitmap.
 * Before any DB lookup, the filter is checked: if negative, the shortCode definitely
 * does not exist, saving a useless DB query.
 *
 * On startup, should be rebuilt from DB (all active shortCodes).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterService {

    private static final String REDIS_KEY = "shortlink:bloom";
    /** Expected insertions ~100M, false positive rate ~0.1% */
    private static final long EXPECTED_INSERTIONS = 100_000_000L;
    private static final double FPP = 0.001;

    /** Optimal bit array size: -n*ln(p) / (ln2)^2 */
    private static final long BIT_SIZE = (long) (-EXPECTED_INSERTIONS * Math.log(FPP) / (Math.log(2) * Math.log(2)));
    /** Optimal hash count: (m/n)*ln2 */
    private static final int HASH_COUNT = Math.max(1, (int) ((BIT_SIZE / EXPECTED_INSERTIONS) * Math.log(2)));

    private final StringRedisTemplate redisTemplate;

    /**
     * Check if a shortCode possibly exists. False positives are possible,
     * but false negatives are NOT (guaranteed).
     */
    public boolean mightContain(String shortCode) {
        long[] hashes = hash(shortCode);
        for (long h : hashes) {
            long offset = Math.abs(h % BIT_SIZE);
            Boolean bit = redisTemplate.opsForValue().getBit(REDIS_KEY, offset);
            if (Boolean.FALSE.equals(bit)) {
                return false; // definitely not present
            }
        }
        return true;
    }

    /**
     * Add a shortCode to the filter (called when a new short link is created).
     */
    public void add(String shortCode) {
        long[] hashes = hash(shortCode);
        for (long h : hashes) {
            long offset = Math.abs(h % BIT_SIZE);
            redisTemplate.opsForValue().setBit(REDIS_KEY, offset, true);
        }
    }

    /**
     * Rebuild the Bloom filter from a list of all active shortCodes.
     * Should be called on application startup.
     */
    @Async
    public void rebuild(List<String> shortCodes) {
        log.info("Rebuilding Bloom filter with {} shortCodes...", shortCodes.size());
        // Clear and rebuild
        redisTemplate.delete(REDIS_KEY);
        for (String code : shortCodes) {
            add(code);
        }
        log.info("Bloom filter rebuild complete: {} shortCodes, {} bits", shortCodes.size(), BIT_SIZE);
    }

    /**
     * Generate HASH_COUNT deterministic hashes from a shortCode.
     * Uses SHA-256 with different seed bytes to simulate multiple hash functions.
     */
    private long[] hash(String value) {
        long[] result = new long[HASH_COUNT];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < HASH_COUNT; i++) {
                md.reset();
                md.update((byte) i); // seed
                md.update(value.getBytes(StandardCharsets.UTF_8));
                byte[] digest = md.digest();
                // Take first 8 bytes as long
                long h = 0;
                for (int j = 0; j < 8; j++) {
                    h = (h << 8) | (digest[j] & 0xFF);
                }
                result[i] = h;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}