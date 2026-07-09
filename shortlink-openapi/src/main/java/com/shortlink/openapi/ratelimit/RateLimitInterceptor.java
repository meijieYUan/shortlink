package com.shortlink.openapi.ratelimit;

import com.shortlink.common.exception.BizException;
import com.shortlink.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rate limit interceptor for /openapi/** endpoints.
 * Acquires a permit per AppKey before allowing the request through.
 * Returns 429 Too Many Requests with X-RateLimit-* headers when exceeded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!request.getRequestURI().startsWith("/openapi/")) {
            return true;
        }

        String appKey = (String) request.getAttribute("appKey");
        if (appKey == null) {
            // Auth interceptor should have set this; if not, let auth interceptor reject
            return true;
        }

        if (!rateLimiterService.tryAcquire(appKey)) {
            log.warn("Rate limit exceeded: appKey={}, uri={}", appKey, request.getRequestURI());
            response.setHeader("X-RateLimit-Retry-After", "1");
            throw new BizException(ResultCode.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Try again in 1 second.");
        }

        return true;
    }
}