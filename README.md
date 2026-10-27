# StaySync — Hostel Mess Complaint Tracker

StaySync is a desktop hostel and mess complaint management system built with **Java 21**, **Java Swing** (with FlatLaf), and **MySQL 8.0+**. It features authenticated roles for students and administrative staff, real-time SLA metrics, category-based routing, confidential issue handling, and an append-only audit trail.

---

## 1. System Requirements

| Component | Minimum Requirement | Recommended |
| :--- | :--- | :--- |
| **Operating System** | Windows 10 / 11 (64-bit) | Windows 11 (64-bit) |
| **Java Development Kit** | JDK 21 | [Eclipse Temurin OpenJDK 21](https://adoptium.net/temurin/releases/?version=21) |
| **Build Tool** | Apache Maven 3.8+ *(optional if using pre-built JAR)* | Maven 3.9+ |
| **Database Server** | MySQL Server 8.0+ or 9.x | MySQL Server 8.0/8.4 (Community Edition) |
| **MySQL Tools** | MySQL Command Line Client | MySQL CLI (Workbench is **not** required) |

> [!IMPORTANT]
> **MySQL Server is an external service**: An SQL file or Java application cannot start a MySQL Server. MySQL Server must be installed and actively running as a background Windows service before StaySync can connect.

---

## 2. MySQL Server Setup (Command Line Only)

StaySync is fully compatible with command-line-only MySQL administration. MySQL Workbench is **not** required.

### A. Verify MySQL Server is Running

Open Command Prompt as Administrator and run:

```cmd
sc query MySQL80
```
*(If your service name is different, check `net start | findstr -i mysql`)*

If the service is stopped, start it:
```cmd
net start MySQL80
```

### B. Database Initialization Options

StaySync has **safe automated initialization** built-in (see Section 3), but you can also manage the database manually via the MySQL CLI.

#### Option 1: Automated First-Run Setup (Recommended)
You do **not** need to manually execute SQL scripts!
1. Ensure MySQL Server is running.
2. Configure your MySQL credentials in `config/app.properties`.
3. Launch StaySync.
4. StaySync will detect if `staysync_db` is missing, create it, load the canonical schema, and safely seed standard categories and demo accounts without deleting existing records.

#### Option 2: Manual MySQL CLI Setup (If Preferred)
Log in using the MySQL Command Line Client:

```cmd
mysql -u root -p
```
Enter your MySQL root password when prompted.

Then execute the following safe commands in order:

```sql
-- 1. Create the database
CREATE DATABASE IF NOT EXISTS staysync_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE staysync_db;

-- 2. Execute canonical schema
SOURCE database/01_schema.sql;

-- 3. Execute views and transition trigger
SOURCE database/05_views_triggers.sql;

-- 4. Provision featured demo accounts
SOURCE database/08_custom_demo_accounts.sql;
```

### C. SQL Script Safety Guide

| Script | Purpose | Safety Classification | Instructions |
| :--- | :--- | :--- | :--- |
| `01_schema.sql` | Canonical database DDL (tables, keys, constraints) | **SAFE** for first-time setup | Creates tables if absent. Safe to run. |
| `05_views_triggers.sql` | Analytics view (`v_general_complaints`) and status trigger | **SAFE** for first-time setup | Idempotent (`CREATE OR REPLACE VIEW`, `DROP TRIGGER IF EXISTS`). |
| `08_custom_demo_accounts.sql` | Featured demonstration accounts | **SAFE** | Idempotent upsert (`ON DUPLICATE KEY UPDATE`). |
| `06_fix_demo_passwords.sql` | Password hash reset for default accounts | **CONDITIONAL** | Only run if resetting passwords to `password123`. |
| `02_seed.sql` | Full synthetic dataset with demo complaints | **DESTRUCTIVE** | **DO NOT RUN on existing databases!** Lines 11–19 delete all existing records (`DELETE FROM complaints`, etc.). |
| `07_cleanup_demo_complaints.sql` | Purges demo complaints 1–10 | **DESTRUCTIVE** | Deletes demo complaints. Do not run if you want to keep demo records. |
| `03_dml_demo.sql` | Evaluator demonstration transactions | **DEMO ONLY** | Inserts test complaints and immediately cleans them up. |
| `04_queries.sql` | Academic rubric test queries | **READ ONLY** | Safe SELECT queries for inspection. |

---

## 3. How the Automated Database Initializer Works

When StaySync starts up, `DatabaseInitializer` performs an asynchronous, non-blocking pre-flight sequence:

1. **Server Reachability**: Tests connectivity to the MySQL Server port (e.g. `localhost:3306`) with a 3-second timeout. If the server is offline, it displays an actionable message instead of hanging.
2. **Database Auto-Creation**: Checks if `staysync_db` exists. If not, it creates the database using `utf8mb4` encoding (provided the configured account has permission).
3. **Fresh Install Detection**:
   - If tables do not exist: Applies `01_schema.sql`, creates `v_general_complaints` and status triggers, seeds the 6 standard categories, provisions all demo users, and seeds initial demo complaints.
   - **No destructive deletes**: Destructive `DELETE` and `ALTER AUTO_INCREMENT` commands from `02_seed.sql` are **never** executed.
4. **Existing Database Safety**:
   - If tables already exist: Connects normally without dropping, recreating, or wiping any records.
   - Verifies the 7 core tables exist. If any table is missing, it halts and reports an incomplete schema to prevent accidental data loss.
   - Verifies `v_general_complaints` view exists (required by Analytics).
   - Syncs demo accounts using `INSERT IGNORE` so existing user passwords and profiles are **never** overwritten.

---

## 4. Secure Database Configuration

1. Make a copy of the example configuration file:
   ```cmd
   copy config\app.properties.example config\app.properties
   ```
2. Open `config\app.properties` in Notepad or your preferred editor:
   ```properties
   # MySQL Connection URL
   db.url=jdbc:mysql://localhost:3306/staysync_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false

   # Local MySQL credentials
   db.user=root
   db.password=YOUR_MYSQL_PASSWORD

   # Connection Pool Size
   db.pool.size=5

   # Attachments directory & 256-bit AES encryption key
   uploads.dir=./uploads
   files.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
   ```
3. Save and close the file.

> [!CAUTION]
> **GitHub Security**: `config/app.properties` is registered in `.gitignore` and must **never** be committed or pushed to a Git repository. Always keep only `config/app.properties.example` in version control.

---

## 5. How to Launch StaySync

### Method 1: Windows One-Click Launcher (`run.bat`)
Double-click `run.bat` in the project root, or execute:
```cmd
run.bat
```
The launcher will:
- Verify that Java 21+ is available on PATH.
- Automatically create `config/app.properties` from template if missing and open it for editing.
- Launch the packaged `target/staysync.jar` (or fall back to `mvn compile exec:java`).
- Keep the terminal open with diagnostic output if an error occurs.

### Method 2: Maven CLI (Development Mode)
```cmd
mvn compile exec:java
```

### Method 3: Executable Fat JAR
Build the self-contained shaded JAR:
```cmd
mvn clean package -DskipTests
```
Then run anywhere on a machine with Java 21:
```cmd
java -jar target/staysync.jar
```

---

## 6. Demonstration Accounts

All demo accounts use the standard password: **`password123`**

You can click any of the **Demo Account Chips** on the login screen for one-click credentials auto-fill:

| Role | Name | Email | Password | Clearance |
| :--- | :--- | :--- | :--- | :--- |
| **Student** | Shrey Vora | `shrey@student.staysync` | `password123` | Standard Student Panel |
| **Student** | Aadi Sheth | `aadi@student.staysync` | `password123` | Standard Student Panel |
| **Admin (Warden)** | Bhavesh Patel | `bhavesh@staysync.internal` | `password123` | **Full Confidential Clearance** |
| **Admin (Staff)** | Shubhangi Keni | `shubhangi@staysync.internal` | `password123` | General Admin Operations |
| **Admin (Maintenance)** | Suresh Patel | `maint@staysync.internal` | `password123` | General Maintenance Admin |

---

## 7. Common Errors and Troubleshooting

### 1. `Cannot connect to MySQL Server at localhost:3306` / `Database Offline`
- **Cause**: MySQL Server service is stopped or not installed.
- **Fix**: Open Command Prompt as Administrator and run `net start MySQL80`. Check Task Manager → Services to verify the MySQL service is `Running`.

### 2. `MySQL authentication failed for user 'root'` / Error 1045
- **Cause**: Incorrect password in `config/app.properties`.
- **Fix**: Open `config/app.properties` and verify `db.password` matches your local MySQL root password.

### 3. `UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java Runtime (class file version 65.0)`
- **Cause**: Running with Java 17 or older. Class file version 65 requires Java 21.
- **Fix**: Install JDK 21 and verify with `java -version`. Ensure `JAVA_HOME` points to the JDK 21 installation.

### 4. `MySQL user '...' lacks permissions to create or access the database` / Error 1044
- **Cause**: The configured MySQL user does not have `CREATE DATABASE` privileges.
- **Fix**: Either run with `db.user=root`, or log into MySQL CLI as root and grant privileges:
  ```sql
  CREATE DATABASE IF NOT EXISTS staysync_db;
  GRANT ALL PRIVILEGES ON staysync_db.* TO 'your_user'@'localhost';
  FLUSH PRIVILEGES;
  ```

---

## 8. License & Attribution

StaySync is an academic microproject developed with native Java Swing, HikariCP, and MySQL Connector/J.
