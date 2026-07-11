package com.shortlink.domain.service;

import com.shortlink.domain.model.dto.ShortenRequest;
import com.shortlink.domain.model.dto.ShortenResponse;

public interface CoreLinkService {

    /** Create a short link — idempotent within same tenant. */
    ShortenResponse shorten(ShortenRequest request);

    /** Lookup original URL for browser redirect. Returns null if not found. */
    String getOriginalUrl(String shortCode);

    /** Check if a short code has expired. */
    boolean isExpired(String shortCode);
}