package com.staysync.error;

/**
 * Thrown when authentication fails (invalid credentials, suspended account,
 * rate limit lockout, or expired session).
 */
public class AuthenticationException extends AppException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
