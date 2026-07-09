package com.shortlink.core.controller;

import com.shortlink.common.result.Result;
import com.shortlink.core.model.dto.ShortenRequest;
import com.shortlink.core.model.dto.ShortenResponse;
import com.shortlink.core.service.ShortLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open API endpoints — authenticated via HMAC-SHA256 + rate limited.
 * Path prefix: /openapi/**
 */
@Slf4j
@RestController
@RequestMapping("/openapi/v1")
@RequiredArgsConstructor
public class OpenApiController {

    private final ShortLinkService shortLinkService;

    @PostMapping("/shorten")
    public Result<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        log.info("OpenAPI shorten request: url={}", request.getOriginalUrl());
        ShortenResponse response = shortLinkService.shorten(request);
        return Result.success(response);
    }
}