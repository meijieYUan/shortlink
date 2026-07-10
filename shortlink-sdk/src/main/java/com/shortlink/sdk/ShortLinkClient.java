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
 *
 * Follows API_AUTH_DESIGN.md spec:
 * - X-AccessKey header (not X-AppKey)
 * - Signature covers method + path + timestamp + nonce + body-MD5 + content-type
 */
public class ShortLinkClient {

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

    public ShortenResult shorten(String originalUrl) throws Exception {
        String json = String.format("{\"originalUrl\":\"%s\"}", originalUrl);
        return doRequest("POST", "/openapi/v1/shorten", json);
    }

    public ShortenResult shorten(String originalUrl, String expireTime) throws Exception {
        String json = String.format("{\"originalUrl\":\"%s\",\"expireTime\":\"%s\"}", originalUrl, expireTime);
        return doRequest("POST", "/openapi/v1/shorten", json);
    }

    public ShortenResult lookup(String shortCode) throws Exception {
        String path = "/openapi/v1/shorten/" + shortCode;
        HttpResponse<String> resp = signedRequest("GET", path, "");
        if (resp.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode data = root.get("data");
            return new ShortenResult(0, data.get("shortCode").asText(),
                "", data.get("originalUrl").asText());
        }
        throw new RuntimeException("Lookup failed: HTTP " + resp.statusCode());
    }

    private ShortenResult doRequest(String method, String path, String body) throws Exception {
        HttpResponse<String> resp = signedRequest(method, path, body);
        if (resp.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode data = root.get("data");
            return new ShortenResult(
                data.get("id").asLong(),
                data.get("shortCode").asText(),
                data.get("shortUrl").asText(),
                data.get("originalUrl").asText()
            );
        }
        throw new RuntimeException("Request failed: HTTP " + resp.statusCode() + " " + resp.body());
    }

    private HttpResponse<String> signedRequest(String method, String path, String body) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String contentType = body.isEmpty() ? "" : "application/json";

        String signature = HmacUtil.hmacSha256Hex(method, path, timestamp, nonce, body, contentType, accessSecret);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("X-AccessKey", accessKey)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature);

        if (!body.isEmpty()) {
            builder.header("Content-Type", contentType)
                   .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public record ShortenResult(long id, String shortCode, String shortUrl, String originalUrl) {}

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