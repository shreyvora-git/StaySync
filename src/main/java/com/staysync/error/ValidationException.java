package com.staysync.error;

import java.util.Collections;
import java.util.Map;

/**
 * Thrown when business input validation fails (e.g. missing mandatory resolution notes,
 * empty reopen notes, or string length boundary violations).
 */
public class ValidationException extends AppException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(message);
        this.fieldErrors = Collections.emptyMap();
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors != null ? Map.copyOf(fieldErrors) : Collections.emptyMap();
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
