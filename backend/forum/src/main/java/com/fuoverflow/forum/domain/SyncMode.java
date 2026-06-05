package com.fuoverflow.forum.domain;

import java.util.Locale;

/**
 * Whether the crawl uses anonymous public access or an authenticated session cookie.
 */
public enum SyncMode {
    PUBLIC,
    AUTHENTICATED;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SyncMode from(String value) {
        if (value == null || value.isBlank()) {
            return PUBLIC;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "authenticated", "auth", "private" -> AUTHENTICATED;
            default -> PUBLIC;
        };
    }
}
