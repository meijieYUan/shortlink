package com.shortlink.common.constant;

public final class Constants {
    private Constants() {}

    /** 短码字符集（62进制） */
    public static final char[] BASE62_CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    /** 短码长度 */
    public static final int SHORT_CODE_LENGTH = 7;

    /** 默认短链过期天数 */
    public static final int DEFAULT_EXPIRE_DAYS = 90;

    /** 缓存 key 前缀 */
    public static final String CACHE_KEY_PREFIX = "shortlink:";

    /** 布隆过滤器名称 */
    public static final String BLOOM_FILTER_NAME = "shortlink:bloom";

    /** 布隆过滤器预期容量 */
    public static final long BLOOM_EXPECTED_INSERTIONS = 100_000_000L;

    /** 布隆过滤器误判率 */
    public static final double BLOOM_FPP = 0.001;
}
