-- ==============================================================================
-- StaySync (Hostel Mess Complaint Tracker)
-- Custom Demo Accounts Script: 08_custom_demo_accounts.sql
--
-- Configures the four featured demo accounts requested for StaySync:
--  1. Student: Shrey Vora (shrey@student.staysync)
--  2. Student: Aadi Sheth (aadi@student.staysync)
--  3. Warden / Admin: Bhavesh Patel (bhavesh@staysync.internal) — Full Confidential Clearance
--  4. Staff / Admin: Shubhangi Keni (shubhangi@staysync.internal) — General Operations Staff
--
-- Password for all four accounts: password123
-- Verified BCrypt hash (cost 10): $2a$10$/2vnaOKh8ylIjaRKnH.nr.imwEDqxVO.oYfKYvWMWPZeIg4E55M/y
--
-- Schema conformance:
--   users.role is ENUM('Student', 'Admin')
--   can_view_confidential is BOOLEAN (TRUE for Warden, FALSE for Staff & Students)
--   account_status is ENUM('Active', 'Suspended')
-- ==============================================================================

USE staysync_db;

START TRANSACTION;

-- Insert or update the 4 featured demo accounts (idempotent upsert)
INSERT INTO users (full_name, email, password_hash, role, account_status, can_view_confidential, created_at)
VALUES
  ('Shrey Vora',     'shrey@student.staysync',     '$2a$10$/2vnaOKh8ylIjaRKnH.nr.imwEDqxVO.oYfKYvWMWPZeIg4E55M/y', 'Student', 'Active', FALSE, NOW()),
  ('Aadi Sheth',     'aadi@student.staysync',      '$2a$10$/2vnaOKh8ylIjaRKnH.nr.imwEDqxVO.oYfKYvWMWPZeIg4E55M/y', 'Student', 'Active', FALSE, NOW()),
  ('Bhavesh Patel',  'bhavesh@staysync.internal',  '$2a$10$/2vnaOKh8ylIjaRKnH.nr.imwEDqxVO.oYfKYvWMWPZeIg4E55M/y', 'Admin',   'Active', TRUE,  NOW()),
  ('Shubhangi Keni', 'shubhangi@staysync.internal', '$2a$10$/2vnaOKh8ylIjaRKnH.nr.imwEDqxVO.oYfKYvWMWPZeIg4E55M/y', 'Admin',   'Active', FALSE, NOW())
ON DUPLICATE KEY UPDATE
  full_name             = VALUES(full_name),
  password_hash         = VALUES(password_hash),
  role                  = VALUES(role),
  account_status        = VALUES(account_status),
  can_view_confidential = VALUES(can_view_confidential);

-- Verify the 4 accounts
SELECT user_id, full_name, email, role, can_view_confidential, account_status
FROM users
WHERE email IN (
  'shrey@student.staysync',
  'aadi@student.staysync',
  'bhavesh@staysync.internal',
  'shubhangi@staysync.internal'
);

COMMIT;
