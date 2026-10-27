package com.staysync.model;

import java.util.Collections;
import java.util.List;

/**
 * Immutable pagination envelope holding the current page slice of records and total count.
 *
 * @param <T> element type
 */
public record Page<T>(
    List<T> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {
    public Page {
        items = items != null ? List.copyOf(items) : Collections.emptyList();
    }

    public static <T> Page<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalItems / size) : 0;
        return new Page<>(items, page, size, totalItems, totalPages);
    }

    public static <T> Page<T> empty(int page, int size) {
        return new Page<>(Collections.emptyList(), page, size, 0L, 0);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages;
    }
}
