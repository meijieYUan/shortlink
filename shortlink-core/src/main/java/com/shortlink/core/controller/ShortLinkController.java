package com.shortlink.core.controller;

import com.shortlink.common.exception.BizException;
import com.shortlink.common.result.Result;
import com.shortlink.common.result.ResultCode;
import com.shortlink.core.model.dto.ShortenRequest;
import com.shortlink.core.model.dto.ShortenResponse;
import com.shortlink.core.service.ShortLinkService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    /**
     * Create a short link.
     */
    @PostMapping("/api/v1/shorten")
    public Result<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = shortLinkService.shorten(request);
        return Result.success(response);
    }

    /**
     * Redirect to the original URL.
     * GET /{shortCode} -> 302 redirect, 404 not found, 410 gone.
     */
    @GetMapping("/{shortCode}")
    public void redirect(@PathVariable String shortCode, HttpServletResponse response) throws IOException {
        // Validate shortCode format (only Base62 chars)
        if (!shortCode.matches("^[0-9A-Za-z]+$")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Check expired
        if (shortLinkService.isExpired(shortCode)) {
            log.info("Short link expired: {}", shortCode);
            response.sendError(HttpServletResponse.SC_GONE, "Short link has expired");
            return;
        }

        // Resolve original URL
        String originalUrl = shortLinkService.getOriginalUrl(shortCode);
        if (originalUrl == null) {
            log.info("Short link not found: {}", shortCode);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Short link not found");
            return;
        }

        log.info("Redirect: {} -> {}", shortCode, originalUrl);
        response.sendRedirect(originalUrl);
    }
}