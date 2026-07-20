package com.cacanode.api.tenant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class IntegrationSecretCryptoService {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.integrations.secret-encryption-key:development-integration-secret-encryption-key}")
    private String encryptionKey;

    @Value("${app.integrations.legacy-secret-encryption-key:}")
    private String legacyEncryptionKey;

    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt integration secret", exception);
        }
    }

    public String decrypt(String value) {
        return decryptForMigration(value).value();
    }

    public DecryptedSecret decryptForMigration(String value) {
        try {
            return new DecryptedSecret(decrypt(value, encryptionKey), false);
        } catch (Exception currentKeyException) {
            if (legacyEncryptionKey == null || legacyEncryptionKey.isBlank()
                    || legacyEncryptionKey.equals(encryptionKey)) {
                throw new IllegalStateException("Unable to decrypt integration secret", currentKeyException);
            }
            try {
                return new DecryptedSecret(decrypt(value, legacyEncryptionKey), true);
            } catch (Exception legacyKeyException) {
                currentKeyException.addSuppressed(legacyKeyException);
                throw new IllegalStateException("Unable to decrypt integration secret", currentKeyException);
            }
        }
    }

    private String decrypt(String value, String configuredKey) throws Exception {
        byte[] combined = Base64.getDecoder().decode(value);
        byte[] nonce = Arrays.copyOfRange(combined, 0, 12);
        byte[] encrypted = Arrays.copyOfRange(combined, 12, combined.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(configuredKey), new GCMParameterSpec(128, nonce));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKeySpec key() throws Exception {
        return key(encryptionKey);
    }

    private SecretKeySpec key(String configuredKey) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    public record DecryptedSecret(String value, boolean requiresReencryption) {
    }
}
