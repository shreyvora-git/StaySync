package com.staysync.service;

import com.staysync.dao.CategoryDao;
import com.staysync.dao.ComplaintDao;
import com.staysync.dao.HistoryDao;
import com.staysync.dao.NotificationDao;
import com.staysync.dao.UserDao;
import com.staysync.db.TransactionRunner;
import com.staysync.error.ConflictException;
import com.staysync.error.NotFoundException;
import com.staysync.error.ValidationException;
import com.staysync.model.Category;
import com.staysync.model.CategoryUrgencyMapper;
import com.staysync.model.Complaint;
import com.staysync.model.ComplaintFilter;
import com.staysync.model.HistoryEntry;
import com.staysync.model.Notification;
import com.staysync.model.Page;
import com.staysync.model.Priority;
import com.staysync.model.Status;
import com.staysync.model.Urgency;
import com.staysync.model.User;
import com.staysync.security.CurrentUser;
import com.staysync.security.Permissions;
import com.staysync.security.Session;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core business service managing complaint lifecycles, permissions, state transitions,
 * resolution notes validation, audit histories, and optimistic concurrency.
 *
 * <p>Enforces database state machine rules corresponding to MySQL trigger
 * {@code trg_complaints_transition} and CHECK constraint {@code chk_c_resolved}.
 */
public class ComplaintService {

    private final ComplaintDao complaintDao;
    private final CategoryDao categoryDao;
    private final HistoryDao historyDao;
    private final TransactionRunner transactionRunner;
    private final NotificationDao notificationDao;
    private final UserDao userDao;

    public ComplaintService(
        ComplaintDao complaintDao,
        CategoryDao categoryDao,
        HistoryDao historyDao,
        TransactionRunner transactionRunner,
        NotificationDao notificationDao,
        UserDao userDao
    ) {
        this.complaintDao = complaintDao != null ? complaintDao : new ComplaintDao();
        this.categoryDao = categoryDao != null ? categoryDao : new CategoryDao();
        this.historyDao = historyDao != null ? historyDao : new HistoryDao();
        this.transactionRunner = transactionRunner != null ? transactionRunner : new TransactionRunner();
        this.notificationDao = notificationDao != null ? notificationDao : new NotificationDao();
        this.userDao = userDao != null ? userDao : new UserDao();
    }

    public ComplaintService() {
        this(new ComplaintDao(), new CategoryDao(), new HistoryDao(), new TransactionRunner(), new NotificationDao(), new UserDao());
    }

    /**
     * Retrieves a single complaint by ID with full role-based access control.
     * Throws {@link NotFoundException} if the complaint does not exist, or if the
     * complaint is confidential and the authenticated user lacks confidential clearance.
     *
     * @param complaintId target complaint ID
     * @return the requested {@link Complaint}
     */
    public Complaint getComplaint(long complaintId) {
        CurrentUser user = Session.requireCurrentUser();

        Complaint complaint = complaintDao.findById(complaintId)
            .orElseThrow(() -> new NotFoundException("Complaint #" + complaintId + " not found."));

        Permissions.checkComplaintAccess(user, complaint);
        return complaint;
    }

    /**
     * Lists complaints with pagination, filtering, search, and strict security isolation.
     *
     * <ul>
     *   <li>Students are isolated to their own {@code studentId}.</li>
     *   <li>Admins are bound by the confidentiality predicate {@code canViewConfidential}.</li>
     * </ul>
     *
     * @param filter user-requested search and pagination bounds
     * @return paginated complaints slice
     */
    public Page<Complaint> listComplaints(ComplaintFilter filter) {
        CurrentUser user = Session.requireCurrentUser();
        ComplaintFilter safeFilter = filter != null ? filter : ComplaintFilter.defaultFilter();

        if (user.isStudent()) {
            return complaintDao.findPage(safeFilter, false, user.userId());
        }

        if (user.isAdmin()) {
            return complaintDao.findPage(safeFilter, user.canViewConfidential(), null);
        }

        return Page.empty(safeFilter.page(), safeFilter.size());
    }

