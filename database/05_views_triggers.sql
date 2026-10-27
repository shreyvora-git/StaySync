-- ==============================================================================
-- StaySync (Hostel Mess Complaint Tracker)
-- Views & Triggers: 05_views_triggers.sql
-- Enforces confidentiality in general reporting and defends status transitions
-- ==============================================================================

USE staysync_db;

-- 1. Confidentiality Backstop View: Excludes confidential safety records
CREATE OR REPLACE VIEW v_general_complaints AS
SELECT complaint_id, student_id, category_id, title, description, location,
       student_urgency, status, priority, is_confidential, assigned_admin_id,
       resolution_notes, created_at, updated_at, resolved_at
FROM complaints
WHERE is_confidential = FALSE;

-- 2. State Machine Backstop Trigger: Restricts complaint status transitions
-- Legal transitions:
--   Pending -> In Progress
--   In Progress -> Resolved
--   Resolved -> In Progress (Reopen)
DROP TRIGGER IF EXISTS trg_complaints_transition;

DELIMITER $$
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
END$$
DELIMITER ;
