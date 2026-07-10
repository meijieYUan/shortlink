package com.shortlink.core.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.common.result.Result;
import com.shortlink.core.model.entity.ShortLink;
import com.shortlink.core.service.impl.ShortLinkServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/openapi/v1/admin/links")
@RequiredArgsConstructor
public class LinkAdminController {

    private final ShortLinkServiceImpl shortLinkService;

    @GetMapping
    public Result<Page<ShortLink>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(shortLinkService.listLinks(page, size, status, keyword));
    }

    @GetMapping("/{shortCode}")
    public Result<ShortLink> getOne(@PathVariable String shortCode) {
        ShortLink link = shortLinkService.getLink(shortCode);
        if (link == null) {
            return Result.fail(com.shortlink.common.result.ResultCode.NOT_FOUND);
        }
        return Result.success(link);
    }

    @PutMapping("/{shortCode}")
    public Result<ShortLink> update(
            @PathVariable String shortCode,
            @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") != null ? ((Number) body.get("status")).intValue() : null;
        String customCode = (String) body.get("customCode");
        LocalDateTime expireTime = null;
        if (body.get("expireTime") != null) {
            expireTime = LocalDateTime.parse((String) body.get("expireTime"));
        }
        return Result.success(shortLinkService.updateLink(shortCode, status, customCode, expireTime));
    }

    @DeleteMapping("/{shortCode}")
    public Result<Void> delete(@PathVariable String shortCode) {
        shortLinkService.softDelete(shortCode);
        return Result.success();
    }

    @PostMapping("/{shortCode}/restore")
    public Result<Void> restore(@PathVariable String shortCode) {
        shortLinkService.restore(shortCode);
        return Result.success();
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(shortLinkService.getStats());
    }
}