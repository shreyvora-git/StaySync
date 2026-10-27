package com.staysync.model;

import java.time.Instant;

/**
 * Filter criteria, search parameters, and pagination bounds for complaint queries.
 * Defined per ARCHITECTURE.md §5.5.
 */
public record ComplaintFilter(
    String q,
    Status status,
    Long categoryId,
    Priority priority,
    Instant from,
    Instant to,
    int page,
    int size,
    String sortKey,
    boolean ascending
) {
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    public static final String DEFAULT_SORT_KEY = "createdAt";

    public ComplaintFilter {
        if (page < 0) page = 0;
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        if (sortKey == null || sortKey.isBlank()) sortKey = DEFAULT_SORT_KEY;
    }

    public static ComplaintFilter defaultFilter() {
        return new ComplaintFilter(null, null, null, null, null, null, 0, DEFAULT_PAGE_SIZE, DEFAULT_SORT_KEY, false);
    }

    public int offset() {
        return page * size;
    }

    /**
     * Parses the search query as a positive complaint ID, or returns -1 if non-numeric or absent.
     */
    public long qAsIdOrMinusOne() {
        if (q == null || q.isBlank()) {
            return -1L;
        }
        String trimmed = q.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).trim();
        }
        try {
            long id = Long.parseLong(trimmed);
            return id > 0 ? id : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
