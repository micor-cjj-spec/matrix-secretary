package com.kailei.demo.model;

import java.util.List;

public record PageResult<T>(
        List<T> records,
        long total,
        long page,
        long size,
        long pages
) {
    private static final long DEFAULT_PAGE = 1;
    private static final long DEFAULT_SIZE = 20;
    private static final long MAX_SIZE = 100;

    public PageResult {
        records = records == null ? List.of() : List.copyOf(records);
        page = normalizePage(page);
        size = normalizeSize(size);
        pages = pages < 0 ? 0 : pages;
    }

    public static long normalizePage(Long page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    public static long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public static long maxSize() {
        return MAX_SIZE;
    }
}
