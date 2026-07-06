package com.shortlink.core.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShortenRequest {

    @NotBlank(message = "originalUrl must not be empty")
    @Size(max = 2048, message = "URL exceeds maximum length")
    private String originalUrl;

    /** Optional expiration time */
    private LocalDateTime expireTime;
}