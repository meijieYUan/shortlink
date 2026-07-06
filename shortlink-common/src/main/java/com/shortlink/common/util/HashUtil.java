package com.shortlink.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * URL hash utility. Uses MD5 for fast, non-cryptographic URL deduplication.
 * Output: 32 hex chars (128 bits). Uses ThreadLocal MessageDigest for thread safety.
 *
 * Note: MD5 is NOT used for cryptographic purposes here — only for O(1) index lookup.
 * Full original_url is always compared as a secondary guard against hash collisions.
 */
public final class HashUtil {

    private HashUtil() {}

    private static final String ALGORITHM = "MD5";

    private static final ThreadLocal<MessageDigest> MD = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    });

    /**
     * Compute the full 32-char MD5 hex digest of the normalized URL.
     * Thread-safe via ThreadLocal MessageDigest.
     */
    public static String md5Hex(String input) {
        MessageDigest md = MD.get();
        md.reset();
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}