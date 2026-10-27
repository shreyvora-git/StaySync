package com.staysync.error;

/**
 * Base application runtime exception for StaySync.
 * Wraps checked exceptions (such as SQLException) without exposing
 * internal SQL or credentials to higher layers.
 */
public class AppException extends RuntimeException {

    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }

    public AppException(Throwable cause) {
        super(cause);
    }

    /**
     * Wraps an arbitrary throwable into an AppException if it is not already one.
     */
    public static AppException wrap(Throwable t) {
        if (t instanceof AppException ae) {
            return ae;
        }
        String msg = (t.getMessage() != null && !t.getMessage().isBlank())
                ? t.getMessage()
                : "Database operation encountered an error";
        return new AppException(msg, t);
    }
}
