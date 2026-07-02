package com.fuoverflow.common.web;

public final class PaginationDefaults {

    private PaginationDefaults() {}

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    public static int clampSize(int size, int maxPageSize) {
        return Math.min(Math.max(size, 1), maxPageSize);
    }

    public static int clampPage(int page) {
        return Math.max(page, 0);
    }
}
