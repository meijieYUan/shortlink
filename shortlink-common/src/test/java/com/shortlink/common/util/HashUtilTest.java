package com.shortlink.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    @Test
    @DisplayName("should produce consistent MD5 hash for same input")
    void consistent() {
        String h1 = HashUtil.md5Hex("https://example.com/same");
        String h2 = HashUtil.md5Hex("https://example.com/same");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("should produce different hashes for different inputs")
    void different() {
        String h1 = HashUtil.md5Hex("https://example.com/a");
        String h2 = HashUtil.md5Hex("https://example.com/b");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("should always return 32 hex chars (128-bit MD5)")
    void fixedLength32() {
        String hash = HashUtil.md5Hex("https://some-very-long-url.com/with/many/segments");
        assertThat(hash).hasSize(32);
        assertThat(hash).matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("should handle empty string")
    void emptyInput() {
        String hash = HashUtil.md5Hex("");
        assertThat(hash).hasSize(32);
    }

    @Test
    @DisplayName("should handle Chinese URL")
    void chineseUrl() {
        String hash = HashUtil.md5Hex("https://example.com/中文路径");
        assertThat(hash).hasSize(32);
    }

    @Test
    @DisplayName("MD5 produces known value")
    void knownValue() {
        // md5("test") = 098f6bcd4621d373cade4e832627b4f6
        String hash = HashUtil.md5Hex("test");
        assertThat(hash).isEqualTo("098f6bcd4621d373cade4e832627b4f6");
    }
}