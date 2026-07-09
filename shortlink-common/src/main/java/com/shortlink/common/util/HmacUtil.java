package com.shortlink.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC-SHA256 utility for API authentication.
 * Used by both server-side HmacAuthInterceptor and client-side SDK.
 */
public final class HmacUtil {

    private HmacUtil() {}

    /**
     * Compute HMAC-SHA256 signature as lowercase hex string.
     * @param payload the string to sign (e.g., "POST\n/path\ntimestamp\nnonce")
     * @param secret the signing key
     */
    public static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }
}