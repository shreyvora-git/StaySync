package com.staysync.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing stored file attachment metadata for a complaint.
 * Maps to MySQL table 'complaint_attachments'.
 */
public record Attachment(
    long attachmentId,
    long complaintId,
    long uploadedBy,
    String fileName,
    String filePath,
    String mimeType,
    Instant uploadedAt,
    String uploaderName
) {
    public Attachment {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(mimeType, "mimeType must not be null");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    }

    public Attachment(
        long attachmentId,
        long complaintId,
        long uploadedBy,
        String fileName,
        String filePath,
        String mimeType,
        Instant uploadedAt
    ) {
        this(attachmentId, complaintId, uploadedBy, fileName, filePath, mimeType, uploadedAt, null);
    }
}
