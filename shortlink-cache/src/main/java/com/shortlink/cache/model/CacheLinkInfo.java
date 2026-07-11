package com.shortlink.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheLinkInfo {
    private String shortCode;
    private String originalUrl;
    private String appKey;
    private LocalDateTime expireTime;
    private Integer status;
}