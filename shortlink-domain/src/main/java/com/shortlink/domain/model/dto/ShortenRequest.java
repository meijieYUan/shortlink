package com.shortlink.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShortenRequest {

    @NotBlank(message = "originalUrl must not be empty")
    @Size(max = 2048, message = "URL exceeds maximum length")
    private String originalUrl;
    private LocalDateTime expireTime;

    @Size(max = 8, message = "Custom code max 8 characters")
    @Pattern(regexp = "^[0-9A-Za-z]*$", message = "Custom code must be alphanumeric")
    private String customCode;
}