package com.staysync.error;

/**
 * Thrown when an authenticated user does not have the necessary role or permission
 * to perform a requested operation.
 */
public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
