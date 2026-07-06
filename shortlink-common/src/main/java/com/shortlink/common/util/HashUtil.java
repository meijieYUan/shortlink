package com.shortlink.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * URL hash utility. Uses SHA-256 (first 16 hex chars = 64 bits) for deduplication.
 * Full original URL is always stored and secondarily compared to guard against hash collision.
 */
public final class HashUtil {

    private HashUtil() {}

    private static final String ALGORITHM = "SHA-256";
    private static final int HASH_LENGTH = 16; // first 64 bits

    private static final ThreadLocal<MessageDigest> MD = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    });

    /**
     * Compute the first 16 hex chars of SHA-256 for the given normalized URL.
     * Thread-safe via ThreadLocal MessageDigest.
     */
    public static String sha256Hex16(String input) {
        MessageDigest md = MD.get();
        md.reset();
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest).substring(0, HASH_LENGTH);
    }
}