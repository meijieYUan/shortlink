package com.shortlink.openapi.auth;

import com.shortlink.common.exception.BizException;
import com.shortlink.common.result.ResultCode;
import com.shortlink.common.util.HmacUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

/**
 * HMAC-SHA256 authentication filter for /openapi/** paths.
 * Uses ContentCachingRequestWrapper to read the request body for signature verification.
 *
 * Validates (in order):
 * 1. Required headers present (X-AccessKey, X-Timestamp, X-Nonce, X-Signature)
 * 2. Timestamp within ±5 minute window (anti-replay)
 * 3. Nonce not reused (Redis SET NX, anti-replay)
 * 4. IP whitelist match (if configured)
 * 5. Signature verification (HMAC-SHA256 with body-MD5)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HmacAuthFilter extends OncePerRequestFilter {

    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000;
    private static final String NONCE_PREFIX = "nonce:";

    private final ApiKeyStore apiKeyStore;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/openapi/")) {
            chain.doFilter(request, response);
            return;
        }

        // Wrap request to allow reading body multiple times
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);

        try {
            authenticate(wrapper);
        } catch (BizException e) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + e.getCode()
                + ",\"message\":\"" + e.getMessage() + "\"}");
            return;
        }

        chain.doFilter(wrapper, response);
    }

    private void authenticate(ContentCachingRequestWrapper request) throws IOException {
        String accessKey = request.getHeader("X-AccessKey");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String signature = request.getHeader("X-Signature");
        String contentType = request.getContentType();

        // 1. Required headers
        if (accessKey == null || timestamp == null || nonce == null || signature == null) {
            log.warn("Missing auth headers: uri={}", request.getRequestURI());
            throw new BizException(ResultCode.UNAUTHORIZED, "Missing required authentication headers");
        }

        // 2. Timestamp window
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "Invalid X-Timestamp format");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TIMESTAMP_WINDOW_MS) {
            log.warn("Timestamp expired: accessKey={}", accessKey);
            throw new BizException(ResultCode.UNAUTHORIZED, "Invalid credentials");
        }

        // 3. Nonce anti-replay (Redis SET NX)
        String nonceKey = NONCE_PREFIX + accessKey + ":" + nonce;
        Boolean set = redisTemplate.opsForValue()
            .setIfAbsent(nonceKey, "1", Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(set)) {
            log.warn("Nonce replay detected: accessKey={}, nonce={}", accessKey, nonce);
            throw new BizException(ResultCode.UNAUTHORIZED, "Invalid credentials");
        }

        // 4. IP whitelist check
        String clientIp = request.getRemoteAddr();
        if (!apiKeyStore.matchIpWhitelist(accessKey, clientIp)) {
            log.warn("IP not in whitelist: accessKey={}, ip={}", accessKey, clientIp);
            throw new BizException(ResultCode.FORBIDDEN, "IP not authorized");
        }

        // 5. Signature verification
        String secret = apiKeyStore.getSecret(accessKey)
            .orElseThrow(() -> {
                log.warn("Unknown AccessKey: {}", accessKey);
                return new BizException(ResultCode.UNAUTHORIZED, "Invalid credentials");
            });

        // Read body for signature (trigger caching in ContentCachingRequestWrapper)
        String body = "";
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        if (bodyBytes.length > 0) {
            body = new String(bodyBytes, StandardCharsets.UTF_8);
        }

        String expected = HmacUtil.hmacSha256Hex(
            request.getMethod(), request.getRequestURI(),
            timestamp, nonce, body, contentType, secret);

        if (!expected.equals(signature)) {
            log.warn("Signature mismatch: accessKey={}, uri={}", accessKey, request.getRequestURI());
            throw new BizException(ResultCode.UNAUTHORIZED, "Invalid credentials");
        }

        request.setAttribute("accessKey", accessKey);
        log.debug("Auth passed: accessKey={}", accessKey);
    }
}