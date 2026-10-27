package com.staysync.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing an audit trail log entry for status/priority transitions.
 * Maps to MySQL table 'complaint_history'.
 */
public record HistoryEntry(
    long historyId,
    long complaintId,
    long changedBy,
    Status oldStatus,
    Status newStatus,
    Priority oldPriority,
    Priority newPriority,
    String changeNote,
    Instant changedAt,
    String actorName,
    Role actorRole
) {
    public HistoryEntry {
        Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public HistoryEntry(
        long historyId,
        long complaintId,
        long changedBy,
        Status oldStatus,
        Status newStatus,
        Priority oldPriority,
        Priority newPriority,
        String changeNote,
        Instant changedAt
    ) {
        this(historyId, complaintId, changedBy, oldStatus, newStatus, oldPriority, newPriority, changeNote, changedAt, null, null);
    }
}
