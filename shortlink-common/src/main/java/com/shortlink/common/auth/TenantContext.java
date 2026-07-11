package com.shortlink.common.auth;

/**
 * Thread-local holder for the current tenant (access key).
 * Set by {@link com.shortlink.core.config.TenantInterceptor} on authenticated requests.
 * Cleared automatically after request completion.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String appKey) {
        CURRENT.set(appKey);
    }

    /** Returns the current tenant, or empty string for unauthenticated context */
    public static String get() {
        String val = CURRENT.get();
        return val != null ? val : "";
    }

    public static void clear() {
        CURRENT.remove();
    }
}