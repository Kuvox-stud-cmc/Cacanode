package com.cacanode.api.document.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.InternalServerErrorException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.document.dto.DocumentListItemResponse;
import com.cacanode.api.document.dto.DocumentDownloadResponse;
import com.cacanode.api.document.dto.DocumentStatusResponse;
import com.cacanode.api.document.dto.DocumentUploadResponse;
import com.cacanode.api.document.event.FailedDocumentCleanupRequestedEvent;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentType;
import com.cacanode.api.document.enums.DocumentVisibility;
import com.cacanode.api.document.messaging.DocumentIngestRequestedEvent;
import com.cacanode.api.document.messaging.DocumentIngestionPublisher;
import com.cacanode.api.document.messaging.DocumentStatusEvent;
import com.cacanode.api.document.model.Document;
import com.cacanode.api.document.repository.DocumentRepository;
import com.cacanode.api.document.storage.DocumentStorage;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.repository.KnowledgeBaseRepository;
import com.cacanode.api.tenant.api.TenantModuleApi;

import org.springframework.beans.factory.annotation.Autowired;

@Service
public class DocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 200;
    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentStorage documentStorage;
    private final DocumentIngestionPublisher ingestionPublisher;
    private final DocumentIndexCleanup indexCleanup;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantModuleApi tenantModuleApi;

    @Autowired
    public DocumentService(
            DocumentRepository documentRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentStorage documentStorage,
            DocumentIngestionPublisher ingestionPublisher,
            DocumentIndexCleanup indexCleanup,
            ApplicationEventPublisher eventPublisher,
            TenantModuleApi tenantModuleApi
    ) {
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentStorage = documentStorage;
        this.ingestionPublisher = ingestionPublisher;
        this.indexCleanup = indexCleanup;
        this.eventPublisher = eventPublisher;
        this.tenantModuleApi = tenantModuleApi;
    }

    public DocumentService(
            DocumentRepository documentRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentStorage documentStorage,
            DocumentIngestionPublisher ingestionPublisher,
            DocumentIndexCleanup indexCleanup,
            ApplicationEventPublisher eventPublisher
    ) {
        this(documentRepository, knowledgeBaseRepository, documentStorage, ingestionPublisher,
                indexCleanup, eventPublisher, null);
    }

    DocumentUploadResponse upload(UUID tenantId, UUID userId, UUID knowledgeBaseId, MultipartFile file) {
        return upload(tenantId, userId, "TENANT_ADMIN", knowledgeBaseId,
                DocumentVisibility.CUSTOMER_AND_EMPLOYEE, file);
    }

    @Transactional(noRollbackFor = InternalServerErrorException.class)
    public DocumentUploadResponse upload(UUID tenantId, UUID userId, String role, UUID knowledgeBaseId,
                                         DocumentVisibility visibility, MultipartFile file) {
        if (visibility == null) {
            throw new BadRequestException("Document visibility is required");
        }
        if (!"TENANT_ADMIN".equals(role) && visibility != DocumentVisibility.EMPLOYEE_ONLY) {
            throw new AccessDeniedException("Only tenant admins can share documents with customers");
        }
        var knowledgeBase = knowledgeBaseRepository.findByIdAndTenantId(knowledgeBaseId, tenantId)
                .orElseThrow(() -> new BadRequestException("Knowledge base is not active or not found"));

        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BadRequestException("Knowledge base is not active or not found");
        }

        validateFile(file);
        enforceUploadQuota(tenantId, file.getSize());

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
        document.setVisibility(visibility);
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

    private void enforceUploadQuota(UUID tenantId, long incomingBytes) {
        if (tenantModuleApi == null) return;
        var entitlements = tenantModuleApi.lockEntitlements(tenantId);
        long documentCount = documentRepository.countByTenantIdAndStatusNot(tenantId, DocumentStatus.FAILED);
        long storedBytes = documentRepository.sumFileSizeByTenantIdAndStatusNot(tenantId, DocumentStatus.FAILED);
        if (entitlements.maxDocuments() != null && documentCount + 1 > entitlements.maxDocuments()) {
            throw new BadRequestException("DOCUMENT_QUOTA_EXCEEDED");
        }
        long storageLimitBytes = entitlements.maxStorageMb() == null
                ? Long.MAX_VALUE : entitlements.maxStorageMb() * 1024L * 1024L;
        if (storedBytes + incomingBytes > storageLimitBytes) {
            throw new BadRequestException("STORAGE_QUOTA_EXCEEDED");
        }
    }

    @Transactional
    public DocumentStatusResponse updateVisibility(UUID tenantId, String role, UUID documentId,
                                                   DocumentVisibility visibility) {
        if (!"TENANT_ADMIN".equals(role)) {
            throw new AccessDeniedException("Only tenant admins can update document visibility");
        }
        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        document.setVisibility(visibility);
        return toStatusResponse(document);
    }

    @Transactional
    public void delete(UUID tenantId, String role, UUID documentId) {
        if (!"TENANT_ADMIN".equals(role)) {
            throw new AccessDeniedException("Only tenant admins can delete documents");
        }
        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (document.getStatus() == DocumentStatus.PENDING
                || document.getStatus() == DocumentStatus.PROCESSING) {
            throw new BadRequestException("Wait for document processing to finish before deleting it");
        }
        if (document.getStatus() == DocumentStatus.FAILED) {
            documentRepository.delete(document);
            eventPublisher.publishEvent(new FailedDocumentCleanupRequestedEvent(
                    tenantId,
                    document.getKnowledgeBaseId(),
                    documentId,
                    document.getStoragePath()
            ));
            return;
        }
        indexCleanup.delete(tenantId, document.getKnowledgeBaseId(), documentId);
        documentStorage.delete(document.getStoragePath());
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse get(UUID tenantId, UUID documentId) {
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
                .map(this::toStatusResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    @Transactional(readOnly = true)
    public DocumentDownloadResponse download(UUID tenantId, UUID documentId) {
        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        try {
            var stored = documentStorage.load(document.getStoragePath());
            String contentType = StringUtils.hasText(stored.contentType())
                    ? stored.contentType()
                    : contentTypeFor(document.getFileType());
            return new DocumentDownloadResponse(
                    document.getFileName(),
                    contentType,
                    stored.content()
            );
        } catch (InternalServerErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InternalServerErrorException("Unable to download document", e);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentListItemResponse> list(UUID tenantId, UUID knowledgeBaseId) {
        requireActiveKnowledgeBase(tenantId, knowledgeBaseId);

        return documentRepository
                .findByTenantIdAndKnowledgeBaseIdOrderByCreatedAtDescIdDesc(tenantId, knowledgeBaseId)
                .stream()
                .map(this::toListItemResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentListItemResponse> list(
            UUID tenantId,
            UUID knowledgeBaseId,
            Integer page,
            Integer size,
            String searchText,
            DocumentStatus status,
            DocumentType type,
            DocumentVisibility visibility
    ) {
        requireActiveKnowledgeBase(tenantId, knowledgeBaseId);

        int requestedPage = page == null ? 0 : page;
        int requestedSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (requestedPage < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (requestedSize < 1 || requestedSize > MAX_PAGE_SIZE) {
            throw new BadRequestException("Size must be between 1 and 100");
        }

        String query = StringUtils.hasText(searchText) ? searchText.trim() : null;
        if (query != null && query.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Search text must be 200 characters or fewer");
        }

        Specification<Document> specification = (root, criteriaQuery, builder) -> builder.and(
                builder.equal(root.get("tenantId"), tenantId),
                builder.equal(root.get("knowledgeBaseId"), knowledgeBaseId)
        );
        if (query != null) {
            String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.like(builder.lower(root.get("fileName")), pattern, '\\'));
        }
        if (status != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.get("status"), status));
        }
        if (type != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.get("fileType"), type));
        }
        if (visibility != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.get("visibility"), visibility));
        }

        var pageable = PageRequest.of(
                requestedPage,
                requestedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return documentRepository.findAll(specification, pageable)
                .getContent()
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
        DocumentType type = documentTypeFor(filename);
        DocumentFileValidator.validate(file, type);
    }

    private void requireActiveKnowledgeBase(UUID tenantId, UUID knowledgeBaseId) {
        var knowledgeBase = knowledgeBaseRepository.findByIdAndTenantId(knowledgeBaseId, tenantId)
                .orElseThrow(() -> new BadRequestException("Knowledge base is not active or not found"));
        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BadRequestException("Knowledge base is not active or not found");
        }
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private DocumentType documentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt")) {
            return DocumentType.TXT;
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return DocumentType.MARKDOWN;
        }
        if (lower.endsWith(".docx")) {
            return DocumentType.DOCX;
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return DocumentType.HTML;
        }
        if (lower.endsWith(".xlsx")) {
            return DocumentType.XLSX;
        }
        if (lower.endsWith(".csv")) {
            return DocumentType.CSV;
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

    private String contentTypeFor(DocumentType type) {
        return switch (type) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case TXT -> "text/plain";
            case MARKDOWN -> "text/markdown";
            case HTML -> "text/html";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case CSV -> "text/csv";
        };
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
                document.getStatus(),
                document.getVisibility()
        );
    }

    private DocumentStatusResponse toStatusResponse(Document document) {
        return new DocumentStatusResponse(
                document.getId(),
                UUID.fromString(document.getJobId()),
                document.getFileName(),
                document.getFileType(),
                document.getFileSizeBytes(),
                document.getCreatedAt(),
                document.getKnowledgeBaseId(),
                document.getStatus(),
                document.getVisibility(),
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
                document.getVisibility(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt()
        );
    }
}
