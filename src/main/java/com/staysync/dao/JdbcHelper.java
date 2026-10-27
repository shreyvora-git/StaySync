package com.staysync.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

/**
 * Shared JDBC helper utility for safe NULL handling, java.time.Instant conversions,
 * and SQL wildcard sanitization.
 */
public final class JdbcHelper {

    private JdbcHelper() {
        // Utility class
    }

    public static Instant getInstant(ResultSet rs, String columnLabel) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnLabel);
        return ts != null ? ts.toInstant() : null;
    }

    public static void setInstant(PreparedStatement ps, int paramIndex, Instant instant) throws SQLException {
        if (instant != null) {
            ps.setTimestamp(paramIndex, Timestamp.from(instant));
        } else {
            ps.setNull(paramIndex, Types.TIMESTAMP);
        }
    }

    public static Long getLongOrNull(ResultSet rs, String columnLabel) throws SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }

    public static void setLongOrNull(PreparedStatement ps, int paramIndex, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(paramIndex, value);
        } else {
            ps.setNull(paramIndex, Types.BIGINT);
        }
    }

    public static Integer getIntOrNull(ResultSet rs, String columnLabel) throws SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? null : value;
    }

    public static void setIntOrNull(PreparedStatement ps, int paramIndex, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(paramIndex, value);
        } else {
            ps.setNull(paramIndex, Types.INTEGER);
        }
    }

    /**
     * Escapes MySQL LIKE wildcards ('%', '_', and '\') to prevent pattern injection.
     */
    public static String escapeLike(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }
}
