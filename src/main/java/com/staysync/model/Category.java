package com.staysync.model;

import java.util.Objects;

/**
 * Immutable domain model representing a complaint category.
 * Maps to MySQL table 'categories'.
 */
public record Category(
    long categoryId,
    String categoryName,
    String description,
    boolean isConfidential,
    boolean isActive
) {
    public Category {
        Objects.requireNonNull(categoryName, "categoryName must not be null");
    }
}
