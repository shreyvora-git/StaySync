package com.staysync.security;

import com.staysync.model.Role;
import com.staysync.model.User;

import java.util.Objects;

/**
 * Immutable session context record holding minimal authenticated user details
 * necessary for role enforcement and authorization.
 * Excludes password hashes and sensitive authentication secrets.
 */
public record CurrentUser(
    long userId,
    String fullName,
    String email,
    Role role,
    boolean canViewConfidential
) {
    public CurrentUser {
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    public static CurrentUser fromUser(User user) {
        Objects.requireNonNull(user, "User entity must not be null");
        return new CurrentUser(
            user.userId(),
            user.fullName(),
            user.email(),
            user.role(),
            user.canViewConfidential()
        );
    }

    public boolean isStudent() {
        return role == Role.STUDENT;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isAuthorizedAdmin() {
        return role == Role.ADMIN && canViewConfidential;
    }
}
