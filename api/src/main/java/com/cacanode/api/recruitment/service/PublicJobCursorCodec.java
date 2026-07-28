package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicJobCursorCodec {
    private static final byte[] AAD = "cacanode-public-jobs-v1".getBytes(StandardCharsets.UTF_8);
    private final PublicRecruitmentProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public String encode(String sort, String value, UUID publicId, String fingerprint) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            var payload = new Cursor(sort, value, publicId, Instant.now(clock).plusSeconds(3600).getEpochSecond(), fingerprint);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(objectMapper.writeValueAsBytes(payload));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode public-job cursor", exception);
        }
    }

    public Cursor decode(String token, String expectedSort, String expectedFingerprint) {
        try {
            byte[] packed = Base64.getUrlDecoder().decode(token);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(packed).equals(token)) throw invalid();
            if (packed.length < 29) throw invalid();
            byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, 12, packed.length);
            Cursor cursor = objectMapper.readValue(cipher(Cipher.DECRYPT_MODE, nonce).doFinal(encrypted), Cursor.class);
            if (!expectedSort.equals(cursor.sort()) || !expectedFingerprint.equals(cursor.fingerprint())
                    || cursor.expiresAt() < Instant.now(clock).getEpochSecond()) throw invalid();
            return cursor;
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    public String fingerprint(Map<String, ?> filters) {
        try {
            var canonical = new TreeMap<String, Object>();
            filters.forEach((key, value) -> { if (value != null && !value.toString().isBlank()) canonical.put(key, value); });
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(canonical)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint public-job filters", exception);
        }
    }

    private Cipher cipher(int mode, byte[] nonce) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256")
                .digest(properties.cursorEncryptionKey().getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        return cipher;
    }

    private static BadRequestException invalid() { return new BadRequestException("INVALID_CURSOR"); }

    public record Cursor(String sort, String value, UUID publicId, long expiresAt, String fingerprint) {}
}
