package com.staysync.error;

/**
 * Thrown when a requested resource is not found, or when a confidential record
 * is deliberately masked from an unauthorized user to prevent existence enumeration.
 */
public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
