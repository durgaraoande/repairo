package com.repairo.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class MongoEncryptionConfigTest {

    private MongoEncryptionConfig encryptionConfig;

    @BeforeEach
    void setUp() {
        encryptionConfig = new MongoEncryptionConfig();
        ReflectionTestUtils.setField(encryptionConfig, "encryptionKey", "test-encryption-key-32-chars");
    }

    @Test
    void testEncryptDecrypt_ValidText() {
        // Given
        String plaintext = "This is a test message";

        // When
        String encrypted = encryptionConfig.encrypt(plaintext);
        String decrypted = encryptionConfig.decrypt(encrypted);

        // Then
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptDecrypt_EmptyString() {
        // Given
        String plaintext = "";

        // When
        String encrypted = encryptionConfig.encrypt(plaintext);
        String decrypted = encryptionConfig.decrypt(encrypted);

        // Then
        assertEquals(plaintext, encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptDecrypt_NullString() {
        // Given
        String plaintext = null;

        // When
        String encrypted = encryptionConfig.encrypt(plaintext);
        String decrypted = encryptionConfig.decrypt(encrypted);

        // Then
        assertEquals(plaintext, encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptDecrypt_SpecialCharacters() {
        // Given
        String plaintext = "Hello! @#$%^&*()_+{}|:<>?[]\\;',./`~";

        // When
        String encrypted = encryptionConfig.encrypt(plaintext);
        String decrypted = encryptionConfig.decrypt(encrypted);

        // Then
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptDecrypt_UnicodeCharacters() {
        // Given
        String plaintext = "Hello 世界 🌍 émojis";

        // When
        String encrypted = encryptionConfig.encrypt(plaintext);
        String decrypted = encryptionConfig.decrypt(encrypted);

        // Then
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptDecrypt_LongText() {
        // Given
        String plaintext = "This is a very long text message that should be properly encrypted and decrypted without any issues. ".repeat(10);

        // When
        String encrypted = encryptionConfig.encrypt(plaintext);
        String decrypted = encryptionConfig.decrypt(encrypted);

        // Then
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncrypt_DifferentResults() {
        // Given
        String plaintext = "Same message";

        // When
        String encrypted1 = encryptionConfig.encrypt(plaintext);
        String encrypted2 = encryptionConfig.encrypt(plaintext);

        // Then - Due to no IV, results should be the same (not ideal for production)
        assertNotNull(encrypted1);
        assertNotNull(encrypted2);
        assertEquals(encrypted1, encrypted2);
    }
}
