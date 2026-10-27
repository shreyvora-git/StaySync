package com.staysync.model;

/**
 * Optional student urgency hint provided at complaint submission (complaints.student_urgency).
 */
public enum Urgency {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String dbValue;

    Urgency(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static Urgency fromDb(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (Urgency urgency : values()) {
            if (urgency.dbValue.equalsIgnoreCase(value.trim())) {
                return urgency;
            }
        }
        return null;
    }
}
