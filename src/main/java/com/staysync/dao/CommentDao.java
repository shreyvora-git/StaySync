package com.staysync.dao;

import com.staysync.db.DataSourceProvider;
import com.staysync.error.AppException;
import com.staysync.model.Comment;
import com.staysync.model.Role;

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
 * Data Access Object for Complaint Comments and Internal Staff Notes.
 *
 * <p><strong>Access Control Boundary:</strong>
 * This DAO enforces internal note filtering via the {@code includeInternal} flag:
 * when {@code includeInternal} is false, internal staff notes are omitted at the SQL level.
 * Higher-level service layers (Stage 4) are responsible for verifying caller roles
 * (i.e. ensuring students cannot supply {@code includeInternal = true}).
 */
public class CommentDao {

    private static final String SELECT_BASE = """
        SELECT cm.comment_id, cm.complaint_id, cm.user_id, cm.comment_text,
               cm.is_internal, cm.created_at,
               u.full_name AS author_name,
               u.role AS author_role
        FROM complaint_comments cm
        JOIN users u ON u.user_id = cm.user_id
        """;

    private final DataSource dataSource;

    public CommentDao(DataSource dataSource) {
        this.dataSource = dataSource != null ? dataSource : DataSourceProvider.getDataSource();
    }

    public CommentDao() {
        this(DataSourceProvider.getDataSource());
    }

    /**
     * Retrieves all comments for a complaint in chronological order.
     *
     * @param complaintId     target complaint identifier
     * @param includeInternal true to include staff internal notes; false for student-visible comments only
     */
    public List<Comment> findByComplaintId(long complaintId, boolean includeInternal) {
        try (Connection conn = dataSource.getConnection()) {
            return findByComplaintId(conn, complaintId, includeInternal);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public List<Comment> findByComplaintId(Connection conn, long complaintId, boolean includeInternal) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        sql.append(" WHERE cm.complaint_id = ?");

        if (!includeInternal) {
            sql.append(" AND cm.is_internal = FALSE");
        }

        sql.append(" ORDER BY cm.created_at ASC, cm.comment_id ASC");

        List<Comment> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
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
     * Appends a new comment or internal note within an active transaction.
     */
    public long insert(Connection conn, Comment comment) throws SQLException {
        String sql = """
            INSERT INTO complaint_comments (complaint_id, user_id, comment_text, is_internal, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, comment.complaintId());
            ps.setLong(2, comment.userId());
            ps.setString(3, comment.commentText());
            ps.setBoolean(4, comment.isInternal());

            Instant created = comment.createdAt() != null ? comment.createdAt() : Instant.now();
            JdbcHelper.setInstant(ps, 5, created);

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Inserting comment failed, no ID obtained.");
            }
        }
    }

    private Comment mapRow(ResultSet rs) throws SQLException {
        long commentId = rs.getLong("comment_id");
        long complaintId = rs.getLong("complaint_id");
        long userId = rs.getLong("user_id");
        String commentText = rs.getString("comment_text");
        boolean isInternal = rs.getBoolean("is_internal");
        Instant createdAt = JdbcHelper.getInstant(rs, "created_at");
        String authorName = rs.getString("author_name");
        Role authorRole = Role.fromDb(rs.getString("author_role"));

        return new Comment(commentId, complaintId, userId, commentText, isInternal, createdAt, authorName, authorRole);
    }
}
