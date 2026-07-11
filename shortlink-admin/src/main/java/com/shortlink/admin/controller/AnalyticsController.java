package com.shortlink.admin.controller;

import com.shortlink.common.result.Result;
import com.shortlink.core.service.impl.ShortLinkServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Analytics dashboard API — currently provides basic aggregated stats.
 * Detailed PV/UV/geo analysis will be available after Elasticsearch integration (Phase 5b).
 */
@RestController
@RequestMapping("/openapi/v1/admin")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ShortLinkServiceImpl shortLinkService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(shortLinkService.getStats());
    }
}