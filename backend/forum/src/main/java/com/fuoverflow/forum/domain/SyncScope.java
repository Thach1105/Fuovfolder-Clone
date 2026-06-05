package com.fuoverflow.forum.domain;

import java.util.Locale;

/**
 * Full crawl (re-walk everything within configured caps) vs incremental (only the
 * first listing page per forum, for cheap recurring updates).
 */
public enum SyncScope {
    FULL,
    INCREMENTAL;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SyncScope from(String value) {
        if (value == null || value.isBlank()) {
            return INCREMENTAL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "full" -> FULL;
            default -> INCREMENTAL;
        };
    }
}
