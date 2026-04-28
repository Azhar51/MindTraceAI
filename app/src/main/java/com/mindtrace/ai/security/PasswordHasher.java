package com.mindtrace.ai.security;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Password hashing utility using SHA-256 with per-user salt.
 *
 * <p>This replaces the plaintext password storage that existed previously.
 * While bcrypt would be ideal, it requires a native library; SHA-256 + salt
 * is a significant improvement over plaintext and works with pure Java.</p>
 *
 * <h3>Storage format:</h3>
 * <pre>  base64(salt) + ":" + base64(SHA-256(salt + password)) </pre>
 *
 * @see com.mindtrace.ai.database.entity.User
 */
public final class PasswordHasher {

    private static final int SALT_BYTES = 16;

    private PasswordHasher() { /* utility class */ }

    /**
     * Hash a plaintext password with a fresh random salt.
     *
     * @param plaintext the raw password
     * @return hashed password string in format "salt:hash"
     */
    public static String hash(String plaintext) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);
            byte[] hash = computeHash(salt, plaintext);
            return Base64.encodeToString(salt, Base64.NO_WRAP)
                    + ":"
                    + Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    /**
     * Verify a plaintext password against a stored hash.
     *
     * @param plaintext    the raw password to check
     * @param storedHash   the stored "salt:hash" string
     * @return true if the password matches
     */
    public static boolean verify(String plaintext, String storedHash) {
        if (plaintext == null || storedHash == null) return false;
        try {
            // Legacy plaintext support: if stored hash has no ":", it's a
            // pre-migration plaintext password — compare directly and signal
            // the caller should re-hash on next login.
            if (!storedHash.contains(":")) {
                return plaintext.equals(storedHash);
            }
            String[] parts = storedHash.split(":", 2);
            if (parts.length != 2) return false;
            byte[] salt = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] expectedHash = Base64.decode(parts[1], Base64.NO_WRAP);
            byte[] actualHash = computeHash(salt, plaintext);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a stored password is in the legacy plaintext format
     * (i.e., needs re-hashing on next login).
     */
    public static boolean isLegacyPlaintext(String storedHash) {
        return storedHash != null && !storedHash.contains(":");
    }

    private static byte[] computeHash(byte[] salt, String plaintext) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        return digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
    }
}
