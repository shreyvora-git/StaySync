package com.staysync.db;

import java.sql.SQLException;

/**
 * Functional interface for database operations that accept an argument
 * (such as a {@link java.sql.Connection}) without returning a value, throwing {@link SQLException}.
 *
 * @param <T> input argument type
 */
@FunctionalInterface
public interface SqlConsumer<T> {
    void accept(T t) throws SQLException;
}
