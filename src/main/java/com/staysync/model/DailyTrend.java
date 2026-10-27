package com.staysync.model;

import java.time.LocalDate;

/**
 * Daily complaints count.
 * Maps to Query Q5 in database/04_queries.sql.
 */
public record DailyTrend(
    LocalDate date,
    long count
) {}
