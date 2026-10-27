package com.staysync.dao;

import com.staysync.db.DataSourceProvider;
import com.staysync.error.AppException;
import com.staysync.model.Notification;

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
 * Data Access Object for in-app alert notifications (notifications table).
 * Uses parameterized queries and guarantees tenant/user isolation.
 */
public class NotificationDao {

    private static final String SELECT_COLUMNS =
        "notification_id, user_id, complaint_id, message, is_read, created_at";

    private final DataSource dataSource;

    public NotificationDao(DataSource dataSource) {
        this.dataSource = dataSource != null ? dataSource : DataSourceProvider.getDataSource();
    }

    public NotificationDao() {
        this(DataSourceProvider.getDataSource());
    }

    /**
     * Inserts a notification record within an active database transaction.
     */
    public long insert(Connection conn, Notification notification) throws SQLException {
        String sql = """
            INSERT INTO notifications (user_id, complaint_id, message, is_read, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, notification.userId());
            JdbcHelper.setLongOrNull(ps, 2, notification.complaintId());
            ps.setString(3, notification.message());
            ps.setBoolean(4, notification.isRead());
            JdbcHelper.setInstant(ps, 5, notification.createdAt() != null ? notification.createdAt() : Instant.now());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Inserting notification failed, no generated key obtained.");
            }
        }
    }

    /**
     * Inserts a notification using a standalone connection.
     */
    public long insert(Notification notification) {
        try (Connection conn = dataSource.getConnection()) {
            return insert(conn, notification);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    /**
     * Retrieves recent notifications for the specified user in reverse chronological order.
     */
    public List<Notification> findByUserId(long userId, int limit) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM notifications WHERE user_id = ? ORDER BY created_at DESC, notification_id DESC LIMIT ?";
        List<Notification> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
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
     * Counts unread notifications for the specified user.
     */
    public int countUnreadByUserId(long userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    /**
     * Marks a specific notification as read, ensuring the record belongs to the calling user.
     */
    public boolean markAsRead(long notificationId, long userId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE notification_id = ? AND user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, notificationId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    /**
     * Marks all unread notifications for a user as read.
     */
    public int markAllAsRead(long userId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        long notificationId = rs.getLong("notification_id");
        long userId = rs.getLong("user_id");
        Long complaintId = JdbcHelper.getLongOrNull(rs, "complaint_id");
        String message = rs.getString("message");
        boolean isRead = rs.getBoolean("is_read");
        Instant createdAt = JdbcHelper.getInstant(rs, "created_at");

        return new Notification(notificationId, userId, complaintId, message, isRead, createdAt);
    }
}
