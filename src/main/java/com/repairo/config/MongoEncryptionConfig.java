package com.repairo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class MongoEncryptionConfig {
    
    @Value("${app.encryption.key:defaultDevKey1234567890}")
    private String encryptionKey;
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";
    
    private SecretKey getSecretKey() {
        // In production, use a proper key management system
        byte[] key = encryptionKey.getBytes();
        // Ensure key is 16 bytes for AES-128
        byte[] keyBytes = new byte[16];
        System.arraycopy(key, 0, keyBytes, 0, Math.min(key.length, keyBytes.length));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
    
    public String encrypt(String plaintext) {
        try {
            if (plaintext == null || plaintext.isEmpty()) {
                return plaintext;
            }
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    public String decrypt(String encryptedText) {
        try {
            if (encryptedText == null || encryptedText.isEmpty()) {
                return encryptedText;
            }
            
            // Check if the text is already plain text (not Base64 encoded)
            if (!isBase64(encryptedText)) {
                // Might be already decrypted or corrupted data
                return encryptedText;
            }
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey());
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes);
        } catch (Exception e) {
            // Log the specific error and the problematic text for debugging
            System.err.println("Decryption failed for text: " + encryptedText);
            System.err.println("Error: " + e.getMessage());
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }
    
    private boolean isBase64(String str) {
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Safely decrypt text only if it appears to be encrypted (Base64 encoded)
     * Returns original text if it's not encrypted or if decryption fails
     */
    public String safeDecrypt(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return text;
            }
            
            // Only attempt decryption if the text looks like encrypted data (Base64)
            if (!isBase64(text.trim())) {
                // Not encrypted, return as-is
                return text;
            }
            
            return decrypt(text);
        } catch (Exception e) {
            // If decryption fails, log and return original text
            System.err.println("Safe decryption failed for text, returning original: " + e.getMessage());
            return text;
        }
    }
    
    /**
     * Decrypt only specific sensitive fields
     */
    public String decryptSensitiveField(String fieldValue, String fieldName) {
        if (isSensitiveField(fieldName)) {
            return safeDecrypt(fieldValue);
        }
        return fieldValue;
    }
    
    /**
     * Encrypt only specific sensitive fields
     */
    public String encryptSensitiveField(String fieldValue, String fieldName) {
        if (isSensitiveField(fieldName) && fieldValue != null && !fieldValue.trim().isEmpty()) {
            return encrypt(fieldValue);
        }
        return fieldValue;
    }
    
    /**
     * Check if a field should be encrypted
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;
        
        String lowerFieldName = fieldName.toLowerCase();
        return lowerFieldName.equals("phone") || 
               lowerFieldName.equals("issue") || 
               lowerFieldName.equals("message") ||
               lowerFieldName.equals("text"); // for message text
    }
}
