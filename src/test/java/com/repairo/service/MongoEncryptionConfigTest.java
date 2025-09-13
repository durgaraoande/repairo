package com.repairo.service;

import com.repairo.config.MongoEncryptionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoEncryptionConfigTest {

    private final MongoEncryptionConfig encryptionConfig = new MongoEncryptionConfig();

    @Test
    void testEncryptDecrypt() {
        String plaintext = "SensitiveData";
        String encrypted = encryptionConfig.encrypt(plaintext);
        String decrypted = encryptionConfig.decrypt(encrypted);

        assertEquals(plaintext, decrypted, "Decrypted value should match the original plaintext");
    }
}
