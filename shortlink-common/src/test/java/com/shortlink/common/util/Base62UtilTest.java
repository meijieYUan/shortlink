package com.shortlink.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62UtilTest {

    @Test
    @DisplayName("encode(0) should return all-zeros padded string")
    void encodeZero() {
        String result = Base62Util.encode(0L);
        assertThat(result).hasSize(7);
        assertThat(result).isEqualTo("0000000");
    }

    @Test
    @DisplayName("encode and decode should be reversible")
    void encodeDecodeRoundTrip() {
        long[] ids = {1L, 10L, 62L, 100L, 1000L, 99999L, Long.MAX_VALUE};
        for (long id : ids) {
            String code = Base62Util.encode(id);
            long decoded = Base62Util.decode(code);
            assertThat(decoded).as("Round-trip for id=%d, code=%s", id, code).isEqualTo(id);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0000000",
        "1, 0000001",
        "10, 000000A",
        "61, 000000z",
        "62, 0000010",
        "3844, 0000100",
    })
    @DisplayName("Known Base62 values")
    void knownValues(long id, String expected) {
        assertThat(Base62Util.encode(id)).isEqualTo(expected);
        assertThat(Base62Util.decode(expected)).isEqualTo(id);
    }

    @Test
    @DisplayName("decode should throw on empty string")
    void decodeEmpty() {
        assertThatThrownBy(() -> Base62Util.decode(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("decode should throw on null")
    void decodeNull() {
        assertThatThrownBy(() -> Base62Util.decode(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("encode should throw on negative id")
    void encodeNegative() {
        assertThatThrownBy(() -> Base62Util.encode(-1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("decode should throw on invalid characters")
    void decodeInvalidChars() {
        assertThatThrownBy(() -> Base62Util.decode("abc-def"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("encode output should always be 7 chars for any positive id")
    void encodeFixedLength() {
        for (long id : new long[]{0, 1, 61, 62, 100, 10000, 1000000, 100000000L}) {
            assertThat(Base62Util.encode(id)).hasSize(7);
        }
    }

    @Test
    @DisplayName("encode should only use valid Base62 characters")
    void encodeValidCharacters() {
        String code = Base62Util.encode(1234567890L);
        assertThat(code).matches("^[0-9A-Za-z]{7}$");
    }
}