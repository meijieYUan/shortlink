package com.shortlink.core.controller;

import com.shortlink.common.result.Result;
import com.shortlink.core.model.dto.ShortenRequest;
import com.shortlink.core.model.dto.ShortenResponse;
import com.shortlink.core.model.entity.ShortLink;
import com.shortlink.core.service.ShortLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Open API endpoints — authenticated via HMAC-SHA256 + rate limited.
 * Path prefix: /openapi/v1
 */
@Slf4j
@RestController
@RequestMapping("/openapi/v1")
@RequiredArgsConstructor
public class OpenApiController {

    private final ShortLinkService shortLinkService;

    @PostMapping("/shorten")
    public Result<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        log.info("OpenAPI shorten: url={}", request.getOriginalUrl());
        return Result.success(shortLinkService.shorten(request));
    }

    @GetMapping("/shorten/{shortCode}")
    public Result<?> lookup(@PathVariable String shortCode) {
        String originalUrl = shortLinkService.getOriginalUrl(shortCode);
        if (originalUrl == null) {
            if (shortLinkService.isExpired(shortCode)) {
                return Result.fail(com.shortlink.common.result.ResultCode.GONE);
            }
            return Result.fail(com.shortlink.common.result.ResultCode.NOT_FOUND);
        }
        return Result.success(java.util.Map.of(
            "shortCode", shortCode,
            "originalUrl", originalUrl
        ));
    }
}