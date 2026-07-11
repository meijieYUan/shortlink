package com.shortlink.admin.controller;

import com.shortlink.analytics.service.AccessLogService;
import com.shortlink.common.result.Result;
import com.shortlink.domain.service.OpenApiLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/openapi/v1/admin")
@RequiredArgsConstructor
public class AnalyticsController {

    private final OpenApiLinkService linkService;
    private final AccessLogService accessLogService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> s = linkService.getStats();
        s = new HashMap<>(s);
        s.put("totalPv", accessLogService.totalPv());
        return Result.success(s);
    }

    @GetMapping("/analytics/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> s = new HashMap<>(linkService.getStats());
        s.put("totalPv", accessLogService.totalPv());
        s.put("todayCreated", linkService.countTodayLinks());
        return Result.success(s);
    }

    @GetMapping("/analytics/daily-trend")
    public Result<List<Map<String, Object>>> dailyTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(accessLogService.dailyTrend(Math.min(days, 90)));
    }

    @GetMapping("/analytics/top-links")
    public Result<List<Map<String, Object>>> topLinks(@RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDate s = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(7);
        LocalDate e = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        return Result.success(accessLogService.perLinkStats(s, e));
    }
}