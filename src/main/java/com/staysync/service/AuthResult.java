package com.staysync.service;

import com.staysync.security.CurrentUser;

import java.util.Objects;

/**
 * Safe outcome record representing the result of an authentication operation.
 * Contains the authenticated CurrentUser without password hashes or credentials.
 */
public record AuthResult(
    CurrentUser user,
    boolean success,
    String message
) {
    public AuthResult {
        if (success) {
            Objects.requireNonNull(user, "User context must not be null on successful authentication");
        }
    }

    public static AuthResult success(CurrentUser user) {
        return new AuthResult(user, true, "Authentication successful");
    }

    public static AuthResult failure(String message) {
        return new AuthResult(null, false, message);
    }
}
