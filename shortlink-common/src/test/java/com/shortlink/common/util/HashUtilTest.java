package com.shortlink.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    @Test
    @DisplayName("should produce consistent hash for same input")
    void consistent() {
        String h1 = HashUtil.sha256Hex16("https://example.com/same");
        String h2 = HashUtil.sha256Hex16("https://example.com/same");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("should produce different hashes for different inputs")
    void different() {
        String h1 = HashUtil.sha256Hex16("https://example.com/a");
        String h2 = HashUtil.sha256Hex16("https://example.com/b");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("should always return 16 hex chars")
    void fixedLength() {
        String hash = HashUtil.sha256Hex16("https://some-very-long-url.com/with/many/segments");
        assertThat(hash).hasSize(16);
        assertThat(hash).matches("^[0-9a-f]{16}$");
    }

    @Test
    @DisplayName("should handle empty string")
    void emptyInput() {
        String hash = HashUtil.sha256Hex16("");
        assertThat(hash).hasSize(16);
    }

    @Test
    @DisplayName("should handle Chinese URL")
    void chineseUrl() {
        String hash = HashUtil.sha256Hex16("https://example.com/中文路径");
        assertThat(hash).hasSize(16);
    }
}