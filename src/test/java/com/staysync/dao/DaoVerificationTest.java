package com.staysync.dao;

import com.staysync.db.DataSourceProvider;
import com.staysync.model.Attachment;
import com.staysync.model.Category;
import com.staysync.model.CategoryCount;
import com.staysync.model.Comment;
import com.staysync.model.Complaint;
import com.staysync.model.ComplaintFilter;
import com.staysync.model.DailyTrend;
import com.staysync.model.HistoryEntry;
import com.staysync.model.KpiSummary;
import com.staysync.model.Page;
import com.staysync.model.Priority;
import com.staysync.model.ResolutionStats;
import com.staysync.model.Role;
import com.staysync.model.Status;
import com.staysync.model.User;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end read-only integration verification for Stage 3 DAOs
 * against the local MySQL database staysync_db and seed dataset.
 */
class DaoVerificationTest {

    private static UserDao userDao;
    private static CategoryDao categoryDao;
    private static ComplaintDao complaintDao;
    private static CommentDao commentDao;
    private static HistoryDao historyDao;
    private static AttachmentDao attachmentDao;
    private static AnalyticsDao analyticsDao;

    @BeforeAll
    static void setup() {
        boolean reachable = DataSourceProvider.testConnection();
        Assumptions.assumeTrue(reachable, "MySQL staysync_db is unreachable; skipping live DB tests");

        userDao = new UserDao();
        categoryDao = new CategoryDao();
        complaintDao = new ComplaintDao();
        commentDao = new CommentDao();
        historyDao = new HistoryDao();
        attachmentDao = new AttachmentDao();
        analyticsDao = new AnalyticsDao();
    }

    @Nested
    @DisplayName("UserDao Live Verification")
    class UserDaoTests {

        @Test
        @DisplayName("Finds user by ID and maps all attributes including permissions")
        void testFindById() {
            Optional<User> adminOpt = userDao.findById(1L);
            assertThat(adminOpt).isPresent();
            User admin = adminOpt.get();
            assertThat(admin.userId()).isEqualTo(1L);
            assertThat(admin.fullName()).isEqualTo("Dr. Rajesh Sharma");
            assertThat(admin.role()).isEqualTo(Role.ADMIN);
            assertThat(admin.canViewConfidential()).isTrue();
            assertThat(admin.createdAt()).isNotNull();

            Optional<User> studentOpt = userDao.findById(4L);
            assertThat(studentOpt).isPresent();
            User student = studentOpt.get();
            assertThat(student.fullName()).isEqualTo("Aarav Mehta");
            assertThat(student.role()).isEqualTo(Role.STUDENT);
            assertThat(student.canViewConfidential()).isFalse();

            // Non-existent ID
            assertThat(userDao.findById(99999L)).isEmpty();
        }

        @Test
        @DisplayName("Finds user by email with case-insensitive lookup")
        void testFindByEmail() {
            Optional<User> user1 = userDao.findByEmail("warden@staysync.internal");
            assertThat(user1).isPresent();
            assertThat(user1.get().userId()).isEqualTo(1L);

            // Case-insensitive
            Optional<User> userUpper = userDao.findByEmail("WARDEN@STAYSYNC.INTERNAL");
            assertThat(userUpper).isPresent();
            assertThat(userUpper.get().userId()).isEqualTo(1L);

            // Non-existent email
            assertThat(userDao.findByEmail("nobody@domain.invalid")).isEmpty();
        }

        @Test
        @DisplayName("Retrieves all administrator users")
        void testFindAllAdmins() {
            List<User> admins = userDao.findAllAdmins();
            assertThat(admins).isNotEmpty();
            assertThat(admins).allMatch(User::isAdmin);
            // Seed data has 3 admins (user_ids 1, 2, 3)
            assertThat(admins).extracting(User::userId).contains(1L, 2L, 3L);
        }
    }

    @Nested
    @DisplayName("CategoryDao Live Verification")
    class CategoryDaoTests {

