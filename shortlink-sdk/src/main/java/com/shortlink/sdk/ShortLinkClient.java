package com.shortlink.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.common.util.HmacUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * ShortLink Java SDK — auto-signs requests with HMAC-SHA256.
 */
public class ShortLinkClient {

    private final String baseUrl;
    private final String appKey;
    private final String appSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private ShortLinkClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.appKey = builder.appKey;
        this.appSecret = builder.appSecret;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    public ShortenResult shorten(String originalUrl) throws Exception {
        String json = String.format("{\"originalUrl\":\"%s\"}", originalUrl);
        return doShorten(json);
    }

    public ShortenResult shorten(String originalUrl, String expireTime) throws Exception {
        String json = String.format("{\"originalUrl\":\"%s\",\"expireTime\":\"%s\"}", originalUrl, expireTime);
        return doShorten(json);
    }

    private ShortenResult doShorten(String body) throws Exception {
        String path = "/openapi/v1/shorten";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");

        String payload = "POST\n" + path + "\n" + timestamp + "\n" + nonce;
        String signature = HmacUtil.hmacSha256Hex(payload, appSecret);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("X-AppKey", appKey)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.get("data");
            return new ShortenResult(
                data.get("id").asLong(),
                data.get("shortCode").asText(),
                data.get("shortUrl").asText(),
                data.get("originalUrl").asText()
            );
        }

        throw new RuntimeException("Shorten failed: HTTP " + response.statusCode() + " " + response.body());
    }

    /**
     * Look up a short link by its short code.
     */
    public ShortenResult lookup(String shortCode) throws Exception {
        String path = "/openapi/v1/shorten/" + shortCode;
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");

        String payload = "GET\n" + path + "\n" + timestamp + "\n" + nonce;
        String signature = HmacUtil.hmacSha256Hex(payload, appSecret);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("X-AppKey", appKey)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.get("data");
            return new ShortenResult(0, data.get("shortCode").asText(),
                "", data.get("originalUrl").asText());
        }
        if (response.statusCode() == 410) {
            throw new RuntimeException("Short link expired");
        }
        if (response.statusCode() == 404) {
            throw new RuntimeException("Short link not found");
        }
        throw new RuntimeException("Lookup failed: HTTP " + response.statusCode());
    }

    public record ShortenResult(long id, String shortCode, String shortUrl, String originalUrl) {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String baseUrl = "http://localhost:8080";
        private String appKey;
        private String appSecret;

        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder appKey(String appKey) { this.appKey = appKey; return this; }
        public Builder appSecret(String appSecret) { this.appSecret = appSecret; return this; }

        public ShortLinkClient build() {
            if (appKey == null || appSecret == null) {
                throw new IllegalArgumentException("appKey and appSecret are required");
            }
            return new ShortLinkClient(this);
        }
    }
}