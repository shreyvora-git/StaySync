package com.staysync.model;

/**
 * User account status defined in users.account_status.
 */
public enum AccountStatus {
    ACTIVE("Active"),
    SUSPENDED("Suspended");

    private final String dbValue;

    AccountStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static AccountStatus fromDb(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (AccountStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown account status value in database: " + value);
    }
}
