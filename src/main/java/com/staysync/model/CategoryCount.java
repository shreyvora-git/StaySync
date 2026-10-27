package com.staysync.model;

/**
 * Complaint aggregate count grouped by category.
 * Maps to Query Q4 in database/04_queries.sql.
 */
public record CategoryCount(
    long categoryId,
    String categoryName,
    long count
) {}
