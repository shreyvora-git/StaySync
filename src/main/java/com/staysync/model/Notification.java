package com.staysync.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing an in-app alert notification.
 * Maps directly to MySQL table 'notifications'.
 */
public record Notification(
    long notificationId,
    long userId,
    Long complaintId,
    String message,
    boolean isRead,
    Instant createdAt
) {
    public Notification {
        Objects.requireNonNull(message, "message must not be null");
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Notification(long userId, Long complaintId, String message) {
        this(0L, userId, complaintId, message, false, Instant.now());
    }
}
