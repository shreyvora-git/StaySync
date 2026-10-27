package com.staysync.dao;

import com.staysync.db.DataSourceProvider;
import com.staysync.error.AppException;
import com.staysync.model.AccountStatus;
import com.staysync.model.Role;
import com.staysync.model.User;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Access Object for User records.
 * Uses parameterized queries exclusively and explicit column projections.
 */
public class UserDao {

    private static final Logger log = LoggerFactory.getLogger(UserDao.class);

    private static final String SELECT_COLUMNS =
        "user_id, full_name, email, password_hash, role, account_status, can_view_confidential, created_at";

    private final DataSource dataSource;

    public UserDao(DataSource dataSource) {
        this.dataSource = dataSource != null ? dataSource : DataSourceProvider.getDataSource();
    }

    public UserDao() {
        this(DataSourceProvider.getDataSource());
    }

    /**
     * Finds a user by their primary key (user_id).
     */
    public Optional<User> findById(long userId) {
        try (Connection conn = dataSource.getConnection()) {
            return findById(conn, userId);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public Optional<User> findById(Connection conn, long userId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds a user by their unique email address (case-insensitive lookup).
     */
    public Optional<User> findByEmail(String email) {
        try (Connection conn = dataSource.getConnection()) {
            return findByEmail(conn, email);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public Optional<User> findByEmail(Connection conn, String email) throws SQLException {
        if (email == null) return Optional.empty();
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE LOWER(email) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Retrieves all admin accounts (for task assignment and triage selection).
     */
    public List<User> findAllAdmins() {
        try (Connection conn = dataSource.getConnection()) {
            return findAllAdmins(conn);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public List<User> findAllAdmins(Connection conn) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE role = 'Admin' ORDER BY full_name ASC";
        List<User> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Inserts a new user record within an active transaction.
     * Returns the generated user_id.
     */
    public long insert(Connection conn, User user) throws SQLException {
        String sql = """
            INSERT INTO users (full_name, email, password_hash, role, account_status, can_view_confidential, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.fullName());
            ps.setString(2, user.email());
            ps.setString(3, user.passwordHash());
            ps.setString(4, user.role().dbValue());
            ps.setString(5, user.accountStatus().dbValue());
            ps.setBoolean(6, user.canViewConfidential());
            JdbcHelper.setInstant(ps, 7, user.createdAt() != null ? user.createdAt() : Instant.now());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Inserting user failed, no ID obtained.");
            }
        }
    }

    /**
     * Idempotently provisions or updates the default demo accounts so that one-click
     * demo logins work out-of-the-box in development and testing environments.
     */
    public void ensureDemoAccounts() {
        String hash = "$2a$10$/2vnaOKh8ylIjaRKnH.nr.imwEDqxVO.oYfKYvWMWPZeIg4E55M/y";
        String sql = """
            INSERT IGNORE INTO users (full_name, email, password_hash, role, account_status, can_view_confidential, created_at)
            VALUES (?, ?, ?, ?, 'Active', ?, NOW())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 1. Shrey Vora (Student)
            addDemoUserBatch(ps, "Shrey Vora", "shrey@student.staysync", hash, "Student", false);

            // 2. Aadi Sheth (Student)
            addDemoUserBatch(ps, "Aadi Sheth", "aadi@student.staysync", hash, "Student", false);

            // 3. Bhavesh Patel (Warden / Admin with confidential clearance)
            addDemoUserBatch(ps, "Bhavesh Patel", "bhavesh@staysync.internal", hash, "Admin", true);

            // 4. Shubhangi Keni (Staff / Admin with general operations)
            addDemoUserBatch(ps, "Shubhangi Keni", "shubhangi@staysync.internal", hash, "Admin", false);

            // 5. Additional demo accounts
            addDemoUserBatch(ps, "Suresh Patel", "maint@staysync.internal", hash, "Admin", false);
            addDemoUserBatch(ps, "Rohan Iyer", "rohan@student.staysync", hash, "Student", false);
            addDemoUserBatch(ps, "Ananya Roy", "ananya@student.staysync", hash, "Student", false);
            addDemoUserBatch(ps, "Kabir Nair", "kabir@student.staysync", hash, "Student", false);
            addDemoUserBatch(ps, "Neha Kapoor", "neha@student.staysync", hash, "Student", false);

            // 6. Classic Seed Accounts for backward test compatibility
            addDemoUserBatch(ps, "Bhavesh Patel", "warden@staysync.internal", hash, "Admin", true);
            addDemoUserBatch(ps, "Shubhangi Keni", "mess@staysync.internal", hash, "Admin", false);
            addDemoUserBatch(ps, "Shrey Vora", "aarav@student.staysync", hash, "Student", false);

            ps.executeBatch();
            log.info("StaySync demo accounts provisioned and synchronized.");
        } catch (SQLException e) {
            log.warn("Notice: demo accounts sync deferred: {}", e.getMessage());
        }
    }

    private void addDemoUserBatch(PreparedStatement ps, String name, String email, String hash, String role, boolean canConf) throws SQLException {
        ps.setString(1, name);
        ps.setString(2, email);
        ps.setString(3, hash);
        ps.setString(4, role);
        ps.setBoolean(5, canConf);
        ps.addBatch();
    }

    private User mapRow(ResultSet rs) throws SQLException {
        long userId = rs.getLong("user_id");
        String fullName = rs.getString("full_name");
        String email = rs.getString("email");
        String passwordHash = rs.getString("password_hash");
        Role role = Role.fromDb(rs.getString("role"));
        AccountStatus accountStatus = AccountStatus.fromDb(rs.getString("account_status"));
        boolean canViewConfidential = rs.getBoolean("can_view_confidential");
        Instant createdAt = JdbcHelper.getInstant(rs, "created_at");

        return new User(userId, fullName, email, passwordHash, role, accountStatus, canViewConfidential, createdAt);
    }
}
