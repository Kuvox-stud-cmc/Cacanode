package com.cacanode.api.document.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.InternalServerErrorException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.document.dto.DocumentListItemResponse;
import com.cacanode.api.document.dto.DocumentStatusResponse;
import com.cacanode.api.document.dto.DocumentUploadResponse;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentType;
import com.cacanode.api.document.messaging.DocumentIngestRequestedEvent;
import com.cacanode.api.document.messaging.DocumentIngestionPublisher;
import com.cacanode.api.document.messaging.DocumentStatusEvent;
import com.cacanode.api.document.model.Document;
import com.cacanode.api.document.repository.DocumentRepository;
import com.cacanode.api.document.storage.DocumentStorage;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.repository.KnowledgeBaseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/plain",
            "application/pdf",
            "application/octet-stream"
    );

    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentStorage documentStorage;
    private final DocumentIngestionPublisher ingestionPublisher;

    @Transactional(noRollbackFor = InternalServerErrorException.class)
    public DocumentUploadResponse upload(UUID tenantId, UUID userId, UUID knowledgeBaseId, MultipartFile file) {
        var knowledgeBase = knowledgeBaseRepository.findByIdAndTenantId(knowledgeBaseId, tenantId)
                .orElseThrow(() -> new BadRequestException("Knowledge base is not active or not found"));

        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BadRequestException("Knowledge base is not active or not found");
        }

        validateFile(file);

        String safeFileName = safeFileName(file.getOriginalFilename());
        DocumentType documentType = documentTypeFor(safeFileName);
        UUID jobId = UUID.randomUUID();

        Document document = new Document();
        document.setTenantId(tenantId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setUploadedBy(userId);
        document.setFileName(safeFileName);
        document.setFileType(documentType);
        document.setFileSizeBytes(file.getSize());
        document.setStoragePath("pending");
        document.setStatus(DocumentStatus.PENDING);
        document.setJobId(jobId.toString());

        document = documentRepository.save(document);
        String storageKey = storageKey(tenantId, knowledgeBaseId, document.getId(), safeFileName);
        document.setStoragePath(storageKey);

        try {
            documentStorage.store(storageKey, file);
        } catch (RuntimeException e) {
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage("DOCUMENT_STORAGE_FAILED");
            if (e instanceof InternalServerErrorException) {
                throw e;
            }
            throw new InternalServerErrorException("Unable to store uploaded document");
        }

        DocumentIngestRequestedEvent event = new DocumentIngestRequestedEvent(
                "1.0",
                UUID.randomUUID(),
                jobId,
                tenantId,
                knowledgeBaseId,
                document.getId(),
                userId,
                storageKey,
                safeFileName,
                file.getContentType(),
                file.getSize(),
                Instant.now()
        );

        try {
            ingestionPublisher.publish(event);
        } catch (RuntimeException e) {
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage("INGESTION_PUBLISH_FAILED");
            if (e instanceof InternalServerErrorException) {
                throw e;
            }
            throw new InternalServerErrorException("Unable to publish document ingestion request");
        }

        return toUploadResponse(document);
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse get(UUID tenantId, UUID documentId) {
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
                .map(this::toStatusResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    @Transactional(readOnly = true)
    public List<DocumentListItemResponse> list(UUID tenantId, UUID knowledgeBaseId) {
        var knowledgeBase = knowledgeBaseRepository.findByIdAndTenantId(knowledgeBaseId, tenantId)
                .orElseThrow(() -> new BadRequestException("Knowledge base is not active or not found"));
        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BadRequestException("Knowledge base is not active or not found");
        }

        return documentRepository
                .findByTenantIdAndKnowledgeBaseIdOrderByCreatedAtDesc(tenantId, knowledgeBaseId)
                .stream()
                .map(this::toListItemResponse)
                .toList();
    }

    @Transactional
    public boolean applyStatusEvent(DocumentStatusEvent event) {
        DocumentStatus status = parseStatus(event.status());
        return documentRepository.findByIdAndTenantId(event.documentId(), event.tenantId())
                .map(document -> {
                    document.setStatus(status);
                    document.setChunkCount(event.chunkCount());
                    document.setErrorMessage(event.errorMessage());
                    return true;
                })
                .orElse(false);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Uploaded file exceeds 20 MB");
        }

        String filename = safeFileName(file.getOriginalFilename());
        documentTypeFor(filename);

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Unsupported file content type");
        }
    }

    private DocumentType documentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt")) {
            return DocumentType.TXT;
        }
        if (lower.endsWith(".pdf")) {
            return DocumentType.PDF;
        }
        throw new BadRequestException("Unsupported file extension");
    }

    private String safeFileName(String originalFilename) {
        String cleaned = StringUtils.hasText(originalFilename) ? originalFilename.replace('\\', '/') : "document";
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(cleaned) || cleaned.equals(".") || cleaned.equals("..")) {
            return "document";
        }
        return cleaned;
    }

    private String storageKey(UUID tenantId, UUID knowledgeBaseId, UUID documentId, String fileName) {
        return "tenants/%s/knowledge-bases/%s/documents/%s/%s"
                .formatted(tenantId, knowledgeBaseId, documentId, fileName);
    }

    private DocumentStatus parseStatus(String status) {
        try {
            return DocumentStatus.valueOf(status);
        } catch (RuntimeException e) {
            throw new BadRequestException("Unsupported document status event");
        }
    }

    private DocumentUploadResponse toUploadResponse(Document document) {
        return new DocumentUploadResponse(
                document.getId(),
                UUID.fromString(document.getJobId()),
                document.getFileName(),
                document.getStatus()
        );
    }

    private DocumentStatusResponse toStatusResponse(Document document) {
        return new DocumentStatusResponse(
                document.getId(),
                UUID.fromString(document.getJobId()),
                document.getFileName(),
                document.getKnowledgeBaseId(),
                document.getStatus(),
                document.getChunkCount(),
                document.getErrorMessage()
        );
    }

    private DocumentListItemResponse toListItemResponse(Document document) {
        return new DocumentListItemResponse(
                document.getId(),
                UUID.fromString(document.getJobId()),
                document.getFileName(),
                document.getFileType(),
                document.getFileSizeBytes(),
                document.getKnowledgeBaseId(),
                document.getStatus(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt()
        );
    }
}
