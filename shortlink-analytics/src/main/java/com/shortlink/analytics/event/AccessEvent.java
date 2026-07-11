package com.shortlink.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessEvent {
    private String shortCode;
    private String ip;
    private String userAgent;
    private String referer;
    private LocalDateTime timestamp;
    private String country;
    private String city;
}