package com.staysync.model;

/**
 * Summary KPI counts calculated across complaints.
 * Maps to Query Q3 in database/04_queries.sql and PRD.md §6.8.
 */
public record KpiSummary(
    long total,
    long pending,
    long inProgress,
    long resolved
) {}
