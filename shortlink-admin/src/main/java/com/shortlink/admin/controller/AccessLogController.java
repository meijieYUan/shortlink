package com.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.analytics.model.AccessLog;
import com.shortlink.analytics.service.AccessLogService;
import com.shortlink.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/openapi/v1/admin/access-logs")
@RequiredArgsConstructor
public class AccessLogController {

    private final AccessLogService accessLogService;

    @GetMapping
    public Result<Page<AccessLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String shortCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : null;
        return Result.success(accessLogService.page(page, size, shortCode, start, end));
    }

    @GetMapping("/count")
    public Result<Map<String, Object>> count(@RequestParam String shortCode) {
        long pv = accessLogService.countByShortCode(shortCode);
        return Result.success(Map.of("shortCode", shortCode, "pv", pv));
    }
}