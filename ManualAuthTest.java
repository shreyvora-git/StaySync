
import com.staysync.service.AuthService;
import com.staysync.security.Session;
import com.staysync.service.ComplaintService;
import com.staysync.error.NotFoundException;

public class ManualAuthTest {

    private static final AuthService authService = new AuthService();
    private static final ComplaintService complaintService = new ComplaintService();

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        try {
            test("1. Aarav can access his own complaint", () -> {
                login("aarav@student.staysync", "password123");
                complaintService.getComplaint(1L);
            });

            test("2. Aarav cannot access another student's complaint", () -> {
                login("aarav@student.staysync", "password123");
                expectNotFound(() -> complaintService.getComplaint(2L));
            });

            test("3. General admin cannot access confidential complaint", () -> {
                login("mess@staysync.internal", "password123");
                expectNotFound(() -> complaintService.getComplaint(4L));
            });

            test("4. Authorized admin can access confidential complaint", () -> {
                login("warden@staysync.internal", "password123");
                complaintService.getComplaint(4L);
            });

        } finally {
            Session.logout();
        }

        System.out.println("\n========== AUTHORIZATION RESULTS ==========");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("============================================");
    }

    private static void login(String email, String password) {
        Session.logout();
        authService.login(email, password);
    }

    private static void expectNotFound(Runnable action) {
        try {
            action.run();
            throw new AssertionError(
                    "Expected NotFoundException, but access was allowed.");
        } catch (NotFoundException expected) {
            // Correct: access was denied without revealing the complaint.
        }
    }

    private static void test(String name, Runnable action) {
        try {
            action.run();
            passed++;
            System.out.println("PASS: " + name);
        } catch (Throwable error) {
            failed++;
            System.out.println("FAIL: " + name);
            System.out.println("      " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        } finally {
            Session.logout();
        }
    }
}
