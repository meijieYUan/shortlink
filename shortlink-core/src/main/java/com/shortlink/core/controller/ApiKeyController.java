package com.shortlink.core.controller;

import com.shortlink.common.result.Result;
import com.shortlink.openapi.auth.ApiKeyEntity;
import com.shortlink.openapi.service.AccessKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API key management endpoints for /openapi/v1/admin/keys.
 * These also go through HmacAuthFilter (authentication required).
 */
@Slf4j
@RestController
@RequestMapping("/openapi/v1/admin/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final AccessKeyService accessKeyService;

    @PostMapping
    public Result<Map<String, Object>> create(
            @RequestBody Map<String, Object> body) {
        String owner = (String) body.getOrDefault("owner", "");
        String ipWhitelist = (String) body.get("ipWhitelist");
        Integer quota = body.get("quotaPerMinute") != null
            ? ((Number) body.get("quotaPerMinute")).intValue() : null;

        ApiKeyEntity created = accessKeyService.createKey(owner, ipWhitelist, quota);
        return Result.success(Map.of(
            "accessKey", created.getAppKey(),
            "secretKey", created.getAppSecret(),
            "owner", created.getOwner(),
            "status", created.getStatus()
        ));
    }

    @GetMapping
    public Result<java.util.List<ApiKeyEntity>> list(
            @RequestParam(required = false) Integer status) {
        return Result.success(accessKeyService.listKeys(status));
    }

    @PutMapping("/{accessKey}")
    public Result<ApiKeyEntity> update(@PathVariable String accessKey,
                                        @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") != null
            ? ((Number) body.get("status")).intValue() : null;
        String ipWhitelist = (String) body.get("ipWhitelist");
        Integer quota = body.get("quotaPerMinute") != null
            ? ((Number) body.get("quotaPerMinute")).intValue() : null;

        ApiKeyEntity updated = accessKeyService.updateKey(accessKey, status, ipWhitelist, quota);
        return Result.success(updated);
    }

    @DeleteMapping("/{accessKey}")
    public Result<Void> delete(@PathVariable String accessKey) {
        accessKeyService.deleteKey(accessKey);
        return Result.success();
    }
}