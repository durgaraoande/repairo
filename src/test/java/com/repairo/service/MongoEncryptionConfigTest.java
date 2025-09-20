package com.repairo.service;

import com.repairo.config.MongoEncryptionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoEncryptionConfigTest {

    private MongoEncryptionConfig encryptionConfig;

    @BeforeEach
    void setUp() {
        encryptionConfig = new MongoEncryptionConfig();
        // Set a test encryption key using reflection
        ReflectionTestUtils.setField(encryptionConfig, "encryptionKey", "testKey123456789012345678901234"); // 32 chars for AES
    }

    @Test
    void testEncryptDecrypt() {
        String plaintext = "SensitiveData";
        String encrypted = encryptionConfig.encrypt(plaintext);
        String decrypted = encryptionConfig.decrypt(encrypted);

        assertEquals(plaintext, decrypted, "Decrypted value should match the original plaintext");
    }
}
