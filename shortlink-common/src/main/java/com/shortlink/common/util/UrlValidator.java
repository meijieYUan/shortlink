package com.shortlink.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * URL validation and normalization utility.
 *
 * Normalization follows RFC 3986 semantics for idempotent deduplication:
 * - Lowercase scheme and host (case-insensitive per RFC)
 * - Remove default ports (80 for http, 443 for https)
 * - Resolve "." and ".." path segments
 * - Collapse duplicate slashes
 * - Sort query parameters alphabetically by key
 * - Strip fragment (client-side only, irrelevant for server routing)
 * - Empty path normalizes to "/"
 */
public final class UrlValidator {

    private UrlValidator() {}

    private static final int MAX_URL_LENGTH = 2048;

    // ---- Validation ----

    /**
     * Check whether a URL is structurally valid and uses http/https scheme.
     */
    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (url.length() > MAX_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ---- Normalization ----

    /**
     * Normalize a URL so that semantically-equivalent URLs produce the same string.
     * Used for idempotency checks in short link creation.
     *
     * If the URL cannot be parsed, returns the trimmed original (isValid will reject it).
     */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        String trimmed = url.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return trimmed;
        }

        try {
            // 1. Lowercase scheme (RFC 3986: scheme is case-insensitive)
            String scheme = uri.getScheme() != null
                ? uri.getScheme().toLowerCase(Locale.ENGLISH) : null;

            // 2. Lowercase host (RFC 3986: host is case-insensitive)
            String host = uri.getHost() != null
                ? uri.getHost().toLowerCase(Locale.ENGLISH) : null;

            // 3. Remove default ports
            int port = uri.getPort();
            if (port == 80 && "http".equals(scheme)) port = -1;
            if (port == 443 && "https".equals(scheme)) port = -1;

            // 4. Normalize path
            String path = normalizePath(uri.getRawPath());

            // 5. Normalize query parameters (sort by key)
            String query = normalizeQueryParams(uri.getRawQuery());

            // 6. Rebuild URI without fragment
            URI normalized = new URI(scheme, uri.getUserInfo(), host, port, path, query, null);
            return normalized.toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return trimmed;
        }
    }

    // ---- private helpers ----

    /**
     * Normalize the path component:
     * - Empty/null -> "/"
     * - Collapse "//" -> "/"
     * - Resolve "." and ".." segments
     */
    static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }

        // Collapse multiple consecutive slashes
        String path = rawPath.replaceAll("/{2,}", "/");

        // Resolve dot segments using URI's built-in normalization.
        // Create a dummy base URI with the path, then normalize.
        try {
            URI tmp = new URI("http", "x", path, null);
            String normalized = tmp.normalize().getRawPath();
            if (normalized == null || normalized.isEmpty()) {
                return "/";
            }
            return normalized;
        } catch (URISyntaxException e) {
            return path;
        }
    }

    /**
     * Sort query parameters alphabetically by key for deterministic comparison.
     * Values are preserved as-is (including percent-encoding).
     *
     * Example: "b=2&a=1" -> "a=1&b=2"
     */
    static String normalizeQueryParams(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return rawQuery;
        }

        String[] params = rawQuery.split("&");
        if (params.length <= 1) {
            return rawQuery;
        }

        // Sort by key (text before '='), stable sort preserves value order for same key
        Arrays.sort(params, Comparator.comparing(p -> {
            int eq = p.indexOf('=');
            return eq >= 0 ? p.substring(0, eq) : p;
        }));

        return String.join("&", params);
    }
}