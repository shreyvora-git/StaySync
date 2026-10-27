package com.staysync.model;

/**
 * User roles defined in StaySync database schema (users.role).
 */
public enum Role {
    STUDENT("Student"),
    ADMIN("Admin");

    private final String dbValue;

    Role(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static Role fromDb(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (Role role : values()) {
            if (role.dbValue.equalsIgnoreCase(value.trim())) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role value in database: " + value);
    }
}
