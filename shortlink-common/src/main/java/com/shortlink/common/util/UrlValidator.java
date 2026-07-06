package com.shortlink.common.util;

import java.net.URI;

/**
 * URL validation utility.
 */
public final class UrlValidator {

    private UrlValidator() {}

    private static final int MAX_URL_LENGTH = 2048;

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
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Normalize a URL for idempotency checks:
     * - Lowercase scheme and host
     * - Remove default ports
     * - Remove trailing slash for root paths
     * - Remove fragment
     */
    public static String normalize(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : null;
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : null;
            int port = uri.getPort();
            // Remove default ports
            if (port == 80 && "http".equals(scheme)) port = -1;
            if (port == 443 && "https".equals(scheme)) port = -1;
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                // Remove trailing slash except for root
                path = path.replaceAll("/$", "");
            } else {
                path = "/";
            }
            String query = uri.getQuery();
            URI normalized = new URI(scheme, uri.getUserInfo(), host, port,
                                     path, query, null);
            return normalized.toASCIIString();
        } catch (Exception e) {
            return url.trim();
        }
    }
}