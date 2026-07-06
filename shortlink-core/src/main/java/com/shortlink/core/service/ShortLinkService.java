package com.shortlink.core.service;

import com.shortlink.core.model.dto.ShortenRequest;
import com.shortlink.core.model.dto.ShortenResponse;

public interface ShortLinkService {

    /**
     * Create a short link from the given request.
     * Idempotent: returns the existing short code if the same URL already exists (not expired).
     */
    ShortenResponse shorten(ShortenRequest request);

    /**
     * Get original URL by short code.
     * Returns null if not found.
     */
    String getOriginalUrl(String shortCode);

    /**
     * Check if a short code exists and has expired.
     */
    boolean isExpired(String shortCode);
}