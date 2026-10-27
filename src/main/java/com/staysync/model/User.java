package com.staysync.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing a registered user (Student or Admin).
 * Maps to MySQL table 'users'.
 */
public record User(
    long userId,
    String fullName,
    String email,
    String passwordHash,
    Role role,
    AccountStatus accountStatus,
    boolean canViewConfidential,
    Instant createdAt
) {
    public User {
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(accountStatus, "accountStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isStudent() {
        return role == Role.STUDENT;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }
}
