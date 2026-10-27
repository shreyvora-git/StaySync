package com.staysync.dao;

import com.staysync.db.DataSourceProvider;
import com.staysync.error.AppException;
import com.staysync.model.CategoryCount;
import com.staysync.model.DailyTrend;
import com.staysync.model.KpiSummary;
import com.staysync.model.ResolutionStats;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for aggregate analytics and dashboard reporting.
 * Exclusively executes schema-backed aggregate queries (COUNT, SUM, AVG, GROUP BY).
 * Respects confidentiality by querying v_general_complaints for general reporting.
 */
public class AnalyticsDao {

    private final DataSource dataSource;

    public AnalyticsDao(DataSource dataSource) {
        this.dataSource = dataSource != null ? dataSource : DataSourceProvider.getDataSource();
    }

    public AnalyticsDao() {
        this(DataSourceProvider.getDataSource());
    }

    /**
     * Calculates total, pending, in-progress, and resolved complaint counts.
     * Maps to Query Q3 in database/04_queries.sql.
     *
     * @param includeConfidential true for authorized admin view; false for standard dashboard
     */
    public KpiSummary getKpiSummary(boolean includeConfidential) {
        try (Connection conn = dataSource.getConnection()) {
            return getKpiSummary(conn, includeConfidential);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public KpiSummary getKpiSummary(Connection conn, boolean includeConfidential) throws SQLException {
        String source = includeConfidential ? "complaints" : "v_general_complaints";
        String sql = "SELECT COUNT(*) AS total, "
                   + "COALESCE(SUM(status = 'Pending'), 0)     AS pending, "
                   + "COALESCE(SUM(status = 'In Progress'), 0) AS in_progress, "
                   + "COALESCE(SUM(status = 'Resolved'), 0)    AS resolved "
                   + "FROM " + source;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new KpiSummary(
                    rs.getLong("total"),
                    rs.getLong("pending"),
                    rs.getLong("in_progress"),
                    rs.getLong("resolved")
                );
            }
            return new KpiSummary(0, 0, 0, 0);
        }
    }

    /**
     * Aggregates complaints by category for chart rendering.
     * Maps to Query Q4 in database/04_queries.sql.
     */
    public List<CategoryCount> getCategoryDistribution(boolean includeConfidential) {
        try (Connection conn = dataSource.getConnection()) {
            return getCategoryDistribution(conn, includeConfidential);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public List<CategoryCount> getCategoryDistribution(Connection conn, boolean includeConfidential) throws SQLException {
        String source = includeConfidential ? "complaints" : "v_general_complaints";
        String sql = "SELECT cat.category_id, cat.category_name, COUNT(*) AS total "
                   + "FROM " + source + " c "
                   + "JOIN categories cat ON cat.category_id = c.category_id "
                   + "GROUP BY cat.category_id, cat.category_name "
                   + "ORDER BY total DESC";

        List<CategoryCount> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new CategoryCount(
                    rs.getLong("category_id"),
                    rs.getString("category_name"),
                    rs.getLong("total")
                ));
            }
        }
        return list;
    }

    /**
     * Calculates complaints filed per day over the requested day window.
     * Maps to Query Q5 in database/04_queries.sql.
     */
    public List<DailyTrend> getDailyTrend(int days, boolean includeConfidential) {
        try (Connection conn = dataSource.getConnection()) {
            return getDailyTrend(conn, days, includeConfidential);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public List<DailyTrend> getDailyTrend(Connection conn, int days, boolean includeConfidential) throws SQLException {
        String source = includeConfidential ? "complaints" : "v_general_complaints";
        int safeDays = Math.max(1, Math.min(days, 365));
        String sql = "SELECT DATE(created_at) AS day, COUNT(*) AS created "
                   + "FROM " + source + " "
                   + "WHERE created_at >= CURDATE() - INTERVAL ? DAY "
                   + "GROUP BY DATE(created_at) "
                   + "ORDER BY day ASC";

        List<DailyTrend> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, safeDays - 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date date = rs.getDate("day");
                    LocalDate localDate = date != null ? date.toLocalDate() : null;
                    list.add(new DailyTrend(localDate, rs.getLong("created")));
                }
            }
        }
        return list;
    }

    /**
     * Calculates average resolution duration in hours by priority level.
     * Maps to Query Q6 in database/04_queries.sql.
     */
    public List<ResolutionStats> getResolutionPerformance(boolean includeConfidential) {
        try (Connection conn = dataSource.getConnection()) {
            return getResolutionPerformance(conn, includeConfidential);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public List<ResolutionStats> getResolutionPerformance(Connection conn, boolean includeConfidential) throws SQLException {
        String source = includeConfidential ? "complaints" : "v_general_complaints";
        String sql = "SELECT COALESCE(priority, 'All Priorities') AS priority_level, "
                   + "ROUND(AVG(TIMESTAMPDIFF(MINUTE, created_at, resolved_at)) / 60.0, 1) AS avg_hours, "
                   + "COUNT(*) AS resolved_count "
                   + "FROM " + source + " "
                   + "WHERE status = 'Resolved' AND resolved_at IS NOT NULL "
                   + "GROUP BY priority WITH ROLLUP";

        List<ResolutionStats> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new ResolutionStats(
                    rs.getString("priority_level"),
                    rs.getDouble("avg_hours"),
                    rs.getLong("resolved_count")
                ));
            }
        }
        return list;
    }
}
