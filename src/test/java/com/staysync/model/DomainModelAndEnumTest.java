package com.staysync.model;

import com.staysync.dao.JdbcHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelAndEnumTest {

    @Test
    @DisplayName("Role enum maps database values correctly and handles unknown values")
    void testRoleMapping() {
        assertThat(Role.fromDb("Student")).isEqualTo(Role.STUDENT);
        assertThat(Role.fromDb("Admin")).isEqualTo(Role.ADMIN);
        assertThat(Role.fromDb("student")).isEqualTo(Role.STUDENT);
        assertThat(Role.fromDb(null)).isNull();
        assertThat(Role.fromDb("   ")).isNull();

        assertThat(Role.STUDENT.dbValue()).isEqualTo("Student");
        assertThat(Role.ADMIN.dbValue()).isEqualTo("Admin");

        assertThatThrownBy(() -> Role.fromDb("Superuser"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown role");
    }

    @Test
    @DisplayName("Status enum maps database values correctly and handles unknown values")
    void testStatusMapping() {
        assertThat(Status.fromDb("Pending")).isEqualTo(Status.PENDING);
        assertThat(Status.fromDb("In Progress")).isEqualTo(Status.IN_PROGRESS);
        assertThat(Status.fromDb("Resolved")).isEqualTo(Status.RESOLVED);
        assertThat(Status.fromDb(null)).isNull();
        assertThat(Status.fromDb("")).isNull();

        assertThat(Status.IN_PROGRESS.dbValue()).isEqualTo("In Progress");

        assertThatThrownBy(() -> Status.fromDb("Cancelled"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown status");
    }

    @Test
    @DisplayName("Priority enum safely handles null and unknown values")
    void testPriorityMapping() {
        assertThat(Priority.fromDb("Low")).isEqualTo(Priority.LOW);
        assertThat(Priority.fromDb("Medium")).isEqualTo(Priority.MEDIUM);
        assertThat(Priority.fromDb("High")).isEqualTo(Priority.HIGH);
        assertThat(Priority.fromDb(null)).isNull();
        assertThat(Priority.fromDb("")).isNull();
        assertThat(Priority.fromDb("InvalidPriority")).isNull();

        assertThat(Priority.HIGH.dbValue()).isEqualTo("High");
    }

    @Test
    @DisplayName("Urgency enum safely handles student urgency hints")
    void testUrgencyMapping() {
        assertThat(Urgency.fromDb("Low")).isEqualTo(Urgency.LOW);
        assertThat(Urgency.fromDb("Medium")).isEqualTo(Urgency.MEDIUM);
        assertThat(Urgency.fromDb("High")).isEqualTo(Urgency.HIGH);
        assertThat(Urgency.fromDb(null)).isNull();
        assertThat(Urgency.fromDb(" ")).isNull();
        assertThat(Urgency.fromDb("Urgent")).isNull();
    }

    @Test
    @DisplayName("AccountStatus enum maps database values")
    void testAccountStatusMapping() {
        assertThat(AccountStatus.fromDb("Active")).isEqualTo(AccountStatus.ACTIVE);
        assertThat(AccountStatus.fromDb("Suspended")).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(AccountStatus.fromDb(null)).isNull();

        assertThatThrownBy(() -> AccountStatus.fromDb("Deleted"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("User record enforces required attributes and helper predicates")
    void testUserRecord() {
        Instant now = Instant.now();
        User user = new User(1L, "Test Student", "student@test.com", "hash", Role.STUDENT, AccountStatus.ACTIVE, false, now);

        assertThat(user.userId()).isEqualTo(1L);
        assertThat(user.isStudent()).isTrue();
        assertThat(user.isAdmin()).isFalse();
        assertThat(user.isActive()).isTrue();
        assertThat(user.canViewConfidential()).isFalse();

        assertThatThrownBy(() -> new User(1L, null, "email", "hash", Role.STUDENT, AccountStatus.ACTIVE, false, now))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Complaint record supports joined display fields and constructor overloads")
    void testComplaintRecord() {
        Instant now = Instant.now();
        Complaint raw = new Complaint(10L, 4L, 1L, "Title", "Desc", "Room 101", Urgency.MEDIUM, Status.PENDING, null, false, null, null, now, now, null);
        assertThat(raw.studentName()).isNull();
        assertThat(raw.categoryName()).isNull();

        Complaint joined = new Complaint(10L, 4L, 1L, "Title", "Desc", "Room 101", Urgency.MEDIUM, Status.PENDING, null, false, null, null, now, now, null, "Aarav", "Food & Mess", null);
        assertThat(joined.studentName()).isEqualTo("Aarav");
        assertThat(joined.categoryName()).isEqualTo("Food & Mess");
    }

    @Test
    @DisplayName("Page record computes pagination metadata correctly")
    void testPageCalculations() {
        Page<String> page = Page.of(List.of("A", "B", "C"), 0, 3, 10);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(3);
        assertThat(page.totalItems()).isEqualTo(10L);
        assertThat(page.totalPages()).isEqualTo(4);
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isTrue();

        Page<String> lastPage = Page.of(List.of("J"), 3, 3, 10);
        assertThat(lastPage.hasPrevious()).isTrue();
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("ComplaintFilter correctly parses numeric IDs and sets safe boundaries")
    void testComplaintFilter() {
        ComplaintFilter f1 = new ComplaintFilter("123", null, null, null, null, null, -1, 100, null, false);
        assertThat(f1.page()).isEqualTo(0);
        assertThat(f1.size()).isEqualTo(ComplaintFilter.MAX_PAGE_SIZE);
        assertThat(f1.sortKey()).isEqualTo(ComplaintFilter.DEFAULT_SORT_KEY);
        assertThat(f1.qAsIdOrMinusOne()).isEqualTo(123L);

        ComplaintFilter f2 = new ComplaintFilter("#45", null, null, null, null, null, 2, 10, "status", true);
        assertThat(f2.qAsIdOrMinusOne()).isEqualTo(45L);
        assertThat(f2.offset()).isEqualTo(20);

        ComplaintFilter f3 = new ComplaintFilter("food quality", null, null, null, null, null, 0, 10, "status", true);
        assertThat(f3.qAsIdOrMinusOne()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("JdbcHelper escapes SQL LIKE wildcards correctly")
    void testEscapeLike() {
        assertThat(JdbcHelper.escapeLike("100% pure_water\\ice"))
            .isEqualTo("100\\% pure\\_water\\\\ice");
        assertThat(JdbcHelper.escapeLike(null)).isEmpty();
    }
}
