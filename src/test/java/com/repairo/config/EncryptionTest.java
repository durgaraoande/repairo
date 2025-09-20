package com.repairo.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class EncryptionTest {

    private MongoEncryptionConfig config;
    
    @BeforeEach
    public void setUp() {
        config = new MongoEncryptionConfig();
        // Manually set the encryption key for testing
        ReflectionTestUtils.setField(config, "encryptionKey", "defaultDevKey1234567890");
    }

    @Test
    public void testEncryptionDecryption() {
        // Test normal text
        String originalText = "Hello World";
        String encrypted = config.encrypt(originalText);
        String decrypted = config.decrypt(encrypted);
        
        System.out.println("Original: " + originalText);
        System.out.println("Encrypted: " + encrypted);
        System.out.println("Decrypted: " + decrypted);
        
        assertEquals(originalText, decrypted);
    }

    @Test
    public void testPhoneNumberEncryption() {
        String phoneNumber = "919959441469";
        String encrypted = config.encrypt(phoneNumber);
        String decrypted = config.decrypt(encrypted);
        
        System.out.println("Phone Original: " + phoneNumber);
        System.out.println("Phone Encrypted: " + encrypted);
        System.out.println("Phone Decrypted: " + decrypted);
        
        assertEquals(phoneNumber, decrypted);
    }
    
    @Test
    public void testEmptyAndNullValues() {
        // Test null
        assertNull(config.encrypt(null));
        assertNull(config.decrypt(null));
        
        // Test empty
        assertEquals("", config.encrypt(""));
        assertEquals("", config.decrypt(""));
    }
    
    @Test
    public void testDecryptionOfInvalidData() {
        // Test decryption of non-encrypted data (should not fail)
        String plainText = "This is not encrypted";
        String result = config.decrypt(plainText);
        
        // Should return the original text since it's not Base64 encoded
        assertEquals(plainText, result);
    }
    
    @Test
    public void testSelectiveEncryption() {
        // Test that sensitive fields are encrypted
        String phoneNumber = "919959441469";
        String issueDescription = "Screen cracked";
        String messageText = "Hello, I need help";
        String customerName = "John Doe";
        String phoneModel = "iPhone 12";
        
        // These should be encrypted
        String encryptedPhone = config.encryptSensitiveField(phoneNumber, "phone");
        String encryptedIssue = config.encryptSensitiveField(issueDescription, "issue");
        String encryptedMessage = config.encryptSensitiveField(messageText, "message");
        
        // These should remain plain text
        String plainName = config.encryptSensitiveField(customerName, "name");
        String plainModel = config.encryptSensitiveField(phoneModel, "phoneModel");
        
        // Verify sensitive fields are encrypted (changed)
        assertNotEquals(phoneNumber, encryptedPhone);
        assertNotEquals(issueDescription, encryptedIssue);
        assertNotEquals(messageText, encryptedMessage);
        
        // Verify non-sensitive fields remain plain text
        assertEquals(customerName, plainName);
        assertEquals(phoneModel, plainModel);
        
        // Verify decryption works for encrypted fields
        assertEquals(phoneNumber, config.decryptSensitiveField(encryptedPhone, "phone"));
        assertEquals(issueDescription, config.decryptSensitiveField(encryptedIssue, "issue"));
        assertEquals(messageText, config.decryptSensitiveField(encryptedMessage, "message"));
        
        // Verify decryption doesn't change non-sensitive fields
        assertEquals(customerName, config.decryptSensitiveField(plainName, "name"));
        assertEquals(phoneModel, config.decryptSensitiveField(plainModel, "phoneModel"));
        
        System.out.println("=== Selective Encryption Test Results ===");
        System.out.println("Phone (sensitive): " + phoneNumber + " -> " + encryptedPhone);
        System.out.println("Issue (sensitive): " + issueDescription + " -> " + encryptedIssue);
        System.out.println("Message (sensitive): " + messageText + " -> " + encryptedMessage);
        System.out.println("Name (plain): " + customerName + " -> " + plainName);
        System.out.println("Model (plain): " + phoneModel + " -> " + plainModel);
    }
    
    @Test
    public void testSafeDecryptionWithMixedData() {
        // Test safe decryption with a mix of encrypted and plain text data
        String plainText = "John Doe";
        String encryptedText = config.encrypt("Secret Message");
        
        // Safe decryption should handle both cases
        String result1 = config.safeDecrypt(plainText);
        String result2 = config.safeDecrypt(encryptedText);
        
        assertEquals(plainText, result1); // Plain text should remain unchanged
        assertEquals("Secret Message", result2); // Encrypted text should be decrypted
        
        System.out.println("Safe decrypt plain text: " + plainText + " -> " + result1);
        System.out.println("Safe decrypt encrypted: " + encryptedText + " -> " + result2);
    }
}