package com.fuoverflow.coursera.support;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.coursera.config.CourseraProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CredentialEncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;
    private final String keyId;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialEncryptionService(CourseraProperties properties) {
        String rawKey = properties.credentials() != null ? properties.credentials().encryptionKey() : null;
        if (rawKey == null || rawKey.isBlank()) {
            throw new BadRequestException(
                    "COURSERA_ENCRYPTION_NOT_CONFIGURED",
                    "COURSERA_CREDENTIALS_ENCRYPTION_KEY must be set");
        }
        this.secretKey = deriveKey(rawKey);
        this.keyId = properties.credentials().keyId() != null && !properties.credentials().keyId().isBlank()
                ? properties.credentials().keyId()
                : "default";
    }

    public String keyId() {
        return keyId;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    public String decrypt(String ciphertextBase64) {
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertextBase64);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt credential", e);
        }
    }

    private static SecretKey deriveKey(String rawKey) {
        try {
            byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length == 32) {
                return new SecretKeySpec(keyBytes, "AES");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(keyBytes), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }
}
