package com.staysync.security;

import com.staysync.config.AppConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, sliding-window rate limiter tracking failed login attempts per email.
 *
 * <p><strong>Process Limitation Notice:</strong>
 * This throttle maintains its sliding window in memory for the local desktop application process.
 * In accordance with the desktop two-tier architecture, lockout state is process-bound and resets
 * on application restart.
 */
public class LoginThrottle {

    private final int maxAttempts;
    private final Duration windowDuration;
    private final Map<String, Deque<Instant>> attemptsByEmail = new ConcurrentHashMap<>();

    public LoginThrottle(int maxAttempts, int windowMinutes) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowDuration = Duration.ofMinutes(Math.max(1, windowMinutes));
    }

    public LoginThrottle() {
        this(
            AppConfig.load().getLoginMaxAttempts(),
            AppConfig.load().getLoginWindowMinutes()
        );
    }

    /**
     * Checks if the given email account is currently throttled / locked out.
     */
    public boolean isLocked(String email) {
        return isLocked(email, Instant.now());
    }

    public boolean isLocked(String email, Instant now) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String key = normalizeKey(email);
        Deque<Instant> timestamps = attemptsByEmail.get(key);
        if (timestamps == null) {
            return false;
        }

        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            return timestamps.size() >= maxAttempts;
        }
    }

    /**
     * Records an authentication failure for the given email address.
     */
    public void recordFailure(String email) {
        recordFailure(email, Instant.now());
    }

    public void recordFailure(String email, Instant now) {
        if (email == null || email.isBlank()) {
            return;
        }
        String key = normalizeKey(email);
        Deque<Instant> timestamps = attemptsByEmail.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            timestamps.addLast(now);
        }
    }

    /**
     * Clears all failed attempts for the given email upon successful authentication.
     */
    public void recordSuccess(String email) {
        if (email != null && !email.isBlank()) {
            attemptsByEmail.remove(normalizeKey(email));
        }
    }

    /**
     * Returns the remaining lockout duration in minutes for an account that is throttled.
     * Returns 0 if not locked.
     */
    public long getRemainingLockMinutes(String email) {
        return getRemainingLockMinutes(email, Instant.now());
    }

    public long getRemainingLockMinutes(String email, Instant now) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        String key = normalizeKey(email);
        Deque<Instant> timestamps = attemptsByEmail.get(key);
        if (timestamps == null) {
            return 0L;
        }

        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            if (timestamps.size() < maxAttempts) {
                return 0L;
            }
            Instant oldest = timestamps.peekFirst();
            if (oldest == null) {
                return 0L;
            }
            Instant unlockTime = oldest.plus(windowDuration);
            long secondsRemaining = Duration.between(now, unlockTime).toSeconds();
            return Math.max(1L, (secondsRemaining + 59) / 60);
        }
    }

    /**
     * Resets all throttle records (primarily for testing or administrative reset).
     */
    public void reset() {
        attemptsByEmail.clear();
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }

    private void pruneExpired(Deque<Instant> timestamps, Instant now) {
        Instant threshold = now.minus(windowDuration);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(threshold)) {
            timestamps.pollFirst();
        }
    }

    private String normalizeKey(String email) {
        return email.trim().toLowerCase();
    }
}
