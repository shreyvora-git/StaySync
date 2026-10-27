package com.staysync.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing a student complaint.
 * Maps to MySQL table 'complaints' and includes joined helper attributes
 * for student name, category name, and assigned administrator.
 */
public record Complaint(
    long complaintId,
    long studentId,
    long categoryId,
    String title,
    String description,
    String location,
    Urgency studentUrgency,
    Status status,
    Priority priority,
    boolean isConfidential,
    Long assignedAdminId,
    String resolutionNotes,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt,
    String studentName,
    String categoryName,
    String assignedAdminName
) {
    public Complaint {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Convenience constructor without joined display fields.
     */
    public Complaint(
        long complaintId,
        long studentId,
        long categoryId,
        String title,
        String description,
        String location,
        Urgency studentUrgency,
        Status status,
        Priority priority,
        boolean isConfidential,
        Long assignedAdminId,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
    ) {
        this(
            complaintId, studentId, categoryId, title, description, location,
            studentUrgency, status, priority, isConfidential, assignedAdminId,
            resolutionNotes, createdAt, updatedAt, resolvedAt,
            null, null, null
        );
    }
}
