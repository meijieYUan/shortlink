package com.shortlink.openapi.auth;

import com.shortlink.common.exception.BizException;
import com.shortlink.common.result.ResultCode;
import com.shortlink.common.util.HmacUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HMAC-SHA256 authentication interceptor for /openapi/** endpoints.
 *
 * Required headers:
 * - X-AppKey: application key
 * - X-Timestamp: epoch millis (must be within ±5 minutes of server time)
 * - X-Nonce: unique per request (anti-replay)
 * - X-Signature: hex(HMAC-SHA256(appSecret, method+"\n"+path+"\n"+timestamp+"\n"+nonce))
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HmacAuthInterceptor implements HandlerInterceptor {

    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000;

    private final ApiKeyStore apiKeyStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!request.getRequestURI().startsWith("/openapi/")) {
            return true;
        }

        String appKey = request.getHeader("X-AppKey");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String signature = request.getHeader("X-Signature");

        if (appKey == null || timestamp == null || nonce == null || signature == null) {
            log.warn("Missing auth headers: uri={}", request.getRequestURI());
            throw new BizException(ResultCode.UNAUTHORIZED, "Missing required authentication headers");
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "Invalid X-Timestamp format");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TIMESTAMP_WINDOW_MS) {
            log.warn("Timestamp out of window: appKey={}", appKey);
            throw new BizException(ResultCode.UNAUTHORIZED, "Request timestamp expired");
        }

        String secret = apiKeyStore.getSecret(appKey)
            .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "Invalid AppKey"));

        String payload = request.getMethod() + "\n" + request.getRequestURI() + "\n"
                        + timestamp + "\n" + nonce;
        String expected = HmacUtil.hmacSha256Hex(payload, secret);

        if (!expected.equals(signature)) {
            log.warn("Signature mismatch: appKey={}, uri={}", appKey, request.getRequestURI());
            throw new BizException(ResultCode.UNAUTHORIZED, "Invalid signature");
        }

        request.setAttribute("appKey", appKey);
        log.debug("Auth passed: appKey={}", appKey);
        return true;
    }
}