package com.staysync.dao;

import com.staysync.db.DataSourceProvider;
import com.staysync.error.AppException;
import com.staysync.model.Complaint;
import com.staysync.model.ComplaintFilter;
import com.staysync.model.Page;
import com.staysync.model.Priority;
import com.staysync.model.Status;
import com.staysync.model.Urgency;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object for Complaint records.
 * Provides parameterized filtering, search, pagination, count calculations,
 * and optimistic concurrency updates.
 */
public class ComplaintDao {

    private static final String SELECT_BASE = """
        SELECT c.complaint_id, c.student_id, c.category_id, c.title, c.description, c.location,
               c.student_urgency, c.status, c.priority, c.is_confidential, c.assigned_admin_id,
               c.resolution_notes, c.created_at, c.updated_at, c.resolved_at,
               u.full_name AS student_name,
               cat.category_name,
               adm.full_name AS assigned_admin_name
        FROM complaints c
        JOIN categories cat ON cat.category_id = c.category_id
        JOIN users u        ON u.user_id = c.student_id
        LEFT JOIN users adm ON adm.user_id = c.assigned_admin_id
        """;

    private static final Map<String, String> SORT_WHITELIST = Map.of(
        "createdat", "c.created_at",
        "updatedat", "c.updated_at",
        "status", "c.status",
        "priority", "c.priority",
        "urgency", "c.student_urgency",
        "title", "c.title",
        "id", "c.complaint_id"
    );

    private final DataSource dataSource;

    public ComplaintDao(DataSource dataSource) {
        this.dataSource = dataSource != null ? dataSource : DataSourceProvider.getDataSource();
    }

    public ComplaintDao() {
        this(DataSourceProvider.getDataSource());
    }

