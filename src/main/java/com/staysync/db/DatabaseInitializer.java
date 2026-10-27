package com.staysync.db;

import com.staysync.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Automates safe, idempotent database initialization and connectivity pre-flight checks
 * for StaySync.
 *
 * <p>Key Guarantees:
 * <ul>
 *   <li>Pre-flight checks MySQL Server reachability without hanging or silent failure.</li>
 *   <li>Creates {@code staysync_db} if absent (given sufficient user privileges).</li>
 *   <li>Executes canonical schema from {@code database/01_schema.sql} on a fresh database.</li>
 *   <li>Creates views and triggers from {@code database/05_views_triggers.sql}.</li>
 *   <li>Populates default categories and demo accounts idempotently.</li>
 *   <li>Loads demo complaints ONLY on a clean install; NEVER executes destructive scripts
 *       (e.g. {@code DELETE} statements or reset scripts) on an existing database.</li>
 *   <li>Preserves all existing users, complaints, categories, and audit records on restart.</li>
 *   <li>Never exposes credentials in logs.</li>
 * </ul>
 */
public final class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    // BCrypt hash for demo accounts password: "password123"
    private static final String DEMO_PASSWORD_HASH =
            "$2a$10$/2vnaOKh8ylIjaRKnH.nr.imwEDqxVO.oYfKYvWMWPZeIg4E55M/y";

    private static final List<String> CORE_TABLES = List.of(
            "users",
            "categories",
            "complaints",
            "complaint_comments",
            "complaint_history",
            "notifications",
            "complaint_attachments"
    );

    private DatabaseInitializer() {
        // Utility class
    }

    /**
     * Represents the outcome of the initialization pre-flight check.
     */
    public static final class InitResult {
        private final boolean success;
        private final boolean freshInit;
        private final String message;
        private final Throwable cause;

        private InitResult(boolean success, boolean freshInit, String message, Throwable cause) {
            this.success = success;
            this.freshInit = freshInit;
            this.message = message;
            this.cause = cause;
        }

        public static InitResult successFresh(String message) {
            return new InitResult(true, true, message, null);
        }

        public static InitResult successExisting(String message) {
            return new InitResult(true, false, message, null);
        }

        public static InitResult failed(String message, Throwable cause) {
            return new InitResult(false, false, message, cause);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isFreshInit() {
            return freshInit;
        }

        public String getMessage() {
            return message;
        }

        public Throwable getCause() {
            return cause;
        }
    }

    /**
     * Parsed components of a JDBC connection URL.
     */
    static final class ParsedJdbcUrl {
        final String serverUrl;
        final String databaseName;
        final String fullUrl;
        final String hostPort;

        ParsedJdbcUrl(String serverUrl, String databaseName, String fullUrl, String hostPort) {
            this.serverUrl = serverUrl;
            this.databaseName = databaseName;
            this.fullUrl = fullUrl;
            this.hostPort = hostPort;
        }
    }

    /**
     * Executes the safe database initialization lifecycle.
     *
     * @return {@link InitResult} with detailed status for UI and logging
     */
    public static InitResult initialize() {
        AppConfig config = AppConfig.load();
        String rawUrl = config.getDbUrl();
        String user = config.getDbUser();
        String password = config.getDbPassword();

        ParsedJdbcUrl parsed = parseJdbcUrl(rawUrl);

        log.info("Starting StaySync database pre-flight check for server [{}] database [{}]",
                parsed.hostPort, parsed.databaseName);

        // 1. Check MySQL Server Reachability (server-level ping)
        Properties connProps = new Properties();
        connProps.put("user", user);
        connProps.put("password", password);
        connProps.put("connectTimeout", "3000"); // 3s fast failover
        connProps.put("socketTimeout", "5000");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            String err = "MySQL JDBC driver not found on classpath: " + e.getMessage();
            log.error(err);
            return InitResult.failed(err, e);
        }

        try (Connection serverConn = DriverManager.getConnection(parsed.serverUrl, connProps)) {
            log.info("MySQL Server reachable at [{}].", parsed.hostPort);

            // 2. Check if staysync_db exists; create if missing
            boolean dbExists = checkDatabaseExists(serverConn, parsed.databaseName);
            if (!dbExists) {
                log.info("Database '{}' does not exist. Creating database...", parsed.databaseName);
                createDatabase(serverConn, parsed.databaseName);
                log.info("Database '{}' created successfully.", parsed.databaseName);
            }
        } catch (SQLException e) {
            String diagnostic = diagnoseConnectionError(e, parsed.hostPort, user);
            log.warn("Database pre-flight check failed: {}", diagnostic);
            return InitResult.failed(diagnostic, e);
        }

        // 3. Connect to target database and inspect schema
        try (Connection dbConn = DriverManager.getConnection(parsed.fullUrl, connProps)) {
            Set<String> existingTables = getExistingTables(dbConn, parsed.databaseName);

            boolean isFreshDatabase = existingTables.isEmpty() ||
                    (!existingTables.contains("users") && !existingTables.contains("complaints"));

            if (isFreshDatabase) {
                log.info("Empty database detected. Performing safe first-run initialization...");
                performFreshInitialization(dbConn);
                return InitResult.successFresh("Database '" + parsed.databaseName + "' initialized with canonical schema and demo data.");
            } else {
                log.info("Existing database detected with {} tables. Verifying schema integrity...", existingTables.size());
                return performExistingDatabaseVerification(dbConn, parsed.databaseName, existingTables);
            }
        } catch (SQLException e) {
            String err = "Failed to connect to database '" + parsed.databaseName + "': " + e.getMessage();
            log.error(err, e);
            return InitResult.failed(err, e);
        } catch (Exception e) {
            String err = "Unexpected error during database initialization: " + e.getMessage();
            log.error(err, e);
            return InitResult.failed(err, e);
        }
    }

    /**
     * Parses a JDBC MySQL URL into server URL (without database) and target database name.
     */
    static ParsedJdbcUrl parseJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return new ParsedJdbcUrl("jdbc:mysql://localhost:3306/", "staysync_db",
                    "jdbc:mysql://localhost:3306/staysync_db", "localhost:3306");
        }

        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return new ParsedJdbcUrl("jdbc:mysql://localhost:3306/", "staysync_db", url, "localhost:3306");
        }

        int pathStart = url.indexOf('/', schemeEnd + 3);
        if (pathStart < 0) {
            // URL has no trailing slash (e.g. jdbc:mysql://localhost:3306)
            return new ParsedJdbcUrl(url + "/", "staysync_db", url + "/staysync_db",
                    url.substring(schemeEnd + 3));
        }

        String hostPort = url.substring(schemeEnd + 3, pathStart);
        int queryStart = url.indexOf('?', pathStart);

        String dbName;
        String queryParams = "";

        if (queryStart >= 0) {
            dbName = url.substring(pathStart + 1, queryStart);
            queryParams = url.substring(queryStart);
        } else {
            dbName = url.substring(pathStart + 1);
        }

        if (dbName.isBlank()) {
            dbName = "staysync_db";
        }

        String serverUrl = url.substring(0, pathStart + 1) + queryParams;
        return new ParsedJdbcUrl(serverUrl, dbName, url, hostPort);
    }

    private static boolean checkDatabaseExists(Connection conn, String dbName) throws SQLException {
        String sql = "SELECT 1 FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void createDatabase(Connection conn, String dbName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            try {
                // Preferred utf8mb4 with modern collation
                stmt.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            } catch (SQLException e) {
                // Fallback for older MySQL versions
                log.info("utf8mb4_0900_ai_ci not supported, falling back to standard utf8mb4 charset: {}", e.getMessage());
                stmt.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4");
            }
        }
    }

    private static Set<String> getExistingTables(Connection conn, String dbName) throws SQLException {
        Set<String> tables = new HashSet<>();
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1).toLowerCase());
                }
            }
        }
        return tables;
    }

    /**
     * Initializes a brand-new database:
     * 1. Runs canonical schema (01_schema.sql)
     * 2. Runs views and triggers (05_views_triggers.sql)
     * 3. Safely seeds categories, demo accounts, and sample complaints
     */
    private static void performFreshInitialization(Connection conn) throws SQLException, IOException {
        // Step 1: Canonical schema
        InputStream schemaStream = getScriptStream("database/01_schema.sql");
        if (schemaStream == null) {
            throw new IllegalStateException("Canonical schema script 'database/01_schema.sql' not found.");
        }
        log.info("Executing canonical schema 'database/01_schema.sql'...");
        executeSqlScript(conn, schemaStream);
        log.info("Canonical schema applied successfully.");

        // Step 2: Views & Triggers
        InputStream viewsStream = getScriptStream("database/05_views_triggers.sql");
        if (viewsStream != null) {
            log.info("Executing views and triggers 'database/05_views_triggers.sql'...");
            executeSqlScript(conn, viewsStream);
            log.info("Views and triggers applied successfully.");
        } else {
            // Programmatic fallback for required view and trigger
            ensureViewsAndTriggers(conn);
        }

        // Step 3: Seed standard categories
        seedCategories(conn);

        // Step 4: Seed demo user accounts
        seedDemoUsers(conn);

        // Step 5: Seed initial demo complaints and comments (safe INSERT IGNORE)
        seedInitialComplaints(conn);
    }

    /**
     * Verifies existing database integrity without altering user records.
     */
    private static InitResult performExistingDatabaseVerification(Connection conn, String dbName, Set<String> existingTables) throws SQLException {
        // Check for missing core tables
        List<String> missingTables = new ArrayList<>();
        for (String required : CORE_TABLES) {
            if (!existingTables.contains(required.toLowerCase())) {
                missingTables.add(required);
            }
        }

        if (!missingTables.isEmpty()) {
            String err = "Database schema mismatch: missing required table(s) " + missingTables +
                    ". Automatic migration halted to prevent data loss. Please verify your database.";
            log.error(err);
            return InitResult.failed(err, null);
        }

        // Verify and ensure view v_general_complaints exists (required by AnalyticsDao)
        boolean viewExists = checkViewExists(conn, dbName, "v_general_complaints");
        if (!viewExists) {
            log.info("View 'v_general_complaints' missing in existing database. Creating view...");
            createGeneralComplaintsView(conn);
        }

        // Ensure demo accounts exist using INSERT IGNORE (will NOT overwrite existing passwords)
        seedDemoUsers(conn);

        // Report surviving complaint count
        long count = 0;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM complaints")) {
            if (rs.next()) {
                count = rs.getLong(1);
            }
        }

        log.info("Existing database '{}' verified successfully (preserved {} complaints).", dbName, count);
        return InitResult.successExisting("Connected to existing database '" + dbName + "' (preserved " + count + " complaints).");
    }

    private static boolean checkViewExists(Connection conn, String dbName, String viewName) throws SQLException {
        String sql = "SELECT 1 FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            ps.setString(2, viewName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void createGeneralComplaintsView(Connection conn) throws SQLException {
        String sql = """
            CREATE OR REPLACE VIEW v_general_complaints AS
            SELECT complaint_id, student_id, category_id, title, description, location,
                   student_urgency, status, priority, is_confidential, assigned_admin_id,
                   resolution_notes, created_at, updated_at, resolved_at
            FROM complaints
            WHERE is_confidential = FALSE
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("View 'v_general_complaints' created successfully.");
        }
    }

    private static void ensureViewsAndTriggers(Connection conn) {
        try {
            createGeneralComplaintsView(conn);
        } catch (SQLException e) {
            log.warn("Notice: could not create v_general_complaints view: {}", e.getMessage());
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TRIGGER IF EXISTS trg_complaints_transition");
            String triggerSql = """
                CREATE TRIGGER trg_complaints_transition
                BEFORE UPDATE ON complaints
                FOR EACH ROW
                BEGIN
                  IF NEW.status <> OLD.status AND NOT (
                       (OLD.status = 'Pending'     AND NEW.status = 'In Progress') OR
                       (OLD.status = 'In Progress' AND NEW.status = 'Resolved')    OR
                       (OLD.status = 'Resolved'    AND NEW.status = 'In Progress')
                     ) THEN
                    SIGNAL SQLSTATE '45000'
                      SET MESSAGE_TEXT = 'Invalid complaint status transition. Allowed transitions are Pending -> In Progress -> Resolved, or Resolved -> In Progress.';
                  END IF;
                END
                """;
            stmt.execute(triggerSql);
            log.info("Trigger 'trg_complaints_transition' ensured.");
        } catch (SQLException e) {
            log.warn("Notice: trigger setup deferred: {}", e.getMessage());
        }
    }

    /**
     * Seeds the standard categories using INSERT IGNORE.
     */
    private static void seedCategories(Connection conn) throws SQLException {
        String sql = """
            INSERT IGNORE INTO categories (category_id, category_name, description, is_confidential, is_active)
            VALUES
            (1, 'Food & Mess', 'Food quality, hygiene, serving delays, and mess amenities', FALSE, TRUE),
            (2, 'Electrical & Room Maintenance', 'Lights, ceiling fans, room furniture, switches, and daily cleaning', FALSE, TRUE),
            (3, 'Washroom & Hygiene', 'Washroom sanitation, flush mechanisms, and drainage', FALSE, TRUE),
            (4, 'Water & Common Facilities', 'Drinking water coolers, hot water supply, and common hall facilities', FALSE, TRUE),
            (5, 'Safety & Student Welfare', 'Confidential safety, harassment, ragging, and personal security concerns', TRUE, TRUE),
            (6, 'Laundry Services (Archived)', 'Former laundry complaint channel, currently decommissioned', FALSE, FALSE)
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("Standard categories seeded.");
        }
    }

    /**
     * Seeds demo user accounts idempotently with INSERT IGNORE.
     * Never overwrites existing passwords or resets accounts if already present.
     */
    public static void seedDemoUsers(Connection conn) throws SQLException {
        String sql = """
            INSERT IGNORE INTO users (full_name, email, password_hash, role, account_status, can_view_confidential, created_at)
            VALUES (?, ?, ?, ?, 'Active', ?, NOW())
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // 1. Featured Student accounts
            addBatchUser(ps, "Shrey Vora", "shrey@student.staysync", DEMO_PASSWORD_HASH, "Student", false);
            addBatchUser(ps, "Aadi Sheth", "aadi@student.staysync", DEMO_PASSWORD_HASH, "Student", false);

            // 2. Featured Admin accounts
            addBatchUser(ps, "Bhavesh Patel", "bhavesh@staysync.internal", DEMO_PASSWORD_HASH, "Admin", true);
            addBatchUser(ps, "Shubhangi Keni", "shubhangi@staysync.internal", DEMO_PASSWORD_HASH, "Admin", false);

            // 3. Additional demo students & staff for full scenario coverage
            addBatchUser(ps, "Suresh Patel", "maint@staysync.internal", DEMO_PASSWORD_HASH, "Admin", false);
            addBatchUser(ps, "Rohan Iyer", "rohan@student.staysync", DEMO_PASSWORD_HASH, "Student", false);
            addBatchUser(ps, "Ananya Roy", "ananya@student.staysync", DEMO_PASSWORD_HASH, "Student", false);
            addBatchUser(ps, "Kabir Nair", "kabir@student.staysync", DEMO_PASSWORD_HASH, "Student", false);
            addBatchUser(ps, "Neha Kapoor", "neha@student.staysync", DEMO_PASSWORD_HASH, "Student", false);

            // 4. Backward-compatible classic aliases
            addBatchUser(ps, "Bhavesh Patel", "warden@staysync.internal", DEMO_PASSWORD_HASH, "Admin", true);
            addBatchUser(ps, "Shubhangi Keni", "mess@staysync.internal", DEMO_PASSWORD_HASH, "Admin", false);
            addBatchUser(ps, "Shrey Vora", "aarav@student.staysync", DEMO_PASSWORD_HASH, "Student", false);

            ps.executeBatch();
            log.info("StaySync demo user accounts verified and synchronized.");
        }
    }

    private static void addBatchUser(PreparedStatement ps, String name, String email, String hash, String role, boolean canConf) throws SQLException {
        ps.setString(1, name);
        ps.setString(2, email);
        ps.setString(3, hash);
        ps.setString(4, role);
        ps.setBoolean(5, canConf);
        ps.addBatch();
    }

    /**
     * Seeds initial demo complaints, comments, history, and notifications on a fresh installation.
     * Uses INSERT IGNORE exclusively. NEVER calls DELETE or resets auto-increments.
     */
    private static void seedInitialComplaints(Connection conn) throws SQLException {
        // Resolve student & admin user IDs to ensure foreign keys match
        long studentShreyId = findUserIdByEmail(conn, "shrey@student.staysync");
        long studentAadiId = findUserIdByEmail(conn, "aadi@student.staysync");
        long adminBhaveshId = findUserIdByEmail(conn, "bhavesh@staysync.internal");
        long adminShubhangiId = findUserIdByEmail(conn, "shubhangi@staysync.internal");
        long adminSureshId = findUserIdByEmail(conn, "maint@staysync.internal");

        if (studentShreyId == 0 || adminBhaveshId == 0) {
            log.warn("Demo users not resolved, skipping complaint seed.");
            return;
        }

        long s1 = studentShreyId;
        long s2 = studentAadiId > 0 ? studentAadiId : studentShreyId;
        long a1 = adminBhaveshId;
        long a2 = adminShubhangiId > 0 ? adminShubhangiId : adminBhaveshId;
        long a3 = adminSureshId > 0 ? adminSureshId : a2;

        try (Statement stmt = conn.createStatement()) {
            // Seed Complaints (safe INSERT IGNORE)
            String complaintsSql = String.format("""
                INSERT IGNORE INTO complaints
                  (complaint_id, student_id, category_id, title, description, location, student_urgency, status, priority, is_confidential, assigned_admin_id, resolution_notes, created_at, updated_at, resolved_at)
                VALUES
                  (1, %d, 1, 'Undercooked rice during dinner', 'Rice served in Block B mess was hard and undercooked tonight. Multiple students complained.', 'Block B Mess Hall', 'Medium', 'Resolved', 'Medium', FALSE, %d, 'Rice cooker thermostat recalibrated; batch testing instituted.', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY),
                  (2, %d, 2, 'Ceiling fan making loud grinding noise', 'Fan in room 304 rattles loudly at speeds above 2, disturbing study and sleep.', 'Block A, Room 304', 'Low', 'In Progress', 'Low', FALSE, %d, NULL, NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 6 DAY, NULL),
                  (3, %d, 3, 'Water leakage in 2nd floor washroom', 'Pipe beneath washbasin 3 is leaking continuously creating slippery puddles on the floor.', 'Block C, 2nd Floor Washroom', 'High', 'In Progress', 'High', FALSE, %d, NULL, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY, NULL),
                  (4, %d, 5, 'Suspicious person loitering near back gate', 'Noticed an unidentified individual without visitor badge loitering near Block B emergency exit past 11 PM.', 'Block B, Back Exit Stairwell', 'High', 'Resolved', 'High', TRUE, %d, 'Security patrol frequency doubled; CCTV footage reviewed.', NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 7 DAY),
                  (5, %d, 4, 'Hot water geyser not working in mornings', 'Geyser on 3rd floor does not heat up between 6 AM and 8 AM when demand is highest.', 'Block B, 3rd Floor Bathrooms', 'Medium', 'Pending', NULL, FALSE, NULL, NULL, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, NULL),
                  (6, %d, 1, 'Insects found near salad counter', 'Small flies and insects observed around uncovered salad counter during lunch today.', 'Block A Dining Area', 'High', 'In Progress', 'High', FALSE, %d, NULL, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY, NULL),
                  (7, %d, 2, 'Tube light flickering continuously', 'Study desk fluorescent tube light blinks rapidly causing eye strain.', 'Block C, Room 112', 'Low', 'Pending', NULL, FALSE, NULL, NULL, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, NULL),
                  (8, %d, 5, 'Hostel curfew harassment concern', 'Faced inappropriate comments from night security guard while returning from library before curfew.', 'Main Hostel Gate', 'High', 'In Progress', 'High', TRUE, %d, NULL, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY, NULL),
                  (9, %d, 4, 'Water cooler dispensing warm water', 'Water cooler on first floor compressor appears faulty; water is warm.', 'Block A, 1st Floor Lobby', 'Low', 'Resolved', 'Low', FALSE, %d, 'Replaced cooling relay switch. Compressor tested and confirmed 14C.', NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY),
                  (10, %d, 3, 'Exhaust fan in washroom stopped working', 'Exhaust fan in 4th floor west wing washroom is dead, causing damp smell.', 'Block B, 4th Floor West Washroom', 'Medium', 'Pending', NULL, FALSE, NULL, NULL, NOW() - INTERVAL 12 HOUR, NOW() - INTERVAL 12 HOUR, NULL)
                """,
                    s1, a2,
                    s2, a3,
                    s1, a3,
                    s2, a1,
                    s1,
                    s2, a2,
                    s1,
                    s2, a1,
                    s1, a3,
                    s2
            );
            stmt.execute(complaintsSql);

            // Seed Comments
            String commentsSql = String.format("""
                INSERT IGNORE INTO complaint_comments (comment_id, complaint_id, user_id, comment_text, is_internal, created_at)
                VALUES
                  (1, 1, %d, 'Mess supervisor acknowledged the issue when we showed him the tray.', FALSE, NOW() - INTERVAL 10 DAY + INTERVAL 1 HOUR),
                  (2, 1, %d, 'Inspected kitchen inventory and issued a warning to the evening shift cook.', FALSE, NOW() - INTERVAL 9 DAY),
                  (3, 1, %d, 'Internal note: Replaced old rice cooker valve.', TRUE, NOW() - INTERVAL 9 DAY),
                  (4, 2, %d, 'Maintenance technician scheduled to visit room 304 tomorrow afternoon.', FALSE, NOW() - INTERVAL 6 DAY),
                  (5, 3, %d, 'Temporary shutoff valve turned off. Replacing the copper elbow joint.', FALSE, NOW() - INTERVAL 4 DAY),
                  (6, 4, %d, 'Patrol logs reviewed. Security officer Kumar was assigned to stationary post at back gate.', TRUE, NOW() - INTERVAL 8 DAY),
                  (7, 6, %d, 'Pest control service contacted. Deep sanitation scheduled for tonight after mess closes.', FALSE, NOW() - INTERVAL 1 DAY),
                  (8, 8, %d, 'Spoke with the student privately. Shift supervisor notified to rotate guard out pending inquiry.', TRUE, NOW() - INTERVAL 3 DAY)
                """,
                    s1, a2, a2, a3, a3, a1, a2, a1
            );
            stmt.execute(commentsSql);

            // Seed History
            String historySql = String.format("""
                INSERT IGNORE INTO complaint_history (history_id, complaint_id, changed_by, old_status, new_status, old_priority, new_priority, change_note, changed_at)
                VALUES
                  (1, 1, %d, NULL, 'Pending', NULL, NULL, 'Complaint submitted by student', NOW() - INTERVAL 10 DAY),
                  (2, 1, %d, 'Pending', 'In Progress', NULL, 'Medium', 'Triaged and assigned to mess contractor', NOW() - INTERVAL 9 DAY),
                  (3, 1, %d, 'In Progress', 'Resolved', 'Medium', 'Medium', 'Cooker thermostat fixed and checked', NOW() - INTERVAL 8 DAY),
                  (4, 2, %d, NULL, 'Pending', NULL, NULL, 'Complaint submitted by student', NOW() - INTERVAL 7 DAY),
                  (5, 2, %d, 'Pending', 'In Progress', NULL, 'Low', 'Assigned electrician for room inspection', NOW() - INTERVAL 6 DAY),
                  (6, 3, %d, NULL, 'Pending', NULL, NULL, 'Complaint submitted by student', NOW() - INTERVAL 5 DAY),
                  (7, 3, %d, 'Pending', 'In Progress', NULL, 'High', 'Marked high due to flood hazard', NOW() - INTERVAL 4 DAY),
                  (8, 4, %d, NULL, 'Pending', NULL, NULL, 'Confidential complaint submitted', NOW() - INTERVAL 9 DAY),
                  (9, 4, %d, 'Pending', 'In Progress', NULL, 'High', 'Warden took immediate ownership', NOW() - INTERVAL 8 DAY),
                  (10, 4, %d, 'In Progress', 'Resolved', 'High', 'High', 'Security protocols enforced', NOW() - INTERVAL 7 DAY),
                  (11, 6, %d, NULL, 'Pending', NULL, NULL, 'Complaint submitted by student', NOW() - INTERVAL 2 DAY),
                  (12, 6, %d, 'Pending', 'In Progress', NULL, 'High', 'Hygiene urgency prioritized', NOW() - INTERVAL 1 DAY),
                  (13, 9, %d, NULL, 'Pending', NULL, NULL, 'Complaint submitted by student', NOW() - INTERVAL 14 DAY),
                  (14, 9, %d, 'Pending', 'In Progress', NULL, 'Low', 'Assigned cooling technician', NOW() - INTERVAL 13 DAY),
                  (15, 9, %d, 'In Progress', 'Resolved', 'Low', 'Low', 'Relay switch replaced', NOW() - INTERVAL 12 DAY)
                """,
                    s1, a2, a2,
                    s2, a3,
                    s1, a3,
                    s2, a1, a1,
                    s2, a2,
                    s1, a3, a3
            );
            stmt.execute(historySql);

            // Seed Notifications
            String notifSql = String.format("""
                INSERT IGNORE INTO notifications (notification_id, user_id, complaint_id, message, is_read, created_at)
                VALUES
                  (1, %d, 1, 'Your complaint #1 has been marked as Resolved.', TRUE, NOW() - INTERVAL 8 DAY),
                  (2, %d, 2, 'Your complaint #2 is now In Progress with priority Low.', TRUE, NOW() - INTERVAL 6 DAY),
                  (3, %d, 3, 'Your complaint #3 is now In Progress with priority High.', FALSE, NOW() - INTERVAL 4 DAY),
                  (4, %d, 4, 'There is an update on your private request.', TRUE, NOW() - INTERVAL 7 DAY),
                  (5, %d, 6, 'Your complaint #6 is now In Progress with priority High.', FALSE, NOW() - INTERVAL 1 DAY),
                  (6, %d, 8, 'There is an update on your private request.', FALSE, NOW() - INTERVAL 3 DAY)
                """,
                    s1, s2, s1, s2, s2, a1
            );
            stmt.execute(notifSql);

            log.info("Fresh database demo complaints, comments, history, and notifications initialized.");
        }
    }

    private static long findUserIdByEmail(Connection conn, String email) {
        String sql = "SELECT user_id FROM users WHERE LOWER(email) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    /**
     * Executes a SQL script stream reliably, handling line comments,
     * statement termination, and DELIMITER switches for triggers/routines.
     */
    public static void executeSqlScript(Connection conn, InputStream in) throws SQLException, IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             Statement stmt = conn.createStatement()) {

            StringBuilder sb = new StringBuilder();
            String currentDelimiter = ";";
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // 1. Detect DELIMITER directive (e.g. DELIMITER $$ or DELIMITER ;)
                if (trimmed.toUpperCase().startsWith("DELIMITER")) {
                    String newDelim = trimmed.substring("DELIMITER".length()).trim();
                    if (!newDelim.isEmpty()) {
                        currentDelimiter = newDelim;
                    }
                    continue;
                }

                // 2. Ignore whole-line comments and empty lines when buffer is empty
                if (sb.length() == 0 && (trimmed.startsWith("--") || trimmed.startsWith("#") || trimmed.isEmpty())) {
                    continue;
                }

                // 3. Strip trailing line comments if not inside trigger definition
                if (currentDelimiter.equals(";")) {
                    int commentIdx = line.indexOf("--");
                    if (commentIdx >= 0) {
                        line = line.substring(0, commentIdx);
                    }
                }

                sb.append(line).append("\n");

                // 4. Check if accumulated statement ends with active delimiter
                String accumulated = sb.toString().trim();
                if (accumulated.endsWith(currentDelimiter)) {
                    String sql = accumulated.substring(0, accumulated.length() - currentDelimiter.length()).trim();
                    sb.setLength(0);

                    if (!sql.isEmpty() && !sql.toUpperCase().startsWith("USE ")) {
                        stmt.execute(sql);
                    }
                }
            }

            // Flush remaining statement if any
            String remaining = sb.toString().trim();
            if (!remaining.isEmpty()) {
                if (remaining.endsWith(currentDelimiter)) {
                    remaining = remaining.substring(0, remaining.length() - currentDelimiter.length()).trim();
                }
                if (!remaining.isEmpty() && !remaining.toUpperCase().startsWith("USE ")) {
                    stmt.execute(remaining);
                }
            }
        }
    }

    /**
     * Resolves a SQL script by checking classpath resources first, then filesystem paths.
     */
    public static InputStream getScriptStream(String relativePath) {
        // 1. Try classloader
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            InputStream in = cl.getResourceAsStream(relativePath);
            if (in != null) return in;
        }

        InputStream in = DatabaseInitializer.class.getResourceAsStream("/" + relativePath);
        if (in != null) return in;

        in = DatabaseInitializer.class.getResourceAsStream(relativePath);
        if (in != null) return in;

        // 2. Try filesystem relative to working directory
        try {
            Path p = Paths.get(relativePath);
            if (Files.exists(p)) {
                return Files.newInputStream(p);
            }
            Path parentP = Paths.get("..", relativePath);
            if (Files.exists(parentP)) {
                return Files.newInputStream(parentP);
            }
        } catch (IOException ignored) {}

        return null;
    }

    /**
     * Translates a SQLException into an actionable, user-friendly diagnostic message.
     */
    private static String diagnoseConnectionError(SQLException e, String hostPort, String user) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        int errorCode = e.getErrorCode();
        String sqlState = e.getSQLState() != null ? e.getSQLState() : "";

        // Error 1045 / SQLState 28000: Access denied
        if (errorCode == 1045 || sqlState.startsWith("28")) {
            return "MySQL authentication failed for user '" + user + "'. Please check the password in config/app.properties.";
        }

        // Connection refused / Communications link failure / Host unreachable
        if (sqlState.startsWith("08") || msg.contains("Communications link failure") ||
                msg.contains("Connection refused") || msg.contains("ConnectException")) {
            return "Cannot reach MySQL Server at " + hostPort +
                    ". Please verify MySQL Server is running as a Windows service ('net start MySQL80' or via Services).";
        }

        // Error 1044: Access denied to database (lacks CREATE DATABASE privilege)
        if (errorCode == 1044) {
            return "MySQL user '" + user + "' lacks permissions to create or access the database. Please grant DB privileges or create 'staysync_db' manually.";
        }

        return "MySQL connection error (" + errorCode + "): " + msg;
    }
}
