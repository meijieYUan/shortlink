package com.shortlink.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.common.util.HmacUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * ShortLink Java SDK — auto-signs requests with HMAC-SHA256.
 *
 * Follows API_AUTH_DESIGN.md spec:
 * - X-AccessKey header
 * - Nonce via SecureRandom (32 hex chars)
 * - Signature: HMAC-SHA256(method + path + timestamp + nonce + body-MD5 + content-type, secret)
 */
public class ShortLinkClient {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 16; // 32 hex chars

    private final String baseUrl;
    private final String accessKey;
    private final String accessSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private ShortLinkClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.accessKey = builder.accessKey;
        this.accessSecret = builder.accessSecret;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    // ---- Public API ----

    public ShortenResult shorten(String originalUrl) throws Exception {
        return shorten(originalUrl, null);
    }

    public ShortenResult shorten(String originalUrl, String expireTime) throws Exception {
        var bodyMap = new java.util.LinkedHashMap<String, Object>();
        bodyMap.put("originalUrl", originalUrl);
        if (expireTime != null) {
            bodyMap.put("expireTime", expireTime);
        }
        String body = objectMapper.writeValueAsString(bodyMap);
        return doPost("/openapi/v1/shorten", body);
    }

    public ShortenResult lookup(String shortCode) throws Exception {
        HttpResponse<String> resp = signedRequest("GET", "/openapi/v1/shorten/" + shortCode, "");
        if (resp.statusCode() == 200) {
            JsonNode data = objectMapper.readTree(resp.body()).get("data");
            return new ShortenResult(0, data.get("shortCode").asText(),
                "", data.get("originalUrl").asText());
        }
        throw new RuntimeException("Lookup failed: HTTP " + resp.statusCode());
    }

    public record ShortenResult(long id, String shortCode, String shortUrl, String originalUrl) {}

    // ---- Internal ----

    private ShortenResult doPost(String path, String body) throws Exception {
        HttpResponse<String> resp = signedRequest("POST", path, body);
        if (resp.statusCode() == 200) {
            JsonNode data = objectMapper.readTree(resp.body()).get("data");
            return new ShortenResult(
                data.get("id").asLong(),
                data.get("shortCode").asText(),
                data.get("shortUrl").asText(),
                data.get("originalUrl").asText());
        }
        throw new RuntimeException("Request failed: HTTP " + resp.statusCode() + " " + resp.body());
    }

    private HttpResponse<String> signedRequest(String method, String path, String body) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = generateNonce();
        String contentType = body.isEmpty() ? "" : "application/json";
        String signature = HmacUtil.hmacSha256Hex(method, path, timestamp, nonce, body, contentType, accessSecret);

        var builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("X-AccessKey", accessKey)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature);
        /*
        在 HttpClient 的设计中，每个请求都必须有一个 BodyPublisher（即使是空的）。
        如果你调用 builder.GET() 或 builder.DELETE() 等快捷方法，它们内部会自动设置一个 noBody() 的发布器。
        但当你使用通用的 method() 方法时，必须显式提供一个 BodyPublisher 实例。

         */
        if (!body.isEmpty()) {
            builder.header("Content-Type", contentType)
                   .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ---- Static helpers ----

    static String generateNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    // ---- Builder ----

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String baseUrl = "http://localhost:8080";
        private String accessKey;
        private String accessSecret;

        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder accessKey(String accessKey) { this.accessKey = accessKey; return this; }
        public Builder accessSecret(String accessSecret) { this.accessSecret = accessSecret; return this; }

        public ShortLinkClient build() {
            if (accessKey == null || accessSecret == null) {
                throw new IllegalArgumentException("accessKey and accessSecret are required");
            }
            return new ShortLinkClient(this);
        }
    }
}