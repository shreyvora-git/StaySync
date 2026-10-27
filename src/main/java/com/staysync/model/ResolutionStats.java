package com.staysync.model;

/**
 * Resolution performance statistics by priority level.
 * Maps to Query Q6 in database/04_queries.sql.
 */
public record ResolutionStats(
    String priorityLevel,
    double avgHours,
    long resolvedCount
) {}
