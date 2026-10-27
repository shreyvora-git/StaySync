package com.staysync.dao;

import com.staysync.db.DataSourceProvider;
import com.staysync.error.AppException;
import com.staysync.model.Attachment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Complaint Attachment metadata.
 * Manages database rows only; file content I/O, encryption, and disk operations
 * are reserved for Stage 4/5.
 */
public class AttachmentDao {

    private static final String SELECT_BASE = """
        SELECT a.attachment_id, a.complaint_id, a.uploaded_by,
               a.file_name, a.file_path, a.mime_type, a.uploaded_at,
               u.full_name AS uploader_name
        FROM complaint_attachments a
        JOIN users u ON u.user_id = a.uploaded_by
        """;

    private final DataSource dataSource;

    public AttachmentDao(DataSource dataSource) {
        this.dataSource = dataSource != null ? dataSource : DataSourceProvider.getDataSource();
    }

    public AttachmentDao() {
        this(DataSourceProvider.getDataSource());
    }

    /**
     * Retrieves all attachment metadata records associated with a complaint.
     */
    public List<Attachment> findByComplaintId(long complaintId) {
        try (Connection conn = dataSource.getConnection()) {
            return findByComplaintId(conn, complaintId);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public List<Attachment> findByComplaintId(Connection conn, long complaintId) throws SQLException {
        String sql = SELECT_BASE + " WHERE a.complaint_id = ? ORDER BY a.uploaded_at ASC, a.attachment_id ASC";
        List<Attachment> list = new ArrayList<>();
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
     * Finds attachment metadata by primary key.
     */
    public Optional<Attachment> findById(long attachmentId) {
        try (Connection conn = dataSource.getConnection()) {
            return findById(conn, attachmentId);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public Optional<Attachment> findById(Connection conn, long attachmentId) throws SQLException {
        String sql = SELECT_BASE + " WHERE a.attachment_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, attachmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Inserts attachment metadata within an active transaction.
     */
    public long insert(Connection conn, Attachment attachment) throws SQLException {
        String sql = """
            INSERT INTO complaint_attachments (complaint_id, uploaded_by, file_name, file_path, mime_type, uploaded_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, attachment.complaintId());
            ps.setLong(2, attachment.uploadedBy());
            ps.setString(3, attachment.fileName());
            ps.setString(4, attachment.filePath());
            ps.setString(5, attachment.mimeType());

            Instant uploaded = attachment.uploadedAt() != null ? attachment.uploadedAt() : Instant.now();
            JdbcHelper.setInstant(ps, 6, uploaded);

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Inserting attachment metadata failed, no ID obtained.");
            }
        }
    }

    private Attachment mapRow(ResultSet rs) throws SQLException {
        long attachmentId = rs.getLong("attachment_id");
        long complaintId = rs.getLong("complaint_id");
        long uploadedBy = rs.getLong("uploaded_by");
        String fileName = rs.getString("file_name");
        String filePath = rs.getString("file_path");
        String mimeType = rs.getString("mime_type");
        Instant uploadedAt = JdbcHelper.getInstant(rs, "uploaded_at");
        String uploaderName = rs.getString("uploader_name");

        return new Attachment(attachmentId, complaintId, uploadedBy, fileName, filePath, mimeType, uploadedAt, uploaderName);
    }
}
