package com.staysync.db;

import com.staysync.config.AppConfig;
import com.staysync.error.AppException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Manages the HikariCP connection pool for the StaySync desktop application.
 * Configures pooling parameters based on {@link AppConfig} without exposing
 * credentials in logs or UI layers.
 */
public final class DataSourceProvider {
    private static final Logger log = LoggerFactory.getLogger(DataSourceProvider.class);

    private static volatile HikariDataSource dataSource;
    private static final Object lock = new Object();

    private DataSourceProvider() {
        // Utility class
    }

    /**
     * Retrieves or initializes the shared HikariCP {@link DataSource}.
     *
     * @return the active HikariDataSource instance
     */
    public static DataSource getDataSource() {
        HikariDataSource ds = dataSource;
        if (ds == null || ds.isClosed()) {
            synchronized (lock) {
                ds = dataSource;
                if (ds == null || ds.isClosed()) {
                    dataSource = initDataSource();
                    ds = dataSource;
                }
            }
        }
        return ds;
    }

    private static HikariDataSource initDataSource() {
        AppConfig config = AppConfig.load();
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setPoolName("StaySync-HikariPool");
        hikariConfig.setJdbcUrl(config.getDbUrl());
        hikariConfig.setUsername(config.getDbUser());
        hikariConfig.setPassword(config.getDbPassword());

        int poolSize = Math.max(2, config.getDbPoolSize());
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setMinimumIdle(Math.min(2, poolSize));

        // Connection timeout: 3000ms (3 seconds) for quick failover when offline
        hikariConfig.setConnectionTimeout(3000);
        hikariConfig.setIdleTimeout(60000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setLeakDetectionThreshold(10000);

        // Performance optimizations for MySQL Connector/J
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        // Mask credentials in logs (only log URL and user)
        log.info("Initializing HikariCP connection pool [Pool: StaySync-HikariPool, URL: {}, User: {}, MaxPoolSize: {}]",
                config.getDbUrl(), config.getDbUser(), poolSize);

        try {
            return new HikariDataSource(hikariConfig);
        } catch (Exception e) {
            log.warn("Failed to initialize HikariCP connection pool: {}", e.getMessage());
            throw new AppException("Unable to initialize database connection pool", e);
        }
    }

    /**
     * Executes a fast, non-blocking health check ping (SELECT 1) against the database.
     *
     * @return true if MySQL is reachable and queries execute successfully; false otherwise
     */
    public static boolean testConnection() {
        try {
            DataSource ds = getDataSource();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1");
                 ResultSet rs = ps.executeQuery()) {
                boolean ok = rs.next() && rs.getInt(1) == 1;
                log.info("Database startup health check: OK (MySQL connected)");
                return ok;
            }
        } catch (Exception e) {
            log.warn("Database health check failed (MySQL offline or unreachable): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Shuts down the connection pool cleanly, releasing all physical connections.
     */
    public static void shutdown() {
        synchronized (lock) {
            if (dataSource != null && !dataSource.isClosed()) {
                log.info("Shutting down HikariCP connection pool...");
                dataSource.close();
                dataSource = null;
                log.info("HikariCP connection pool closed.");
            }
        }
    }

    /**
     * Checks if the data source is currently initialized and open.
     */
    public static boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
    }
}
