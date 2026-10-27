-- ==============================================================================
-- StaySync (Hostel Mess Complaint Tracker)
-- Core Queries: 04_queries.sql
-- SELECT, JOIN, Aggregates, Pagination, and Analytics queries
-- For MySQL Workbench evaluation and report generation
-- ==============================================================================

USE staysync_db;

-- ------------------------------------------------------------------------------
-- Q1. Student's own complaints, newest first (paginated)
-- Demonstrates: JOIN, WHERE, ORDER BY, LIMIT/OFFSET
-- ------------------------------------------------------------------------------
SELECT c.complaint_id, c.title, cat.category_name, c.status, c.priority, c.created_at
FROM complaints c
JOIN categories cat ON cat.category_id = c.category_id
WHERE c.student_id = 4
  AND ('All' = 'All' OR c.status = 'Pending')
ORDER BY c.created_at DESC
LIMIT 10 OFFSET 0;

-- ------------------------------------------------------------------------------
-- Q2. Admin queue with confidentiality predicate and multi-column filter
-- Demonstrates: Multi-table JOIN, Confidentiality Guard, Search, Pagination
-- ------------------------------------------------------------------------------
SET @can_view_confidential = FALSE;

SELECT c.complaint_id, c.title, cat.category_name, u.full_name AS student,
       c.status, c.priority, c.student_urgency, c.is_confidential, c.created_at
FROM complaints c
JOIN categories cat ON cat.category_id = c.category_id
JOIN users u        ON u.user_id = c.student_id
WHERE (c.is_confidential = FALSE OR @can_view_confidential = TRUE)
ORDER BY c.created_at DESC
LIMIT 10 OFFSET 0;

-- ------------------------------------------------------------------------------
-- Q3. KPI Summary Counts (Non-confidential public analytics)
-- Demonstrates: Aggregate functions COUNT, SUM with boolean condition
-- ------------------------------------------------------------------------------
SELECT COUNT(*) AS total,
       COALESCE(SUM(status = 'Pending'), 0)     AS pending,
       COALESCE(SUM(status = 'In Progress'), 0) AS in_progress,
       COALESCE(SUM(status = 'Resolved'), 0)    AS resolved
FROM v_general_complaints;

-- ------------------------------------------------------------------------------
-- Q4. Complaints by Category (Bar Chart data)
-- Demonstrates: GROUP BY, aggregate COUNT, ORDER BY
-- ------------------------------------------------------------------------------
SELECT cat.category_name, COUNT(*) AS total
FROM v_general_complaints g
JOIN categories cat ON cat.category_id = g.category_id
GROUP BY cat.category_id, cat.category_name
ORDER BY total DESC;

-- ------------------------------------------------------------------------------
-- Q5. 30-Day Trend (Created per day)
-- Demonstrates: Date arithmetic, DATE formatting, GROUP BY
-- ------------------------------------------------------------------------------
SELECT DATE(created_at) AS day, COUNT(*) AS created
FROM v_general_complaints
WHERE created_at >= CURDATE() - INTERVAL 29 DAY
GROUP BY DATE(created_at)
ORDER BY day;

-- ------------------------------------------------------------------------------
-- Q6. Resolution Performance (Average hours to resolve by priority)
-- Demonstrates: TIMESTAMPDIFF, AVG, ROUND, WITH ROLLUP
-- ------------------------------------------------------------------------------
SELECT COALESCE(priority, 'All Priorities') AS priority_level,
       ROUND(AVG(TIMESTAMPDIFF(MINUTE, created_at, resolved_at)) / 60.0, 1) AS avg_hours,
       COUNT(*) AS resolved_count
FROM v_general_complaints
WHERE status = 'Resolved'
GROUP BY priority WITH ROLLUP;

-- ------------------------------------------------------------------------------
-- Q7. Full Audit Timeline for a specific complaint
-- Demonstrates: JOIN with user details, chronological audit history
-- ------------------------------------------------------------------------------
SELECT h.changed_at, u.full_name AS actor, u.role, h.old_status, h.new_status,
       h.old_priority, h.new_priority, h.change_note
FROM complaint_history h
JOIN users u ON u.user_id = h.changed_by
WHERE h.complaint_id = 1
ORDER BY h.changed_at ASC, h.history_id ASC;

-- ------------------------------------------------------------------------------
-- Q8. Unread notification count for an authenticated user
-- Demonstrates: Point-index single-user unread badge calculation
-- ------------------------------------------------------------------------------
SELECT COUNT(*) AS unread_notifications
FROM notifications
WHERE user_id = 4 AND is_read = FALSE;

-- ------------------------------------------------------------------------------
-- Q9. Needs-Triage Queue (Pending complaints lacking priority assignment)
-- Demonstrates: IS NULL filter, priority queue sorting
-- ------------------------------------------------------------------------------
SELECT c.complaint_id, c.title, cat.category_name, c.student_urgency, c.created_at
FROM v_general_complaints c
JOIN categories cat ON cat.category_id = c.category_id
WHERE c.status = 'Pending' AND c.priority IS NULL
ORDER BY c.created_at ASC
LIMIT 10;
