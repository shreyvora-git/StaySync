package com.staysync.error;

/**
 * Thrown when an operation conflicts with the current entity state, such as an
 * invalid complaint lifecycle status transition or an optimistic locking collision (updated_at mismatch).
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
