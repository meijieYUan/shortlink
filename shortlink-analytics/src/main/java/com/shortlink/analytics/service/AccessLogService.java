package com.shortlink.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.analytics.model.AccessLog;
import com.shortlink.analytics.repository.AccessLogMapper;
import com.shortlink.common.auth.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Access log service with tenant isolation via JOIN with t_short_link.
 *
 * Design rationale: t_access_log does NOT carry app_key directly.
 * Instead, every admin-facing query joins with t_short_link on short_code
 * to scope results to the current tenant. This keeps the redirect hot path
 * (t_access_log insert) lean and delegate tenant filtering to the
 * less-frequent analytics query path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogMapper accessLogMapper;

    /** Page query — tenant-scoped via short_code subquery */
    public Page<AccessLog> page(int page, int size, String shortCode, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AccessLog> qw = new LambdaQueryWrapper<>();
        applyTenantFilter(qw);
        if (shortCode != null && !shortCode.isBlank()) qw.eq(AccessLog::getShortCode, shortCode);
        if (startDate != null) qw.ge(AccessLog::getAccessTime, startDate.atStartOfDay());
        if (endDate != null) qw.le(AccessLog::getAccessTime, endDate.plusDays(1).atStartOfDay());
        qw.orderByDesc(AccessLog::getAccessTime);
        return accessLogMapper.selectPage(new Page<>(page, size), qw);
    }

    public void batchInsert(List<AccessLog> logs) {
        if (logs == null || logs.isEmpty()) return;
        try { accessLogMapper.batchInsert(logs); }
        catch (Exception e) { log.error("Batch insert failed, count={}", logs.size(), e); throw e; }
    }

    /** Total PV — tenant-scoped */
    public long totalPv() {
        LambdaQueryWrapper<AccessLog> qw = new LambdaQueryWrapper<>();
        applyTenantFilter(qw);
        return accessLogMapper.selectCount(qw);
    }

    /** Per-link stats (top N) — tenant-scoped via JOIN */
    public List<Map<String, Object>> perLinkStats(LocalDate startDate, LocalDate endDate) {
        QueryWrapper<AccessLog> qw = new QueryWrapper<>();
        applyTenantFilter(qw);
        qw.select("short_code AS shortCode", "COUNT(0) AS pv");
        qw.groupBy("short_code");
        qw.orderByDesc("pv");
        qw.last("LIMIT 50");
        if (startDate != null) qw.ge("access_time", startDate.atStartOfDay());
        if (endDate != null) qw.le("access_time", endDate.plusDays(1).atStartOfDay());
        List<Map<String, Object>> rows = accessLogMapper.selectMaps(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("shortCode", row.get("shortCode"));
            entry.put("pv", row.get("pv"));
            result.add(entry);
        }
        return result;
    }

    public long countByShortCode(String shortCode) {
        LambdaQueryWrapper<AccessLog> qw = new LambdaQueryWrapper<>();
        applyTenantFilter(qw);
        qw.eq(AccessLog::getShortCode, shortCode);
        return accessLogMapper.selectCount(qw);
    }

    /** Daily trend — tenant-scoped via JOIN */
    public List<Map<String, Object>> dailyTrend(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1);
        QueryWrapper<AccessLog> qw = new QueryWrapper<>();
        applyTenantFilter(qw);
        qw.select("DATE(access_time) AS access_date", "COUNT(0) AS cnt");
        qw.between("access_time", start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        qw.groupBy("DATE(access_time)");
        qw.orderByAsc("access_date");
        List<Map<String, Object>> rows = accessLogMapper.selectMaps(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", String.valueOf(row.get("access_date")));
            entry.put("pv", row.get("cnt"));
            result.add(entry);
        }
        return result;
    }

    /**
     * Apply tenant scope to any AccessLog query.
     * Uses a subquery: short_code IN (SELECT short_code FROM t_short_link WHERE app_key = ?)
     * Because short_code is globally unique, this JOIN correctly scopes all access logs
     * to the links owned by the current tenant.
     */
    private void applyTenantFilter(LambdaQueryWrapper<AccessLog> qw) {
        String appKey = TenantContext.get();
        if (!appKey.isEmpty()) {
            qw.apply("short_code IN (SELECT short_code FROM t_short_link WHERE app_key = {0})", appKey);
        }
    }

    private void applyTenantFilter(QueryWrapper<AccessLog> qw) {
        String appKey = TenantContext.get();
        if (!appKey.isEmpty()) {
            qw.apply("short_code IN (SELECT short_code FROM t_short_link WHERE app_key = {0})", appKey);
        }
    }
}