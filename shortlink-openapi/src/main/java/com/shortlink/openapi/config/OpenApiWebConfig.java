package com.shortlink.openapi.config;

import com.shortlink.common.auth.TenantInterceptor;
import com.shortlink.openapi.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Order(1)
@RequiredArgsConstructor
public class OpenApiWebConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
            .addPathPatterns("/openapi/**")
            .order(1);

        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/openapi/**")
            .order(2);
    }
}