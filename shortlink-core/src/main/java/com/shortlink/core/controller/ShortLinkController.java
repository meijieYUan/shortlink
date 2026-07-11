package com.shortlink.core.controller;

import com.shortlink.analytics.disruptor.AccessEventProducer;
import com.shortlink.common.result.Result;
import com.shortlink.domain.model.dto.ShortenRequest;
import com.shortlink.domain.model.dto.ShortenResponse;
import com.shortlink.domain.service.CoreLinkService;
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

    private final CoreLinkService coreLinkService;
    private final AccessEventProducer accessEventProducer;

    @PostMapping("/api/v1/shorten")
    public Result<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        return Result.success(coreLinkService.shorten(request));
    }

    @GetMapping("/{shortCode}")
    public void redirect(@PathVariable String shortCode, HttpServletResponse response,
                         HttpServletRequest request) throws IOException {
        if (!shortCode.matches("^[0-9A-Za-z]+$")) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
        if (coreLinkService.isExpired(shortCode)) { response.sendError(HttpServletResponse.SC_GONE, "Short link has expired"); return; }
        String originalUrl = coreLinkService.getOriginalUrl(shortCode);
        if (originalUrl == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND, "Short link not found"); return; }

        try { accessEventProducer.publish(shortCode, request.getRemoteAddr(), request.getHeader("User-Agent"), request.getHeader("Referer")); }
        catch (Exception e) { log.debug("Failed to publish access event: {}", e.getMessage()); }

        response.sendRedirect(originalUrl);
    }
}