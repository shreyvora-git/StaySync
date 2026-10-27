package com.staysync.security;

import com.staysync.config.AppConfig;
import com.staysync.error.AppException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographic utility for securing confidential attachments at rest.
 * Uses AES-256-GCM with a fresh, cryptographically secure 12-byte nonce per operation
 * and 128-bit authentication tags.
 *
 * <p>Never falls back to unencrypted storage. Decryption authenticates data integrity
 * and rejects altered or corrupt payloads.
 */
public final class FileCrypto {

    public static final String ALGORITHM = "AES/GCM/NoPadding";
    public static final int IV_LENGTH_BYTES = 12; // 96-bit nonce per NIST SP 800-38D
    public static final int TAG_LENGTH_BITS = 128; // 16-byte authentication tag
    public static final int KEY_LENGTH_BYTES = 32; // 256-bit key

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private FileCrypto() {
        // Utility class
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a random 12-byte IV.
     * Output format: [12-byte IV] + [ciphertext with 16-byte GCM authentication tag].
     *
     * @param plaintext raw bytes to encrypt
     * @return combined IV + authenticated ciphertext
     */
    public static byte[] encrypt(byte[] plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext bytes must not be null");
        }
        byte[] keyBytes = loadKeyBytes();

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return buffer.array();
        } catch (Exception e) {
            throw new AppException("Attachment encryption failed", e);
        }
    }

    /**
     * Decrypts and authenticates an AES-256-GCM encrypted payload.
     * Expects input format: [12-byte IV] + [ciphertext with 16-byte GCM authentication tag].
     *
     * @param encryptedPayload combined IV and ciphertext
     * @return original plaintext bytes
     * @throws AppException if ciphertext is tampered, corrupted, or key is invalid
     */
    public static byte[] decrypt(byte[] encryptedPayload) {
        if (encryptedPayload == null || encryptedPayload.length < (IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8))) {
            throw new AppException("Invalid encrypted attachment: payload too short or malformed");
        }
        byte[] keyBytes = loadKeyBytes();

        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedPayload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new AppException("Attachment decryption failed: data may be corrupted or key invalid", e);
        }
    }

    /**
     * Tests whether an encryption key is present in configuration and satisfies AES-256 length requirements.
     */
    public static boolean isKeyConfigured() {
        try {
            loadKeyBytes();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] loadKeyBytes() {
        String base64Key = AppConfig.load().getFilesKey();
        if (base64Key == null || base64Key.isBlank()) {
            throw new AppException("Attachment encryption key 'files.key' is not configured in app.properties");
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
            if (keyBytes.length != KEY_LENGTH_BYTES) {
                throw new AppException("Invalid encryption key length: expected 32 bytes (256-bit AES), found " + keyBytes.length);
            }
            return keyBytes;
        } catch (IllegalArgumentException e) {
            throw new AppException("Failed to decode Base64 encryption key 'files.key'", e);
        }
    }
}
