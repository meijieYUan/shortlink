package com.shortlink.domain.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenResponse {
    private Long id;
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private LocalDateTime expireTime;
}