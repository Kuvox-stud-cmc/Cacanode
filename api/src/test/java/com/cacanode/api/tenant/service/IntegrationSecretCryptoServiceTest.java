package com.cacanode.api.tenant.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IntegrationSecretCryptoServiceTest {
    @Test
    void encryptsWithAuthenticatedEncryptionAndDecrypts() {
        IntegrationSecretCryptoService service = new IntegrationSecretCryptoService();
        ReflectionTestUtils.setField(service, "encryptionKey", "test-integration-encryption-key");

        String encrypted = service.encrypt("ccn_it_browser_secret");

        assertNotEquals("ccn_it_browser_secret", encrypted);
        assertEquals("ccn_it_browser_secret", service.decrypt(encrypted));
    }

    @Test
    void decryptsWithLegacyKeyAndMarksSecretForReencryption() {
        IntegrationSecretCryptoService legacyService = new IntegrationSecretCryptoService();
        ReflectionTestUtils.setField(legacyService, "encryptionKey", "legacy-integration-encryption-key");

        IntegrationSecretCryptoService currentService = new IntegrationSecretCryptoService();
        ReflectionTestUtils.setField(currentService, "encryptionKey", "current-integration-encryption-key");
        ReflectionTestUtils.setField(currentService, "legacyEncryptionKey", "legacy-integration-encryption-key");

        String encrypted = legacyService.encrypt("ccn_it_legacy_browser_secret");
        var decrypted = currentService.decryptForMigration(encrypted);

        assertEquals("ccn_it_legacy_browser_secret", decrypted.value());
        assertTrue(decrypted.requiresReencryption());

        String migrated = currentService.encrypt(decrypted.value());
        var current = currentService.decryptForMigration(migrated);
        assertEquals("ccn_it_legacy_browser_secret", current.value());
        assertFalse(current.requiresReencryption());
    }
}
