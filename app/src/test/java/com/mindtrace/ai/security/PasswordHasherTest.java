package com.mindtrace.ai.security;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link PasswordHasher}.
 *
 * <p>Validates the security-critical password hashing pipeline:
 * hash generation, verification, salt uniqueness, legacy support,
 * and edge case handling.</p>
 */
public class PasswordHasherTest {

    // ═══════════════════════════════════════════════════════════════════
    // HASH GENERATION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void hash_producesNonNullResult() {
        String result = PasswordHasher.hash("password123");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void hash_containsSaltSeparator() {
        String result = PasswordHasher.hash("mySecretPassword");
        assertTrue("Hash should contain ':' separator between salt and hash",
                result.contains(":"));
    }

    @Test
    public void hash_hasTwoParts() {
        String result = PasswordHasher.hash("test");
        String[] parts = result.split(":", 2);
        assertEquals("Hash should have exactly 2 parts (salt:hash)", 2, parts.length);
        assertFalse("Salt part should not be empty", parts[0].isEmpty());
        assertFalse("Hash part should not be empty", parts[1].isEmpty());
    }

    @Test
    public void hash_uniqueSalts_produceDifferentHashes() {
        String hash1 = PasswordHasher.hash("samePassword");
        String hash2 = PasswordHasher.hash("samePassword");
        assertNotEquals("Two hashes of the same password should differ (unique salts)",
                hash1, hash2);
    }

    // ═══════════════════════════════════════════════════════════════════
    // VERIFICATION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void verify_correctPassword_returnsTrue() {
        String password = "correctHorse!battery42";
        String hashed = PasswordHasher.hash(password);
        assertTrue("Correct password should verify", PasswordHasher.verify(password, hashed));
    }

    @Test
    public void verify_wrongPassword_returnsFalse() {
        String hashed = PasswordHasher.hash("realPassword");
        assertFalse("Wrong password should fail verification",
                PasswordHasher.verify("wrongPassword", hashed));
    }

    @Test
    public void verify_emptyPassword_hashAndVerify() {
        String hashed = PasswordHasher.hash("");
        assertTrue("Empty password should verify against its own hash",
                PasswordHasher.verify("", hashed));
        assertFalse("Non-empty should not match empty hash",
                PasswordHasher.verify("something", hashed));
    }

    @Test
    public void verify_unicodePassword() {
        String password = "пароль密码パスワード";
        String hashed = PasswordHasher.hash(password);
        assertTrue("Unicode password should verify", PasswordHasher.verify(password, hashed));
    }

    @Test
    public void verify_longPassword() {
        String password = "a".repeat(1000);
        String hashed = PasswordHasher.hash(password);
        assertTrue("Long password should verify", PasswordHasher.verify(password, hashed));
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES & SAFETY
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void verify_nullPlaintext_returnsFalse() {
        assertFalse(PasswordHasher.verify(null, "salt:hash"));
    }

    @Test
    public void verify_nullStoredHash_returnsFalse() {
        assertFalse(PasswordHasher.verify("password", null));
    }

    @Test
    public void verify_bothNull_returnsFalse() {
        assertFalse(PasswordHasher.verify(null, null));
    }

    @Test
    public void verify_corruptedHash_returnsFalse() {
        assertFalse("Corrupted hash should fail gracefully",
                PasswordHasher.verify("password", "not_valid_base64:also_not_valid"));
    }

    @Test
    public void verify_emptyString_returnsFalse() {
        assertFalse(PasswordHasher.verify("password", ""));
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEGACY PLAINTEXT SUPPORT
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void verify_legacyPlaintext_matchesDirectly() {
        // Pre-migration passwords stored as plaintext (no ':' separator)
        assertTrue("Legacy plaintext should match via direct comparison",
                PasswordHasher.verify("oldPassword", "oldPassword"));
    }

    @Test
    public void verify_legacyPlaintext_wrongPassword() {
        assertFalse(PasswordHasher.verify("wrong", "oldPassword"));
    }

    @Test
    public void isLegacyPlaintext_true_forNoColon() {
        assertTrue(PasswordHasher.isLegacyPlaintext("plainTextPassword"));
    }

    @Test
    public void isLegacyPlaintext_false_forHashedFormat() {
        String hashed = PasswordHasher.hash("test");
        assertFalse(PasswordHasher.isLegacyPlaintext(hashed));
    }

    @Test
    public void isLegacyPlaintext_false_forNull() {
        assertFalse(PasswordHasher.isLegacyPlaintext(null));
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSISTENCY
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void verify_multipleVerifications_consistent() {
        String password = "consistencyTest!";
        String hashed = PasswordHasher.hash(password);
        // Verify multiple times to ensure no state mutation
        for (int i = 0; i < 10; i++) {
            assertTrue("Verification #" + i + " should pass",
                    PasswordHasher.verify(password, hashed));
        }
    }

    @Test
    public void hash_specialCharacters() {
        String password = "p@$$w0rd!#%^&*()_+-=[]{}|;':\",./<>?";
        String hashed = PasswordHasher.hash(password);
        assertTrue("Special characters should hash and verify correctly",
                PasswordHasher.verify(password, hashed));
    }
}
