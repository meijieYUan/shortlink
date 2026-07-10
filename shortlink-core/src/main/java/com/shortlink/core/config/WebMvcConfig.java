package com.shortlink.core.config;

import com.shortlink.openapi.ratelimit.RateLimitInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
/*
在Spring Boot中，Filter 通常可以自动被识别和注册，而 Interceptor 则必须进行显式的配置才能生效。
Interceptor类型 通过实现 WebMvcConfigurer 接口，并重写 addInterceptors 方法
Filter (过滤器) 直接声明为 @Component 的 Filter 实现类  @Order 注解 指定顺序

客户端请求 → Filter Chain（过滤器链） → DispatcherServlet → Interceptor.preHandle() →
 Controller → Interceptor.postHandle() → 视图渲染 → Interceptor.afterCompletion() → 客户端响应
 */
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Request log interceptor — all paths except static resources
        registry.addInterceptor(new RequestLogInterceptor())
            .addPathPatterns("/**")
            .excludePathPatterns(Arrays.asList("/actuator/**", "/favicon.ico"));

        // Rate limit — OpenAPI only (HMAC auth is handled by HmacAuthFilter)
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/openapi/**")
            .order(2);
    }

    private static class RequestLogInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            request.setAttribute("startTime", System.currentTimeMillis());
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                     Object handler, Exception ex) {
            Long startTime = (Long) request.getAttribute("startTime");
            if (startTime != null) {
                long cost = System.currentTimeMillis() - startTime;
                log.info("{} {} -> {} {}ms",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), cost);
            }
        }
    }
}