package com.staysync.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing a comment or internal note on a complaint.
 * Maps to MySQL table 'complaint_comments'.
 */
public record Comment(
    long commentId,
    long complaintId,
    long userId,
    String commentText,
    boolean isInternal,
    Instant createdAt,
    String authorName,
    Role authorRole
) {
    public Comment {
        Objects.requireNonNull(commentText, "commentText must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public Comment(
        long commentId,
        long complaintId,
        long userId,
        String commentText,
        boolean isInternal,
        Instant createdAt
    ) {
        this(commentId, complaintId, userId, commentText, isInternal, createdAt, null, null);
    }
}
