package com.staysync.model;

/**
 * Triage priority levels defined in complaints.priority.
 * Can be null when a complaint is untriaged.
 */
public enum Priority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String dbValue;

    Priority(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static Priority fromDb(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (Priority priority : values()) {
            if (priority.dbValue.equalsIgnoreCase(value.trim())) {
                return priority;
            }
        }
        return null;
    }
}
