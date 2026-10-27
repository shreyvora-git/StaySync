package com.staysync.db;

import com.staysync.error.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Executes database operations with explicit JDBC transaction boundaries.
 * Handles setAutoCommit(false), commit(), rollback(), and connection cleanup.
 */
public final class TransactionRunner {
    private static final Logger log = LoggerFactory.getLogger(TransactionRunner.class);

    private final DataSource dataSource;

    public TransactionRunner(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource must not be null");
        }
        this.dataSource = dataSource;
    }

    public TransactionRunner() {
        this(DataSourceProvider.getDataSource());
    }

    /**
     * Executes work inside a transactional boundary, returning a value.
     * Commits on success, rolls back on any exception, and wraps checked exceptions in {@link AppException}.
     *
     * @param work the transactional logic accepting a {@link Connection}
     * @param <T>  the return type
     * @return the result of work.apply(conn)
     * @throws AppException if execution or rollback fails
     */
    public <T> T call(SqlFunction<Connection, T> work) {
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = work.apply(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try {
                    conn.rollback();
                    log.debug("Transaction rolled back successfully due to error: {}", e.getMessage());
                } catch (SQLException rollbackEx) {
                    log.error("Failed to rollback transaction", rollbackEx);
                }
                throw AppException.wrap(e);
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException resetEx) {
                    log.warn("Failed to restore connection autoCommit status", resetEx);
                }
            }
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    /**
     * Executes void work inside a transactional boundary.
     * Commits on success, rolls back on any exception, and wraps checked exceptions in {@link AppException}.
     *
     * @param work the transactional logic accepting a {@link Connection}
     * @throws AppException if execution or rollback fails
     */
    public void run(SqlConsumer<Connection> work) {
        call(conn -> {
            work.accept(conn);
            return null;
        });
    }

    /**
     * Executes read-only work with auto-commit enabled on a loaned connection from the pool.
     *
     * @param work the read-only logic accepting a {@link Connection}
     * @param <T>  the return type
     * @return the query result
     * @throws AppException if execution fails
     */
    public <T> T withConnection(SqlFunction<Connection, T> work) {
        try (Connection conn = dataSource.getConnection()) {
            return work.apply(conn);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    /**
     * Executes void read-only work with auto-commit enabled on a loaned connection from the pool.
     *
     * @param work the logic accepting a {@link Connection}
     * @throws AppException if execution fails
     */
    public void withConnection(SqlConsumer<Connection> work) {
        withConnection(conn -> {
            work.accept(conn);
            return null;
        });
    }
}
