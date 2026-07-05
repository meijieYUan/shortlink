package com.shortlink.common.constant;

public final class Constants {
    private Constants() {}

    /** ??????62??? */
    public static final char[] BASE62_CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    /** ???? */
    public static final int SHORT_CODE_LENGTH = 7;

    /** ???????? */
    public static final int DEFAULT_EXPIRE_DAYS = 90;

    /** ?? key ?? */
    public static final String CACHE_KEY_PREFIX = "shortlink:";

    /** ??????? */
    public static final String BLOOM_FILTER_NAME = "shortlink:bloom";

    /** ????????? */
    public static final long BLOOM_EXPECTED_INSERTIONS = 100_000_000L;

    /** ???????? */
    public static final double BLOOM_FPP = 0.001;
}
