package com.cacanode.api.document.service;

import com.cacanode.api.ai.api.AiInferenceApi;
import com.cacanode.api.document.api.DocumentApi;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.document.dto.PublicEvidenceDtos;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentVisibility;
import com.cacanode.api.document.model.Document;
import com.cacanode.api.document.repository.DocumentRepository;
import com.cacanode.api.tenant.api.IntegrationAccessApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class PublicEvidenceService {
    private static final String SIGNATURE_PURPOSE = "cacanode-public-evidence-v1:";

    private final DocumentRepository documentRepository;
    private final IntegrationAccessApi integrationAccessApi;
    private final AiInferenceApi inferenceClient;
    private final ObjectMapper objectMapper;

    @Value("${app.public-evidence.signing-key:development-public-evidence-signing-key}")
    private String signingKey;

    @Value("${app.public-evidence.web-base-url:http://localhost:3000}")
    private String webBaseUrl;

    @Value("${app.public-evidence.ttl-seconds:3600}")
    private long ttlSeconds;

    public PublicEvidenceService(
            DocumentRepository documentRepository,
            IntegrationAccessApi integrationAccessApi,
            AiInferenceApi inferenceClient,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.integrationAccessApi = integrationAccessApi;
        this.inferenceClient = inferenceClient;
        this.objectMapper = objectMapper;
    }

    public String issue(
            UUID tenantId,
            UUID knowledgeBaseId,
            UUID integrationTokenId,
            DocumentApi.EvidenceCitation citation
    ) {
        String focus = citation.unitId() != null && !citation.unitId().isBlank()
                ? "unit:" + citation.unitId()
                : "chunk:" + citation.chunkIndex();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        EvidencePayload payload = new EvidencePayload(
                tenantId, knowledgeBaseId, UUID.fromString(citation.documentId()),
                integrationTokenId, focus, expiresAt.getEpochSecond());
        String encodedPayload = encode(write(payload));
        String signature = encode(sign(encodedPayload));
        return stripTrailingSlash(webBaseUrl) + "/evidence/" + encodedPayload + "." + signature;
    }

    @Transactional(readOnly = true)
    public PublicEvidenceDtos.Response load(String signedToken, String requestId) {
        EvidencePayload payload = verify(signedToken);
        try {
            integrationAccessApi.validateEvidenceAccess(
                    payload.integrationTokenId(), payload.tenantId(), payload.knowledgeBaseId());
        } catch (RuntimeException exception) {
            throw unavailable();
        }
        Document document = documentRepository
                .findByIdAndTenantIdAndKnowledgeBaseIdAndStatusAndVisibility(
                        payload.documentId(), payload.tenantId(), payload.knowledgeBaseId(),
                        DocumentStatus.COMPLETED, DocumentVisibility.CUSTOMER_AND_EMPLOYEE)
                .orElseThrow(this::unavailable);
        var units = inferenceClient.listDocumentUnits(
                payload.tenantId(), payload.knowledgeBaseId(), payload.documentId(),
                requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId);
        return new PublicEvidenceDtos.Response(
                document.getId(), document.getFileName(), payload.focus(),
                Instant.ofEpochSecond(payload.expiresAt()), units);
    }

    private EvidencePayload verify(String signedToken) {
        try {
            String[] parts = signedToken.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw unavailable();
            }
            byte[] expected = sign(parts[0]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw unavailable();
            }
            EvidencePayload payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]), EvidencePayload.class);
            if (payload.expiresAt() <= Instant.now().getEpochSecond()) {
                throw unavailable();
            }
            return payload;
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (RuntimeException | java.io.IOException exception) {
            throw unavailable();
        }
    }

    private byte[] write(EvidencePayload payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to encode public evidence link", exception);
        }
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal((SIGNATURE_PURPOSE + encodedPayload).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign public evidence link", exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Evidence link is invalid or no longer available");
    }

    private record EvidencePayload(
            UUID tenantId,
            UUID knowledgeBaseId,
            UUID documentId,
            UUID integrationTokenId,
            String focus,
            long expiresAt
    ) {
    }
}
