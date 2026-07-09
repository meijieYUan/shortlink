package com.shortlink.openapi.config;

import com.shortlink.openapi.auth.HmacAuthInterceptor;
import com.shortlink.openapi.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class OpenApiConfig implements WebMvcConfigurer {

    private final HmacAuthInterceptor hmacAuthInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Auth first, then rate limit
        registry.addInterceptor(hmacAuthInterceptor)
            .addPathPatterns("/openapi/**")
            .order(1);
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/openapi/**")
            .order(2);
    }
}