package com.staysync.model;

/**
 * Complaint life-cycle statuses defined in complaints.status.
 */
public enum Status {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved");

    private final String dbValue;

    Status(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static Status fromDb(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (Status status : values()) {
            if (status.dbValue.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status value in database: " + value);
    }
}
