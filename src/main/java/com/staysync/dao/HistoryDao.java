package com.staysync.dao;

import com.staysync.db.DataSourceProvider;
import com.staysync.error.AppException;
import com.staysync.model.HistoryEntry;
import com.staysync.model.Priority;
import com.staysync.model.Role;
import com.staysync.model.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Complaint Audit History (complaint_history).
 * Records immutable status/priority transitions and audit trail notes.
 */
public class HistoryDao {

    private static final String SELECT_BASE = """
        SELECT h.history_id, h.complaint_id, h.changed_by,
               h.old_status, h.new_status, h.old_priority, h.new_priority,
               h.change_note, h.changed_at,
               u.full_name AS actor_name,
               u.role AS actor_role
        FROM complaint_history h
        JOIN users u ON u.user_id = h.changed_by
        """;

    private final DataSource dataSource;

    public HistoryDao(DataSource dataSource) {
        this.dataSource = dataSource != null ? dataSource : DataSourceProvider.getDataSource();
    }

    public HistoryDao() {
        this(DataSourceProvider.getDataSource());
    }

    /**
     * Retrieves the complete audit history timeline for a complaint in chronological order.
     */
    public List<HistoryEntry> findByComplaintId(long complaintId) {
        try (Connection conn = dataSource.getConnection()) {
            return findByComplaintId(conn, complaintId);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public List<HistoryEntry> findByComplaintId(Connection conn, long complaintId) throws SQLException {
        String sql = SELECT_BASE + " WHERE h.complaint_id = ? ORDER BY h.changed_at ASC, h.history_id ASC";
        List<HistoryEntry> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves recent audit history entries across complaints, respecting confidentiality clearance.
     */
    public List<HistoryEntry> findRecentAuditLogs(boolean includeConfidential, int limit) {
        String sql = """
            SELECT h.history_id, h.complaint_id, h.changed_by,
                   h.old_status, h.new_status, h.old_priority, h.new_priority,
                   h.change_note, h.changed_at,
                   u.full_name AS actor_name,
                   u.role AS actor_role
            FROM complaint_history h
            JOIN users u ON u.user_id = h.changed_by
            JOIN complaints c ON c.complaint_id = h.complaint_id
            WHERE (? = TRUE OR c.is_confidential = FALSE)
            ORDER BY h.changed_at DESC, h.history_id DESC
            LIMIT ?
            """;
        List<HistoryEntry> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, includeConfidential);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    /**
     * Appends a new audit history row within a transaction.
     */
    public long insert(Connection conn, HistoryEntry entry) throws SQLException {
        String sql = """
            INSERT INTO complaint_history (
                complaint_id, changed_by, old_status, new_status,
                old_priority, new_priority, change_note, changed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, entry.complaintId());
            ps.setLong(2, entry.changedBy());
            ps.setString(3, entry.oldStatus() != null ? entry.oldStatus().dbValue() : null);
            ps.setString(4, entry.newStatus() != null ? entry.newStatus().dbValue() : null);
            ps.setString(5, entry.oldPriority() != null ? entry.oldPriority().dbValue() : null);
            ps.setString(6, entry.newPriority() != null ? entry.newPriority().dbValue() : null);
            ps.setString(7, entry.changeNote());

            Instant changed = entry.changedAt() != null ? entry.changedAt() : Instant.now();
            JdbcHelper.setInstant(ps, 8, changed);

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Inserting history entry failed, no ID obtained.");
            }
        }
    }

    private HistoryEntry mapRow(ResultSet rs) throws SQLException {
        long historyId = rs.getLong("history_id");
        long complaintId = rs.getLong("complaint_id");
        long changedBy = rs.getLong("changed_by");
        Status oldStatus = Status.fromDb(rs.getString("old_status"));
        Status newStatus = Status.fromDb(rs.getString("new_status"));
        Priority oldPriority = Priority.fromDb(rs.getString("old_priority"));
        Priority newPriority = Priority.fromDb(rs.getString("new_priority"));
        String changeNote = rs.getString("change_note");
        Instant changedAt = JdbcHelper.getInstant(rs, "changed_at");
        String actorName = rs.getString("actor_name");
        Role actorRole = Role.fromDb(rs.getString("actor_role"));

        return new HistoryEntry(
            historyId, complaintId, changedBy, oldStatus, newStatus,
            oldPriority, newPriority, changeNote, changedAt, actorName, actorRole
        );
    }
}
