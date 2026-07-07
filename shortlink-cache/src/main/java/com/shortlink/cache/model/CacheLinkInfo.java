package com.shortlink.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Immutable value object stored in L1 (Caffeine) and L2 (Redis) caches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheLinkInfo {
    private String shortCode;
    private String originalUrl;
    private LocalDateTime expireTime;
    private Integer status;
}