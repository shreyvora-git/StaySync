-- ==============================================================================
-- StaySync (Hostel Mess Complaint Tracker)
-- Database DDL: 01_schema.sql
-- Targets: MySQL 8.0+ / InnoDB / utf8mb4
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS staysync_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE staysync_db;

-- 1. Users Table (Students & Admins)
CREATE TABLE IF NOT EXISTS users (
  user_id               INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  full_name             VARCHAR(100)  NOT NULL,
  email                 VARCHAR(255)  NOT NULL,
  password_hash         VARCHAR(255)  NOT NULL,
  role                  ENUM('Student','Admin') NOT NULL DEFAULT 'Student',
  account_status        ENUM('Active','Suspended') NOT NULL DEFAULT 'Active',
  can_view_confidential BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT chk_users_conf_admin_only CHECK (can_view_confidential = FALSE OR role = 'Admin')
) ENGINE=InnoDB;

-- 2. Complaint Categories Table
CREATE TABLE IF NOT EXISTS categories (
  category_id     INT UNSIGNED NOT NULL AUTO_INCREMENT,
  category_name   VARCHAR(80)  NOT NULL,
  description     VARCHAR(255) NULL,
  is_confidential BOOLEAN      NOT NULL DEFAULT FALSE,
  is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
  PRIMARY KEY (category_id),
  CONSTRAINT uq_categories_name UNIQUE (category_name)
) ENGINE=InnoDB;

-- 3. Complaints Table
CREATE TABLE IF NOT EXISTS complaints (
  complaint_id      INT UNSIGNED NOT NULL AUTO_INCREMENT,
  student_id        INT UNSIGNED NOT NULL,
  category_id       INT UNSIGNED NOT NULL,
  title             VARCHAR(150) NOT NULL,
  description       TEXT         NOT NULL,
  location          VARCHAR(100) NULL,
  student_urgency   ENUM('Low','Medium','High') NULL,
  status            ENUM('Pending','In Progress','Resolved') NOT NULL DEFAULT 'Pending',
  priority          ENUM('Low','Medium','High') NULL,
  is_confidential   BOOLEAN      NOT NULL DEFAULT FALSE,
  assigned_admin_id INT UNSIGNED NULL,
  resolution_notes  TEXT         NULL,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  resolved_at       TIMESTAMP    NULL,
  PRIMARY KEY (complaint_id),
  CONSTRAINT fk_c_student  FOREIGN KEY (student_id)        REFERENCES users(user_id)          ON DELETE RESTRICT,
  CONSTRAINT fk_c_category FOREIGN KEY (category_id)       REFERENCES categories(category_id) ON DELETE RESTRICT,
  CONSTRAINT fk_c_admin    FOREIGN KEY (assigned_admin_id) REFERENCES users(user_id)          ON DELETE SET NULL,
  CONSTRAINT chk_c_resolved CHECK (
    (status = 'Resolved' AND resolved_at IS NOT NULL AND resolution_notes IS NOT NULL)
    OR (status <> 'Resolved' AND resolved_at IS NULL)
  ),
  INDEX idx_c_student_created (student_id, created_at),
  INDEX idx_c_status_priority (status, priority),
  INDEX idx_c_category        (category_id),
  INDEX idx_c_created         (created_at),
  INDEX idx_c_conf_status     (is_confidential, status)
) ENGINE=InnoDB;

-- 4. Complaint Comments Table (Discussion & Internal Notes)
CREATE TABLE IF NOT EXISTS complaint_comments (
  comment_id    INT UNSIGNED NOT NULL AUTO_INCREMENT,
  complaint_id  INT UNSIGNED NOT NULL,
  user_id       INT UNSIGNED NOT NULL,
  comment_text  TEXT         NOT NULL,
  is_internal   BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (comment_id),
  CONSTRAINT fk_cm_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
  CONSTRAINT fk_cm_user      FOREIGN KEY (user_id)      REFERENCES users(user_id)           ON DELETE RESTRICT,
  INDEX idx_cm_complaint_created (complaint_id, created_at)
) ENGINE=InnoDB;

-- 5. Complaint Audit History Table (Append-only)
CREATE TABLE IF NOT EXISTS complaint_history (
  history_id    INT UNSIGNED NOT NULL AUTO_INCREMENT,
  complaint_id  INT UNSIGNED NOT NULL,
  changed_by    INT UNSIGNED NOT NULL,
  old_status    ENUM('Pending','In Progress','Resolved') NULL,
  new_status    ENUM('Pending','In Progress','Resolved') NULL,
  old_priority  ENUM('Low','Medium','High') NULL,
  new_priority  ENUM('Low','Medium','High') NULL,
  change_note   VARCHAR(500) NULL,
  changed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (history_id),
  CONSTRAINT fk_h_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE RESTRICT,
  CONSTRAINT fk_h_user      FOREIGN KEY (changed_by)   REFERENCES users(user_id)           ON DELETE RESTRICT,
  INDEX idx_h_complaint_changed (complaint_id, changed_at)
) ENGINE=InnoDB;

-- 6. Notifications Table (In-app alerts)
CREATE TABLE IF NOT EXISTS notifications (
  notification_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         INT UNSIGNED NOT NULL,
  complaint_id    INT UNSIGNED NULL,
  message         VARCHAR(255) NOT NULL,
  is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notification_id),
  CONSTRAINT fk_n_user      FOREIGN KEY (user_id)      REFERENCES users(user_id)           ON DELETE CASCADE,
  CONSTRAINT fk_n_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE SET NULL,
  INDEX idx_n_user_read (user_id, is_read, created_at)
) ENGINE=InnoDB;

-- 7. Complaint Attachments Table (Metadata for stored files)
CREATE TABLE IF NOT EXISTS complaint_attachments (
  attachment_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  complaint_id  INT UNSIGNED NOT NULL,
  uploaded_by   INT UNSIGNED NOT NULL,
  file_name     VARCHAR(255) NOT NULL,
  file_path     VARCHAR(255) NOT NULL,
  mime_type     VARCHAR(100) NOT NULL,
  uploaded_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (attachment_id),
  CONSTRAINT fk_a_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
  CONSTRAINT fk_a_user      FOREIGN KEY (uploaded_by)  REFERENCES users(user_id)           ON DELETE RESTRICT,
  INDEX idx_a_complaint (complaint_id)
) ENGINE=InnoDB;
