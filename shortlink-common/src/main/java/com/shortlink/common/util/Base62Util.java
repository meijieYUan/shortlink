package com.shortlink.common.util;

import com.shortlink.common.constant.Constants;

/**
 * Base62 codec: bidirectional conversion between numeric ID and short code string.
 * Uses the 62-character set [0-9A-Za-z].
 */
public final class Base62Util {

    private Base62Util() {}

    private static final int BASE = 62;

    /**
     * Encode a long ID into a Base62 short code.
     * Pads the result to Constants.SHORT_CODE_LENGTH characters.
     */
    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be non-negative");
        }
        if (id == 0) {
            return String.valueOf(Constants.BASE62_CHARS[0])
                       .repeat(Constants.SHORT_CODE_LENGTH);
        }
        StringBuilder sb = new StringBuilder();
        long remaining = id;
        while (remaining > 0) {
            int idx = (int) (remaining % BASE);
            sb.append(Constants.BASE62_CHARS[idx]);
            remaining /= BASE;
        }
        // Pad to fixed length
        while (sb.length() < Constants.SHORT_CODE_LENGTH) {
            sb.append(Constants.BASE62_CHARS[0]);
        }
        return sb.reverse().toString();
    }

    /**
     * Decode a Base62 short code back to a long ID.
     */
    public static long decode(String shortCode) {
        if (shortCode == null || shortCode.isEmpty()) {
            throw new IllegalArgumentException("shortCode must not be empty");
        }
        long result = 0;
        for (int i = 0; i < shortCode.length(); i++) {
            char c = shortCode.charAt(i);
            int value = charToValue(c);
            result = result * BASE + value;
        }
        return result;
    }

    private static int charToValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 36;
        }
        throw new IllegalArgumentException("Invalid Base62 character: " + c);
    }
}