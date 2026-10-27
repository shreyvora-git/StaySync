package com.staysync.db;

import java.sql.SQLException;

/**
 * Functional interface for database operations that accept an argument
 * (such as a {@link java.sql.Connection}) and return a result, throwing {@link SQLException}.
 *
 * @param <T> input argument type
 * @param <R> return value type
 */
@FunctionalInterface
public interface SqlFunction<T, R> {
    R apply(T t) throws SQLException;
}
