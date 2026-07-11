package com.shortlink.openapi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.analytics.service.AccessLogService;
import com.shortlink.common.result.Result;
import com.shortlink.common.result.ResultCode;
import com.shortlink.domain.model.dto.ShortenRequest;
import com.shortlink.domain.model.dto.ShortenResponse;
import com.shortlink.domain.model.entity.ShortLink;
import com.shortlink.domain.service.OpenApiLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/openapi/v1")
@RequiredArgsConstructor
public class OpenApiController {

    private final OpenApiLinkService linkService;
    private final AccessLogService accessLogService;

    @PostMapping("/shorten")
    public Result<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        return Result.success(linkService.shorten(request));
    }

    @GetMapping("/shorten")
    public Result<Page<ShortLink>> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status, @RequestParam(required = false) String keyword) {
        return Result.success(linkService.listLinks(page, size, status, keyword));
    }

    @GetMapping("/shorten/{shortCode}")
    public Result<?> lookup(@PathVariable String shortCode) {
        String originalUrl = linkService.getOriginalUrl(shortCode);
        if (originalUrl == null) {
            if (linkService.isExpired(shortCode)) return Result.fail(ResultCode.GONE);
            return Result.fail(ResultCode.NOT_FOUND);
        }
        return Result.success(Map.of("shortCode", shortCode, "originalUrl", originalUrl));
    }

    @PutMapping("/shorten/{shortCode}")
    public Result<ShortLink> update(@PathVariable String shortCode, @RequestBody Map<String, Object> body) {
        Integer s = body.get("status") != null ? ((Number) body.get("status")).intValue() : null;
        LocalDateTime exp = body.get("expireTime") != null ? LocalDateTime.parse(body.get("expireTime").toString().replace("T", " ")) : null;
        return Result.success(linkService.updateLink(shortCode, s, null, exp));
    }

    @DeleteMapping("/shorten/{shortCode}")
    public Result<Void> delete(@PathVariable String shortCode) { linkService.softDelete(shortCode); return Result.success(); }

    @PostMapping("/shorten/{shortCode}/restore")
    public Result<Void> restore(@PathVariable String shortCode) { linkService.restore(shortCode); return Result.success(); }

    @GetMapping("/shorten/{shortCode}/stats")
    public Result<Map<String, Object>> linkStats(@PathVariable String shortCode) {
        return Result.success(Map.of("shortCode", shortCode, "pv", accessLogService.countByShortCode(shortCode)));
    }

    @GetMapping("/analytics/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> stats = linkService.getStats();
        Map<String, Object> result = new HashMap<>(stats);
        result.put("totalPv", accessLogService.totalPv());
        result.put("todayCreated", linkService.countTodayLinks());
        return Result.success(result);
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