package com.shortlink.openapi.ratelimit;

import com.shortlink.common.exception.BizException;
import com.shortlink.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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

        String accessKey = (String) request.getAttribute("accessKey");
        if (accessKey == null) {
            return true;
        }

        if (!rateLimiterService.tryAcquire(accessKey)) {
            log.warn("Rate limit exceeded: accessKey={}, uri={}", accessKey, request.getRequestURI());
            response.setHeader("X-RateLimit-Retry-After", "1");
            throw new BizException(ResultCode.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Try again in 1 second.");
        }

        return true;
    }
}