        @Test
        @DisplayName("Retrieves active categories and excludes inactive ones")
        void testFindActive() {
            List<Category> active = categoryDao.findActive();
            assertThat(active).isNotEmpty();
            assertThat(active).allMatch(Category::isActive);

            // Inactive category 6 ('Laundry Services (Archived)') must NOT be in active list
            assertThat(active).extracting(Category::categoryId).doesNotContain(6L);

            // Confidential Safety category must be present and marked confidential
            Optional<Category> safety = active.stream()
                .filter(c -> c.categoryName().contains("Safety"))
                .findFirst();
            assertThat(safety).isPresent();
            assertThat(safety.get().isConfidential()).isTrue();
        }

        @Test
        @DisplayName("Retrieves all categories including inactive ones")
        void testFindAll() {
            List<Category> all = categoryDao.findAll();
            assertThat(all.size()).isGreaterThanOrEqualTo(6);
            assertThat(all).extracting(Category::categoryId).contains(6L);
        }

        @Test
        @DisplayName("Finds category by ID")
        void testFindById() {
            Optional<Category> cat1 = categoryDao.findById(1L);
            assertThat(cat1).isPresent();
            assertThat(cat1.get().categoryName()).isEqualTo("Food & Mess");

            assertThat(categoryDao.findById(9999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("ComplaintDao Live Verification")
    class ComplaintDaoTests {

        @Test
        @DisplayName("Finds complaint by ID with joined student and category metadata")
        void testFindById() {
            Optional<Complaint> c1 = complaintDao.findById(1L);
            assertThat(c1).isPresent();
            Complaint complaint = c1.get();
            assertThat(complaint.complaintId()).isEqualTo(1L);
            assertThat(complaint.title()).isEqualTo("Undercooked rice during dinner");
            assertThat(complaint.status()).isEqualTo(Status.RESOLVED);
            assertThat(complaint.priority()).isEqualTo(Priority.MEDIUM);
            assertThat(complaint.studentName()).isEqualTo("Aarav Mehta");
            assertThat(complaint.categoryName()).isEqualTo("Food & Mess");
            assertThat(complaint.assignedAdminName()).isNotNull();
            assertThat(complaint.resolvedAt()).isNotNull();
            assertThat(complaint.resolutionNotes()).isNotBlank();

            assertThat(complaintDao.findById(99999L)).isEmpty();
        }

        @Test
        @DisplayName("Enforces confidentiality predicate on admin queries")
        void testConfidentialityFiltering() {
            // General admin (canViewConfidential = false)
            ComplaintFilter defaultFilter = ComplaintFilter.defaultFilter();
            Page<Complaint> generalPage = complaintDao.findPage(defaultFilter, false, null);
            assertThat(generalPage.items()).allMatch(c -> !c.isConfidential());
            // Complaints #4 and #8 are confidential safety issues in seed data
            assertThat(generalPage.items()).extracting(Complaint::complaintId).doesNotContain(4L, 8L);

            // Authorized admin (canViewConfidential = true)
            Page<Complaint> authPage = complaintDao.findPage(defaultFilter, true, null);
            assertThat(authPage.totalItems()).isGreaterThan(generalPage.totalItems());
            assertThat(authPage.items()).extracting(Complaint::complaintId).contains(4L, 8L);

            // Direct ID lookup with access check
            assertThat(complaintDao.findByIdWithAccess(4L, false, null)).isEmpty();
            assertThat(complaintDao.findByIdWithAccess(4L, true, null)).isPresent();
        }

        @Test
        @DisplayName("Enforces student scope isolation (student only sees own complaints)")
        void testStudentScopeFiltering() {
            long studentId = 4L; // Aarav Mehta
            ComplaintFilter defaultFilter = ComplaintFilter.defaultFilter();
            Page<Complaint> studentPage = complaintDao.findPage(defaultFilter, false, studentId);

            assertThat(studentPage.items()).isNotEmpty();
            assertThat(studentPage.items()).allMatch(c -> c.studentId() == studentId);
            // Aarav filed complaints 1 and 6
            assertThat(studentPage.items()).extracting(Complaint::complaintId).contains(1L, 6L);
            // Must not contain complaint 2 (filed by student 5)
            assertThat(studentPage.items()).extracting(Complaint::complaintId).doesNotContain(2L);
        }

        @Test
        @DisplayName("Filters complaints by status, priority, and category")
        void testFiltering() {
            // Filter by Resolved
            ComplaintFilter resolvedFilter = new ComplaintFilter(null, Status.RESOLVED, null, null, null, null, 0, 10, "createdAt", false);
            Page<Complaint> resolvedPage = complaintDao.findPage(resolvedFilter, true, null);
            assertThat(resolvedPage.items()).allMatch(c -> c.status() == Status.RESOLVED);

            // Filter by High Priority
            ComplaintFilter highFilter = new ComplaintFilter(null, null, null, Priority.HIGH, null, null, 0, 10, "createdAt", false);
            Page<Complaint> highPage = complaintDao.findPage(highFilter, true, null);
            assertThat(highPage.items()).allMatch(c -> c.priority() == Priority.HIGH);

            // Filter by Category 1 (Food & Mess)
            ComplaintFilter foodFilter = new ComplaintFilter(null, null, 1L, null, null, null, 0, 10, "createdAt", false);
            Page<Complaint> foodPage = complaintDao.findPage(foodFilter, true, null);
            assertThat(foodPage.items()).allMatch(c -> c.categoryId() == 1L);
        }

        @Test
        @DisplayName("Performs keyword search against title and description, and numeric search by ID")
        void testSearch() {
            // Text keyword search
            ComplaintFilter textFilter = new ComplaintFilter("rice", null, null, null, null, null, 0, 10, "createdAt", false);
            Page<Complaint> textPage = complaintDao.findPage(textFilter, true, null);
            assertThat(textPage.items()).extracting(Complaint::complaintId).contains(1L);

            // Numeric ID search
            ComplaintFilter idFilter = new ComplaintFilter("#2", null, null, null, null, null, 0, 10, "createdAt", false);
            Page<Complaint> idPage = complaintDao.findPage(idFilter, true, null);
            assertThat(idPage.items()).hasSize(1);
            assertThat(idPage.items().getFirst().complaintId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Supports pagination and total item counts")
        void testPagination() {
            ComplaintFilter p1 = new ComplaintFilter(null, null, null, null, null, null, 0, 3, "createdAt", false);
            Page<Complaint> page1 = complaintDao.findPage(p1, true, null);
            assertThat(page1.items()).hasSize(3);
            assertThat(page1.totalItems()).isGreaterThanOrEqualTo(10L);
            assertThat(page1.hasNext()).isTrue();

            ComplaintFilter p2 = new ComplaintFilter(null, null, null, null, null, null, 1, 3, "createdAt", false);
            Page<Complaint> page2 = complaintDao.findPage(p2, true, null);
            assertThat(page2.items()).hasSize(3);
            // Items on page 2 should differ from page 1
            assertThat(page2.items().getFirst().complaintId()).isNotEqualTo(page1.items().getFirst().complaintId());
        }

        @Test
        @DisplayName("Handles empty search result without errors")
        void testEmptySearchResult() {
            ComplaintFilter emptyFilter = new ComplaintFilter("nonexistent_query_xyz_12345", null, null, null, null, null, 0, 10, "createdAt", false);
            Page<Complaint> emptyPage = complaintDao.findPage(emptyFilter, true, null);
            assertThat(emptyPage.items()).isEmpty();
            assertThat(emptyPage.totalItems()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("CommentDao Live Verification")
    class CommentDaoTests {

        @Test
        @DisplayName("Respects internal note visibility boundary")
        void testCommentVisibility() {
            long complaintId = 1L;

            // Student/Public view: internal notes omitted
            List<Comment> publicComments = commentDao.findByComplaintId(complaintId, false);
            assertThat(publicComments).isNotEmpty();
            assertThat(publicComments).allMatch(c -> !c.isInternal());

            // Staff view: includes internal notes
            List<Comment> allComments = commentDao.findByComplaintId(complaintId, true);
            assertThat(allComments.size()).isGreaterThan(publicComments.size());
            assertThat(allComments).anyMatch(Comment::isInternal);

            // Comments contain author details
            assertThat(allComments.getFirst().authorName()).isNotBlank();
            assertThat(allComments.getFirst().authorRole()).isNotNull();
        }

        @Test
        @DisplayName("Returns empty list for complaint with no comments")
        void testEmptyComments() {
            List<Comment> comments = commentDao.findByComplaintId(99999L, true);
            assertThat(comments).isEmpty();
        }
    }

    @Nested
    @DisplayName("HistoryDao Live Verification")
    class HistoryDaoTests {

        @Test
        @DisplayName("Retrieves chronological audit history timeline")
        void testHistoryTimeline() {
            long complaintId = 1L;
            List<HistoryEntry> history = historyDao.findByComplaintId(complaintId);
            assertThat(history).isNotEmpty();

            // First entry in seed data is creation
            HistoryEntry first = history.getFirst();
            assertThat(first.complaintId()).isEqualTo(1L);
            assertThat(first.newStatus()).isEqualTo(Status.PENDING);
            assertThat(first.actorName()).isNotBlank();

            // Chronological ordering check
            for (int i = 1; i < history.size(); i++) {
                assertThat(history.get(i).changedAt())
                    .isAfterOrEqualTo(history.get(i - 1).changedAt());
            }
        }

        @Test
        @DisplayName("Returns empty list for complaint with no history")
        void testEmptyHistory() {
            List<HistoryEntry> history = historyDao.findByComplaintId(99999L);
            assertThat(history).isEmpty();
        }
    }

    @Nested
    @DisplayName("AttachmentDao Live Verification")
    class AttachmentDaoTests {

        @Test
        @DisplayName("Retrieves attachment metadata and handles empty results")
        void testAttachmentMetadata() {
            long complaintId = 1L;
            List<Attachment> attachments = attachmentDao.findByComplaintId(complaintId);
            assertThat(attachments).isNotEmpty();

            Attachment first = attachments.getFirst();
            assertThat(first.fileName()).isEqualTo("dinner_rice_tray.jpg");
            assertThat(first.mimeType()).isEqualTo("image/jpeg");
            assertThat(first.uploaderName()).isEqualTo("Aarav Mehta");

            // By ID
            Optional<Attachment> byId = attachmentDao.findById(first.attachmentId());
            assertThat(byId).isPresent();
            assertThat(byId.get().fileName()).isEqualTo("dinner_rice_tray.jpg");

            // Non-existent
            assertThat(attachmentDao.findByComplaintId(99999L)).isEmpty();
            assertThat(attachmentDao.findById(99999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("AnalyticsDao Live Verification")
    class AnalyticsDaoTests {

        @Test
        @DisplayName("Calculates KPI summary totals accurately with confidentiality option")
        void testKpiSummary() {
            KpiSummary general = analyticsDao.getKpiSummary(false);
            assertThat(general.total()).isGreaterThan(0);
            assertThat(general.total()).isEqualTo(general.pending() + general.inProgress() + general.resolved());

            KpiSummary full = analyticsDao.getKpiSummary(true);
            assertThat(full.total()).isGreaterThanOrEqualTo(general.total());
        }

        @Test
        @DisplayName("Calculates category distribution aggregates")
        void testCategoryDistribution() {
            List<CategoryCount> counts = analyticsDao.getCategoryDistribution(false);
            assertThat(counts).isNotEmpty();
            assertThat(counts).allMatch(c -> c.count() > 0);
            assertThat(counts).allMatch(c -> c.categoryName() != null && !c.categoryName().isBlank());
        }

        @Test
        @DisplayName("Calculates daily trend records")
        void testDailyTrend() {
            List<DailyTrend> trend = analyticsDao.getDailyTrend(30, false);
            assertThat(trend).isNotNull();
            assertThat(trend).allMatch(t -> t.date() != null && t.count() > 0);
        }

        @Test
        @DisplayName("Calculates resolution performance statistics")
        void testResolutionPerformance() {
            List<ResolutionStats> stats = analyticsDao.getResolutionPerformance(false);
            assertThat(stats).isNotEmpty();
            assertThat(stats).anyMatch(s -> "All Priorities".equals(s.priorityLevel()));
        }
    }
}