    /**
     * Finds a complaint by its primary key with joined student and category information.
     */
    public Optional<Complaint> findById(long complaintId) {
        try (Connection conn = dataSource.getConnection()) {
            return findById(conn, complaintId);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public Optional<Complaint> findById(Connection conn, long complaintId) throws SQLException {
        String sql = SELECT_BASE + " WHERE c.complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds a complaint by primary key while enforcing access rules:
     * - If studentId is non-null, ensures the complaint belongs to that student.
     * - If studentId is null (admin), enforces the confidentiality predicate.
     */
    public Optional<Complaint> findByIdWithAccess(long complaintId, boolean canViewConfidential, Long studentId) {
        try (Connection conn = dataSource.getConnection()) {
            return findByIdWithAccess(conn, complaintId, canViewConfidential, studentId);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public Optional<Complaint> findByIdWithAccess(Connection conn, long complaintId, boolean canViewConfidential, Long studentId) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        sql.append(" WHERE c.complaint_id = ?");

        if (studentId != null) {
            sql.append(" AND c.student_id = ?");
        } else {
            sql.append(" AND (c.is_confidential = FALSE OR ? = TRUE)");
        }

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, complaintId);
            if (studentId != null) {
                ps.setLong(2, studentId);
            } else {
                ps.setBoolean(2, canViewConfidential);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Queries a paginated list of complaints with search, multi-field filtering, and safe ordering.
     */
    public Page<Complaint> findPage(ComplaintFilter filter, boolean canViewConfidential, Long studentId) {
        try (Connection conn = dataSource.getConnection()) {
            return findPage(conn, filter, canViewConfidential, studentId);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public Page<Complaint> findPage(Connection conn, ComplaintFilter filter, boolean canViewConfidential, Long studentId) throws SQLException {
        long total = count(conn, filter, canViewConfidential, studentId);
        if (total == 0) {
            return Page.empty(filter.page(), filter.size());
        }

        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        buildWhereClause(sql, params, filter, canViewConfidential, studentId);

        // Safe ORDER BY using whitelist or prioritized urgency
        if ("urgency".equalsIgnoreCase(filter.sortKey()) || "studenturgency".equalsIgnoreCase(filter.sortKey())) {
            sql.append(" ORDER BY CASE c.student_urgency WHEN 'High' THEN 1 WHEN 'Medium' THEN 2 WHEN 'Low' THEN 3 ELSE 4 END ASC, c.created_at ASC, c.complaint_id ASC");
        } else {
            String sortCol = resolveSortColumn(filter.sortKey());
            String dir = filter.ascending() ? "ASC" : "DESC";
            sql.append(" ORDER BY ").append(sortCol).append(" ").append(dir)
               .append(", c.complaint_id DESC");
        }

        // Safe LIMIT / OFFSET
        sql.append(" LIMIT ? OFFSET ?");
        params.add(filter.size());
        params.add(filter.offset());

        List<Complaint> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapRow(rs));
                }
            }
        }

        return Page.of(items, filter.page(), filter.size(), total);
    }

    /**
     * Counts total complaints matching the given filter and access scope.
     */
    public long count(ComplaintFilter filter, boolean canViewConfidential, Long studentId) {
        try (Connection conn = dataSource.getConnection()) {
            return count(conn, filter, canViewConfidential, studentId);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public long count(Connection conn, ComplaintFilter filter, boolean canViewConfidential, Long studentId) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM complaints c
            JOIN categories cat ON cat.category_id = c.category_id
            JOIN users u        ON u.user_id = c.student_id
            LEFT JOIN users adm ON adm.user_id = c.assigned_admin_id
            """);

        List<Object> params = new ArrayList<>();
        buildWhereClause(sql, params, filter, canViewConfidential, studentId);

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        }
    }

    /**
     * Inserts a new complaint record within a transaction.
     */
    public long insert(Connection conn, Complaint complaint) throws SQLException {
        String sql = """
            INSERT INTO complaints (
                student_id, category_id, title, description, location,
                student_urgency, status, priority, is_confidential,
                assigned_admin_id, resolution_notes, created_at, updated_at, resolved_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, complaint.studentId());
            ps.setLong(2, complaint.categoryId());
            ps.setString(3, complaint.title());
            ps.setString(4, complaint.description());
            ps.setString(5, complaint.location());
            ps.setString(6, complaint.studentUrgency() != null ? complaint.studentUrgency().dbValue() : null);
            ps.setString(7, complaint.status().dbValue());
            ps.setString(8, complaint.priority() != null ? complaint.priority().dbValue() : null);
            ps.setBoolean(9, complaint.isConfidential());
            JdbcHelper.setLongOrNull(ps, 10, complaint.assignedAdminId());
            ps.setString(11, complaint.resolutionNotes());

            Instant now = Instant.now();
            Instant created = complaint.createdAt() != null ? complaint.createdAt() : now;
            Instant updated = complaint.updatedAt() != null ? complaint.updatedAt() : now;
            JdbcHelper.setInstant(ps, 12, created);
            JdbcHelper.setInstant(ps, 13, updated);
            JdbcHelper.setInstant(ps, 14, complaint.resolvedAt());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Inserting complaint failed, no ID obtained.");
            }
        }
    }

    /**
     * Optimistically updates status, priority, and resolution notes.
     * Checks expectedUpdatedAt to guard against concurrent overwrites.
     */
    public boolean updateStatusAndPriority(
        Connection conn,
        long complaintId,
        Status newStatus,
        Priority newPriority,
        Long assignedAdminId,
        String resolutionNotes,
        Instant resolvedAt,
        Instant expectedUpdatedAt
    ) throws SQLException {
        String sql = """
            UPDATE complaints
            SET status = ?,
                priority = ?,
                assigned_admin_id = ?,
                resolution_notes = ?,
                resolved_at = ?
            WHERE complaint_id = ? AND updated_at = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus.dbValue());
            ps.setString(2, newPriority != null ? newPriority.dbValue() : null);
            JdbcHelper.setLongOrNull(ps, 3, assignedAdminId);
            ps.setString(4, resolutionNotes);
            JdbcHelper.setInstant(ps, 5, resolvedAt);
            ps.setLong(6, complaintId);
            JdbcHelper.setInstant(ps, 7, expectedUpdatedAt);

            return ps.executeUpdate() > 0;
        }
    }

    private void buildWhereClause(
        StringBuilder sql,
        List<Object> params,
        ComplaintFilter filter,
        boolean canViewConfidential,
        Long studentId
    ) {
        sql.append(" WHERE 1=1");

        // Access scope
        if (studentId != null) {
            sql.append(" AND c.student_id = ?");
            params.add(studentId);
        } else {
            sql.append(" AND (c.is_confidential = FALSE OR ? = TRUE)");
            params.add(canViewConfidential);
        }

        if (filter.status() != null) {
            sql.append(" AND c.status = ?");
            params.add(filter.status().dbValue());
        } else if ("urgency".equalsIgnoreCase(filter.sortKey()) || "studenturgency".equalsIgnoreCase(filter.sortKey())) {
            // Active triage queue: exclude Resolved unless explicitly requested via status filter
            sql.append(" AND c.status <> 'Resolved'");
        }

        if (filter.categoryId() != null) {
            sql.append(" AND c.category_id = ?");
            params.add(filter.categoryId());
        }

        if (filter.priority() != null) {
            sql.append(" AND c.priority = ?");
            params.add(filter.priority().dbValue());
        }

        if (filter.from() != null) {
            sql.append(" AND c.created_at >= ?");
            params.add(filter.from());
        }

        if (filter.to() != null) {
            sql.append(" AND c.created_at <= ?");
            params.add(filter.to());
        }

        // Keyword or ID search
        if (filter.q() != null && !filter.q().isBlank()) {
            long numericId = filter.qAsIdOrMinusOne();
            String escaped = "%" + JdbcHelper.escapeLike(filter.q().trim()) + "%";
            if (numericId > 0) {
                sql.append(" AND (c.complaint_id = ? OR c.title LIKE ? ESCAPE '\\\\')");
                params.add(numericId);
                params.add(escaped);
            } else {
                sql.append(" AND (c.title LIKE ? ESCAPE '\\\\' OR c.location LIKE ? ESCAPE '\\\\' OR c.description LIKE ? ESCAPE '\\\\')");
                params.add(escaped);
                params.add(escaped);
                params.add(escaped);
            }
        }
    }

    private void bindParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object val = params.get(i);
            int idx = i + 1;
            if (val instanceof String s) {
                ps.setString(idx, s);
            } else if (val instanceof Long l) {
                ps.setLong(idx, l);
            } else if (val instanceof Integer in) {
                ps.setInt(idx, in);
            } else if (val instanceof Boolean b) {
                ps.setBoolean(idx, b);
            } else if (val instanceof Instant inst) {
                JdbcHelper.setInstant(ps, idx, inst);
            } else if (val == null) {
                ps.setNull(idx, java.sql.Types.NULL);
            } else {
                ps.setObject(idx, val);
            }
        }
    }

    private String resolveSortColumn(String sortKey) {
        if (sortKey == null) return "c.created_at";
        String normalized = sortKey.toLowerCase().replace("_", "").replace("-", "");
        return SORT_WHITELIST.getOrDefault(normalized, "c.created_at");
    }

    private Complaint mapRow(ResultSet rs) throws SQLException {
        long complaintId = rs.getLong("complaint_id");
        long studentId = rs.getLong("student_id");
        long categoryId = rs.getLong("category_id");
        String title = rs.getString("title");
        String description = rs.getString("description");
        String location = rs.getString("location");
        Urgency studentUrgency = Urgency.fromDb(rs.getString("student_urgency"));
        Status status = Status.fromDb(rs.getString("status"));
        Priority priority = Priority.fromDb(rs.getString("priority"));
        boolean isConfidential = rs.getBoolean("is_confidential");
        Long assignedAdminId = JdbcHelper.getLongOrNull(rs, "assigned_admin_id");
        String resolutionNotes = rs.getString("resolution_notes");
        Instant createdAt = JdbcHelper.getInstant(rs, "created_at");
        Instant updatedAt = JdbcHelper.getInstant(rs, "updated_at");
        Instant resolvedAt = JdbcHelper.getInstant(rs, "resolved_at");

        String studentName = rs.getString("student_name");
        String categoryName = rs.getString("category_name");
        String assignedAdminName = rs.getString("assigned_admin_name");

        return new Complaint(
            complaintId, studentId, categoryId, title, description, location,
            studentUrgency, status, priority, isConfidential, assignedAdminId,
            resolutionNotes, createdAt, updatedAt, resolvedAt,
            studentName, categoryName, assignedAdminName
        );
    }
}
