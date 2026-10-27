package com.staysync.security;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Utility for BCrypt password hashing and constant-time password verification.
 * Built on the pinned at.favre.lib:bcrypt dependency.
 * Never logs credentials, plaintext passwords, or raw hashes.
 */
public final class PasswordHasher {

    private static final int DEFAULT_COST = 10;

    private PasswordHasher() {
        // Utility class
    }

    /**
     * Hashes a plaintext password using BCrypt with default cost factor (10).
     *
     * @param plaintextPassword the raw password to hash
     * @return standard BCrypt formatted hash string
     */
    public static String hash(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be null or blank");
        }
        return BCrypt.withDefaults().hashToString(DEFAULT_COST, plaintextPassword.toCharArray());
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     *
     * @param plaintextPassword the candidate raw password
     * @param storedHash        the stored hash from the database
     * @return true if candidate password matches stored hash; false otherwise
     */
    public static boolean verify(String plaintextPassword, String storedHash) {
        if (plaintextPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            BCrypt.Result result = BCrypt.verifyer().verify(plaintextPassword.toCharArray(), storedHash.toCharArray());
            return result != null && result.verified;
        } catch (Exception e) {
            return false;
        }
    }
}