    /**
     * Creates a new complaint for the authenticated student within an ACID transaction.
     *
     * <ol>
     *   <li>Validates student role and input field lengths.</li>
     *   <li>Verifies the category is active and copies {@code is_confidential} from category.</li>
     *   <li>Inserts complaint row with initial status {@link Status#PENDING} and null priority.</li>
     *   <li>Appends initial audit history entry (NULL -&gt; Pending).</li>
     * </ol>
     *
     * @param title          complaint summary (5-150 chars)
     * @param description    detailed complaint description (20-2000 chars)
     * @param categoryId     target complaint category
     * @param location       optional room or block location note (&lt;= 100 chars)
     * @param studentUrgency optional student urgency indicator
     * @return the newly created complaint record
     */
    /**
     * Creates a new complaint for the authenticated student with automatic category urgency assignment.
     */
    public Complaint createComplaint(
        String title,
        String description,
        long categoryId,
        String location
    ) {
        return createComplaint(title, description, categoryId, location, null);
    }

    public Complaint createComplaint(
        String title,
        String description,
        long categoryId,
        String location,
        Urgency studentUrgency
    ) {
        CurrentUser user = Session.requireCurrentUser();
        Permissions.requireStudent(user);

        validateComplaintInput(title, description, location);

        return transactionRunner.call(conn -> {
            Category category = categoryDao.findById(conn, categoryId)
                .orElseThrow(() -> new ValidationException("Selected complaint category does not exist."));

            if (!category.isActive()) {
                throw new ValidationException("Selected category is archived or inactive.");
            }

            // System-assigned urgency derived strictly from category policy (ignores untrusted client inputs)
            Urgency assignedUrgency = CategoryUrgencyMapper.getUrgency(category);

            Instant now = Instant.now();
            Complaint newRecord = new Complaint(
                0L,
                user.userId(),
                category.categoryId(),
                title.trim(),
                description.trim(),
                location != null && !location.isBlank() ? location.trim() : null,
                assignedUrgency,
                Status.PENDING,
                null,
                category.isConfidential(),
                null,
                null,
                now,
                now,
                null
            );

            long createdId = complaintDao.insert(conn, newRecord);

            HistoryEntry initialHistory = new HistoryEntry(
                0L,
                createdId,
                user.userId(),
                null,
                Status.PENDING,
                null,
                null,
                "Complaint filed by student (" + assignedUrgency.dbValue() + " urgency assigned)",
                now
            );
            historyDao.insert(conn, initialHistory);

            // Notify relevant admin accounts
            List<User> admins = userDao.findAllAdmins(conn);
            String urgTag = " [" + assignedUrgency.dbValue() + " Urgency]";
            for (User admin : admins) {
                if (category.isConfidential() && !admin.canViewConfidential()) {
                    continue; // Skip admins without confidential clearance
                }
                String notifMsg = category.isConfidential()
                    ? "New confidential complaint #" + createdId + " filed" + urgTag
                    : "New complaint #" + createdId + " (" + category.categoryName() + ")" + urgTag + ": " + title.trim();
                notificationDao.insert(conn, new Notification(admin.userId(), createdId, notifMsg));
            }

            return complaintDao.findById(conn, createdId).orElseThrow(() ->
                new NotFoundException("Failed to retrieve created complaint #" + createdId)
            );
        });
    }

