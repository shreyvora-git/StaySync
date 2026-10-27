package com.staysync.security;

import com.staysync.config.AppConfig;
import com.staysync.error.AuthenticationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe, in-memory session context for the authenticated user in the StaySync desktop application.
 *
 * <p><strong>Single-Session Desktop Model:</strong>
 * StaySync operates as a dedicated desktop client where one authenticated session is active at a time.
 * Multi-user concurrency within a single JVM process is not supported; logging in establishes the active
 * session and replaces any prior context.
 *
 * <p>Idle timeout is enforced via {@code session.idleMinutes}. Expired sessions are automatically
 * invalidated upon subsequent access.
 */
public final class Session {

    private static final Object LOCK = new Object();
    private static volatile CurrentUser activeUser;
    private static volatile Instant lastAccessedAt;
    private static volatile Duration idleTimeout = Duration.ofMinutes(
        Math.max(1, AppConfig.load().getSessionIdleMinutes())
    );

    private Session() {
        // Static context
    }

    /**
     * Establishes a new authenticated session.
     *
     * @param user the authenticated user context
     */
    public static void login(CurrentUser user) {
        Objects.requireNonNull(user, "CurrentUser must not be null");
        synchronized (LOCK) {
            activeUser = user;
            lastAccessedAt = Instant.now();
        }
    }

    /**
     * Terminates the current session and invalidates all cached authentication context.
     */
    public static void logout() {
        synchronized (LOCK) {
            activeUser = null;
            lastAccessedAt = null;
        }
    }

    /**
     * Returns the active user if a valid, unexpired session exists.
     * Automatically invalidates the session if the idle timeout has elapsed.
     */
    public static Optional<CurrentUser> getCurrentUser() {
        return getCurrentUser(Instant.now());
    }

    public static Optional<CurrentUser> getCurrentUser(Instant now) {
        synchronized (LOCK) {
            if (activeUser == null) {
                return Optional.empty();
            }

            if (isExpired(now)) {
                logout();
                return Optional.empty();
            }

            lastAccessedAt = now;
            return Optional.of(activeUser);
        }
    }

    /**
     * Returns the active user, or throws {@link AuthenticationException} if there is
     * no active session or the session has expired.
     */
    public static CurrentUser requireCurrentUser() {
        return getCurrentUser().orElseThrow(() ->
            new AuthenticationException("Authentication required: No active session or session has expired.")
        );
    }

    /**
     * Returns true if a valid, non-expired session is currently active.
     */
    public static boolean isLoggedIn() {
        return getCurrentUser().isPresent();
    }

    /**
     * Refreshes the session activity timestamp.
     */
    public static void touch() {
        touch(Instant.now());
    }

    public static void touch(Instant now) {
        synchronized (LOCK) {
            if (activeUser != null && !isExpired(now)) {
                lastAccessedAt = now;
            }
        }
    }

    public static boolean isExpired(Instant now) {
        synchronized (LOCK) {
            if (activeUser == null || lastAccessedAt == null) {
                return true;
            }
            Duration elapsed = Duration.between(lastAccessedAt, now);
            return elapsed.compareTo(idleTimeout) > 0;
        }
    }

    /**
     * Reconfigures the idle timeout duration (useful for automated testing or runtime preference updates).
     */
    public static void setIdleTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "Timeout must not be null");
        synchronized (LOCK) {
            idleTimeout = timeout;
        }
    }

    /**
     * Clears all session state and restores default configuration.
     */
    public static void clear() {
        synchronized (LOCK) {
            activeUser = null;
            lastAccessedAt = null;
            idleTimeout = Duration.ofMinutes(Math.max(1, AppConfig.load().getSessionIdleMinutes()));
        }
    }
}
