package com.shortlink.core.controller;

import com.shortlink.analytics.disruptor.AccessEventProducer;
import com.shortlink.common.result.Result;
import com.shortlink.core.model.dto.ShortenRequest;
import com.shortlink.core.model.dto.ShortenResponse;
import com.shortlink.core.service.ShortLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;
    private final AccessEventProducer accessEventProducer;

    @PostMapping("/api/v1/shorten")
    public Result<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = shortLinkService.shorten(request);
        return Result.success(response);
    }

    @GetMapping("/{shortCode}")
    public void redirect(@PathVariable String shortCode, HttpServletResponse response,
                         HttpServletRequest request) throws IOException {
        if (!shortCode.matches("^[0-9A-Za-z]+$")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (shortLinkService.isExpired(shortCode)) {
            log.info("Short link expired: {}", shortCode);
            response.sendError(HttpServletResponse.SC_GONE, "Short link has expired");
            return;
        }

        String originalUrl = shortLinkService.getOriginalUrl(shortCode);
        if (originalUrl == null) {
            log.info("Short link not found: {}", shortCode);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Short link not found");
            return;
        }

        // Phase 5: async access event — non-blocking, does not affect redirect
        try {
            accessEventProducer.publish(
                shortCode,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("Referer")
            );
        } catch (Exception e) {
            log.debug("Failed to publish access event (non-critical): {}", e.getMessage());
        }

        log.debug("Redirect: {} -> {}", shortCode, originalUrl);
        response.sendRedirect(originalUrl);
    }
}