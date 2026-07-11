package com.shortlink.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Reserved short codes that cannot be used as custom codes.
 * Prevents hijacking of system routes and common endpoints.
 */
@Component
@ConfigurationProperties(prefix = "shortlink.blacklist")
public class ShortCodeBlacklist {

    /** Minimum length for custom short codes (prevents single-char short code squatting) */
    private int minCustomLength = 4;

    /** Reserved keywords that block custom short code registration */
    private Set<String> reserved = Set.of(
        // System routes
        "admin", "api", "openapi", "actuator", "health", "metrics",
        "prometheus", "swagger", "doc", "docs", "v1", "v2", "v3",
        "login", "logout", "signin", "signup", "register", "auth",
        "oauth", "callback", "redirect", "shorten",
        // Infrastructure
        "static", "assets", "css", "js", "img", "images", "favicon",
        "robots", "sitemap", "rss", "feed",
        // Common web paths
        "home", "about", "contact", "help", "terms", "privacy",
        "dashboard", "settings", "profile", "account", "billing",
        "status", "error", "404", "500", "index", "main",
        // Business-sensitive
        "shortlink", "short", "link", "url",
        "root", "www", "mail", "email", "support", "info"
    );

    public int getMinCustomLength() { return minCustomLength; }
    public void setMinCustomLength(int minCustomLength) { this.minCustomLength = minCustomLength; }
    public Set<String> getReserved() { return reserved; }
    public void setReserved(Set<String> reserved) { this.reserved = reserved; }

    public boolean isBlacklisted(String code) {
        if (code == null) return false;
        String lower = code.toLowerCase();
        return reserved.contains(lower);
    }
}