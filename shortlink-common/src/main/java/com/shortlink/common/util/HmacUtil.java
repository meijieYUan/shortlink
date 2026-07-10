package com.shortlink.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC-SHA256 utility for OpenAPI authentication.
 *
 * Signature string format (aligned with API_AUTH_DESIGN.md):
 *   method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyMd5 + "\n" + contentType
 *
 * Where bodyMd5 = Hex(MD5(body)) for POST/PUT, empty string for GET/DELETE.
 */
public final class HmacUtil {

    private HmacUtil() {}

    /**
     * Compute HMAC-SHA256 signature.
     *
     * @param method      HTTP method (GET/POST/PUT/DELETE)
     * @param path        request path (e.g., "/openapi/v1/shorten")
     * @param timestamp   X-Timestamp header value (epoch millis)
     * @param nonce       X-Nonce header value
     * @param body        request body string (null or empty for GET)
     * @param contentType Content-Type header value (e.g., "application/json")
     * @param secret      AccessSecret
     * @return lowercase hex signature string
     */
    /*
    nonce（Number used once，一次性数字）是一个安全设计模式和编程概念。它的核心目的是防止重放攻击
     */
    public static String hmacSha256Hex(String method, String path, String timestamp,
                                        String nonce, String body, String contentType,
                                        String secret) {
        String bodyMd5 = (body != null && !body.isEmpty())
            ? HashUtil.md5Hex(body) : "";
        String payload = method + "\n"
                       + path + "\n"
                       + timestamp + "\n"
                       + nonce + "\n"
                       + bodyMd5 + "\n"
                       + (contentType != null ? contentType : "");

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