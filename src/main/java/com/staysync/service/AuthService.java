package com.staysync.service;

import com.staysync.dao.UserDao;
import com.staysync.error.AuthenticationException;
import com.staysync.error.ValidationException;
import com.staysync.model.AccountStatus;
import com.staysync.model.User;
import com.staysync.security.CurrentUser;
import com.staysync.security.LoginThrottle;
import com.staysync.security.PasswordHasher;
import com.staysync.security.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Authentication service coordinating credential verification, account status validation,
 * rate-limit throttling, and desktop session lifecycle.
 *
 * <p>Never compares plaintext passwords in SQL and never logs credentials or hashes.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserDao userDao;
    private final LoginThrottle loginThrottle;

    public AuthService(UserDao userDao, LoginThrottle loginThrottle) {
        this.userDao = userDao != null ? userDao : new UserDao();
        this.loginThrottle = loginThrottle != null ? loginThrottle : new LoginThrottle();
    }

    public AuthService() {
        this(new UserDao(), new LoginThrottle());
    }

    /**
     * Authenticates a user using their email and raw password.
     * Enforces login throttling, BCrypt password hash verification, and account status checks.
     *
     * @param email             user email address
     * @param plaintextPassword raw password entered by the user
     * @return safe {@link AuthResult} holding the authenticated {@link CurrentUser}
     * @throws AuthenticationException if credentials are invalid, account is locked, or suspended
     * @throws ValidationException     if inputs are missing or blank
     */
    public AuthResult login(String email, String plaintextPassword) {
        if (email == null || email.isBlank() || plaintextPassword == null || plaintextPassword.isBlank()) {
            throw new ValidationException("Email and password are required.");
        }

        String normalizedEmail = email.trim().toLowerCase();

        // 1. Enforce sliding window lockout
        if (loginThrottle.isLocked(normalizedEmail)) {
            long remaining = loginThrottle.getRemainingLockMinutes(normalizedEmail);
            log.warn("Login blocked by rate limit for user [{}]", maskEmail(normalizedEmail));
            throw new AuthenticationException(
                "Too many failed login attempts. Please try again in " + remaining + " minute(s)."
            );
        }

        // 2. Lookup user record
        Optional<User> userOpt = userDao.findByEmail(normalizedEmail);
        if (userOpt.isEmpty() && isDemoAccount(normalizedEmail)) {
            userDao.ensureDemoAccounts();
            userOpt = userDao.findByEmail(normalizedEmail);
        }

        // 3. Verify BCrypt password hash
        if (userOpt.isEmpty() || !PasswordHasher.verify(plaintextPassword, userOpt.get().passwordHash())) {
            loginThrottle.recordFailure(normalizedEmail);
            log.warn("Authentication failed for user [{}]", maskEmail(normalizedEmail));
            // Uniform generic error prevents email enumeration
            throw new AuthenticationException("Invalid email or password.");
        }

        User user = userOpt.get();

        // 4. Verify account active status
        if (user.accountStatus() == AccountStatus.SUSPENDED) {
            log.warn("Login rejected for suspended user [{}]", maskEmail(normalizedEmail));
            throw new AuthenticationException(
                "Your account is currently suspended. Please contact the hostel administrator."
            );
        }

        // 5. Successful login: clear failure tracker and establish active session
        loginThrottle.recordSuccess(normalizedEmail);
        CurrentUser currentUser = CurrentUser.fromUser(user);
        Session.login(currentUser);

        log.info("User [{}] authenticated successfully with role [{}]", maskEmail(normalizedEmail), currentUser.role());
        return AuthResult.success(currentUser);
    }

    /**
     * Terminates the current session.
     */
    public void logout() {
        Optional<CurrentUser> current = Session.getCurrentUser();
        current.ifPresent(u -> log.info("User [{}] logged out", maskEmail(u.email())));
        Session.logout();
    }

    /**
     * Returns the currently active session user, if present and not expired.
     */
    public Optional<CurrentUser> getCurrentUser() {
        return Session.getCurrentUser();
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String domain = email.substring(at);
        String masked = name.length() <= 2 ? name.charAt(0) + "*" : name.substring(0, 2) + "***";
        return masked + domain;
    }

    private static boolean isDemoAccount(String email) {
        if (email == null) return false;
        String lower = email.toLowerCase().trim();
        return lower.endsWith("@student.staysync") || lower.endsWith("@staysync.internal");
    }
}