    /**
     * Updates complaint status, priority, and assigned admin within an ACID transaction.
     *
     * <p>Enforces:
     * <ul>
     *   <li>Admin role and confidential clearance validation.</li>
     *   <li>Lifecycle state machine: {@code Pending -> In Progress}, {@code In Progress -> Resolved}, {@code Resolved -> In Progress}.</li>
     *   <li>Mandatory non-blank resolution notes when resolving.</li>
     *   <li>Mandatory non-blank change note when reopening.</li>
     *   <li>Optimistic concurrency locking on {@code expectedUpdatedAt}.</li>
     *   <li>Audit history logging in {@code complaint_history}.</li>
     * </ul>
     */
    public Complaint updateStatusAndPriority(
        long complaintId,
        Status newStatus,
        Priority newPriority,
        Long assignedAdminId,
        String changeNote,
        String resolutionNotes,
        Instant expectedUpdatedAt
    ) {
        CurrentUser user = Session.requireCurrentUser();
        Permissions.requireAdmin(user);
        Objects.requireNonNull(expectedUpdatedAt, "expectedUpdatedAt is required for optimistic concurrency control");

        Complaint current = complaintDao.findById(complaintId)
            .orElseThrow(() -> new NotFoundException("Complaint #" + complaintId + " not found."));

        Permissions.checkComplaintAccess(user, current);

        Status oldStatus = current.status();
        Status targetStatus = newStatus != null ? newStatus : oldStatus;
        Priority oldPriority = current.priority();
        Priority targetPriority = newPriority != null ? newPriority : oldPriority;

        // 1. Enforce State Machine Transitions
        validateTransition(oldStatus, targetStatus);

        // 2. Validate Resolution Notes & Reopen Conditions
        Instant resolvedAt;
        String finalResolutionNotes;

        if (targetStatus == Status.RESOLVED) {
            if (resolutionNotes == null || resolutionNotes.isBlank()) {
                throw new ValidationException("Resolution notes are mandatory when marking a complaint as Resolved.");
            }
            finalResolutionNotes = resolutionNotes.trim();
            resolvedAt = Instant.now();
        } else {
            // Under chk_c_resolved constraint, non-resolved complaints must have resolved_at IS NULL
            resolvedAt = null;
            finalResolutionNotes = null;

            if (oldStatus == Status.RESOLVED && targetStatus == Status.IN_PROGRESS) {
                if (changeNote == null || changeNote.isBlank()) {
                    throw new ValidationException("A change note explaining why the complaint is being reopened is mandatory.");
                }
            }
        }

        // 3. Execute Transaction
        return transactionRunner.call(conn -> {
            boolean updated = complaintDao.updateStatusAndPriority(
                conn,
                complaintId,
                targetStatus,
                targetPriority,
                assignedAdminId,
                finalResolutionNotes,
                resolvedAt,
                expectedUpdatedAt
            );

            if (!updated) {
                throw new ConflictException(
                    "This complaint was updated by another administrator. Please refresh and review before making changes."
                );
            }

            String auditNote = (changeNote != null && !changeNote.isBlank())
                ? changeNote.trim()
                : "Updated status to " + targetStatus + (targetPriority != null ? " with priority " + targetPriority : "");

            HistoryEntry history = new HistoryEntry(
                0L,
                complaintId,
                user.userId(),
                oldStatus,
                targetStatus,
                oldPriority,
                targetPriority,
                auditNote,
                Instant.now()
            );
            historyDao.insert(conn, history);

            // Notify the student who filed the complaint
            String studentNotif;
            if (current.isConfidential()) {
                studentNotif = "Your confidential request #" + complaintId + " status was updated to " + targetStatus.dbValue() + ".";
            } else {
                studentNotif = "Your complaint #" + complaintId + " (" + current.title() + ") is now " + targetStatus.dbValue()
                    + (finalResolutionNotes != null ? " — Note: " + finalResolutionNotes : ".");
            }
            notificationDao.insert(conn, new Notification(current.studentId(), complaintId, studentNotif));

            return complaintDao.findById(conn, complaintId).orElseThrow(() ->
                new NotFoundException("Failed to reload updated complaint #" + complaintId)
            );
        });
    }

    /**
     * Retrieves the chronological audit history for a complaint, enforcing access permissions.
     */
    public List<HistoryEntry> getComplaintHistory(long complaintId) {
        getComplaint(complaintId); // Enforces access check
        return historyDao.findByComplaintId(complaintId);
    }

    private void validateTransition(Status oldStatus, Status newStatus) {
        if (oldStatus == newStatus) {
            return; // No status change (e.g. updating priority only)
        }

        boolean valid =
            (oldStatus == Status.PENDING && newStatus == Status.IN_PROGRESS) ||
            (oldStatus == Status.IN_PROGRESS && newStatus == Status.RESOLVED) ||
            (oldStatus == Status.RESOLVED && newStatus == Status.IN_PROGRESS);

        if (!valid) {
            throw new ConflictException(
                "Invalid status transition from " + oldStatus + " to " + newStatus +
                ". Permitted transitions are: Pending -> In Progress, In Progress -> Resolved, or Resolved -> In Progress."
            );
        }
    }

    private void validateComplaintInput(String title, String description, String location) {
        if (title == null || title.trim().length() < 5 || title.trim().length() > 150) {
            throw new ValidationException("Complaint title must be between 5 and 150 characters.");
        }
        if (description == null || description.trim().length() < 20 || description.trim().length() > 2000) {
            throw new ValidationException("Complaint description must be between 20 and 2000 characters.");
        }
        if (location != null && location.trim().length() > 100) {
            throw new ValidationException("Location note must not exceed 100 characters.");
        }
    }
}
