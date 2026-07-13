package com.cacanode.api.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.InternalServerErrorException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentType;
import com.cacanode.api.document.enums.DocumentVisibility;
import org.springframework.security.access.AccessDeniedException;
import com.cacanode.api.document.messaging.DocumentIngestRequestedEvent;
import com.cacanode.api.document.messaging.DocumentIngestionPublisher;
import com.cacanode.api.document.messaging.DocumentStatusEvent;
import com.cacanode.api.document.model.Document;
import com.cacanode.api.document.repository.DocumentRepository;
import com.cacanode.api.document.storage.DocumentStorage;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.model.KnowledgeBase;
import com.cacanode.api.tenant.repository.KnowledgeBaseRepository;

class DocumentServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID knowledgeBaseId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentStorage documentStorage;

    @Mock
    private DocumentIngestionPublisher ingestionPublisher;

    @Mock
    private DocumentIndexCleanup indexCleanup;

    private AutoCloseable mocks;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        documentService = new DocumentService(
                documentRepository,
                knowledgeBaseRepository,
                documentStorage,
                ingestionPublisher,
                indexCleanup
        );

        when(knowledgeBaseRepository.findByIdAndTenantId(knowledgeBaseId, tenantId))
                .thenReturn(Optional.of(knowledgeBase(KnowledgeBaseStatus.ACTIVE)));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(documentId);
            return document;
        });
    }

    @Test
    void uploadStoresPendingDocumentAndPublishesEvent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "return-policy-demo-vi.pdf",
                "application/pdf",
                "%PDF-1.4\n%%EOF".getBytes()
        );

        var response = documentService.upload(tenantId, userId, knowledgeBaseId, file);

        assertEquals(documentId, response.id());
        assertEquals("return-policy-demo-vi.pdf", response.fileName());
        assertEquals(DocumentStatus.PENDING, response.status());

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(documentCaptor.capture());
        Document saved = documentCaptor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(knowledgeBaseId, saved.getKnowledgeBaseId());
        assertEquals(userId, saved.getUploadedBy());
        assertEquals(DocumentType.PDF, saved.getFileType());
        assertEquals(DocumentStatus.PENDING, saved.getStatus());
        assertTrue(saved.getStoragePath().contains("/documents/" + documentId + "/return-policy-demo-vi.pdf"));

        verify(documentStorage).store(saved.getStoragePath(), file);

        ArgumentCaptor<DocumentIngestRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(DocumentIngestRequestedEvent.class);
        verify(ingestionPublisher).publish(eventCaptor.capture());
        DocumentIngestRequestedEvent event = eventCaptor.getValue();
        assertEquals("1.0", event.schemaVersion());
        assertEquals(tenantId, event.tenantId());
        assertEquals(knowledgeBaseId, event.knowledgeBaseId());
        assertEquals(documentId, event.documentId());
        assertEquals(userId, event.uploaderId());
        assertEquals(saved.getStoragePath(), event.storageKey());
    }

    @Test
    void tenantAdminCanUploadCustomerVisibleDocument() {
        var response = documentService.upload(
                tenantId, userId, "TENANT_ADMIN", knowledgeBaseId,
                DocumentVisibility.CUSTOMER_AND_EMPLOYEE, txtFile());

        assertEquals(DocumentVisibility.CUSTOMER_AND_EMPLOYEE, response.visibility());
    }

    @Test
    void regularEmployeeCanUploadEmployeeOnlyDocument() {
        var response = documentService.upload(
                tenantId, userId, "USER", knowledgeBaseId,
                DocumentVisibility.EMPLOYEE_ONLY, txtFile());

        assertEquals(DocumentVisibility.EMPLOYEE_ONLY, response.visibility());
    }

    @Test
    void regularEmployeeCannotExposeDocumentToCustomers() {
        assertThrows(AccessDeniedException.class, () -> documentService.upload(
                tenantId, userId, "USER", knowledgeBaseId,
                DocumentVisibility.CUSTOMER_AND_EMPLOYEE, txtFile()));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void onlyTenantAdminCanUpdateVisibility() {
        Document document = document();
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));

        var updated = documentService.updateVisibility(
                tenantId, "TENANT_ADMIN", documentId, DocumentVisibility.EMPLOYEE_ONLY);
        assertEquals(DocumentVisibility.EMPLOYEE_ONLY, updated.visibility());
        assertThrows(AccessDeniedException.class, () -> documentService.updateVisibility(
                tenantId, "USER", documentId, DocumentVisibility.CUSTOMER_AND_EMPLOYEE));
    }

    @Test
    void uploadRejectsInactiveKnowledgeBase() {
        when(knowledgeBaseRepository.findByIdAndTenantId(knowledgeBaseId, tenantId))
                .thenReturn(Optional.of(knowledgeBase(KnowledgeBaseStatus.INACTIVE)));

        assertThrows(BadRequestException.class, () -> documentService.upload(
                tenantId,
                userId,
                knowledgeBaseId,
                txtFile()
        ));

        verify(documentRepository, never()).save(any());
    }

    @Test
    void uploadRejectsWrongTenantKnowledgeBase() {
        when(knowledgeBaseRepository.findByIdAndTenantId(knowledgeBaseId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> documentService.upload(
                tenantId,
                userId,
                knowledgeBaseId,
                txtFile()
        ));
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", new byte[0]);

        assertThrows(BadRequestException.class, () -> documentService.upload(tenantId, userId, knowledgeBaseId, file));
    }

    @Test
    void uploadRejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.doc", "application/msword", "x".getBytes());

        assertThrows(BadRequestException.class, () -> documentService.upload(tenantId, userId, knowledgeBaseId, file));
    }

    @Test
    void uploadRejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/csv", "x".getBytes());

        assertThrows(BadRequestException.class, () -> documentService.upload(tenantId, userId, knowledgeBaseId, file));
    }

    @Test
    void uploadRejectsOverTwentyMb() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(20L * 1024L * 1024L + 1L);
        when(file.getOriginalFilename()).thenReturn("doc.txt");

        assertThrows(BadRequestException.class, () -> documentService.upload(tenantId, userId, knowledgeBaseId, file));
    }

    @Test
    void uploadMarksDocumentFailedWhenPublishFails() {
        doThrow(new InternalServerErrorException("publish failed"))
                .when(ingestionPublisher)
                .publish(any(DocumentIngestRequestedEvent.class));

        assertThrows(InternalServerErrorException.class, () -> documentService.upload(
                tenantId,
                userId,
                knowledgeBaseId,
                txtFile()
        ));

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(documentCaptor.capture());
        Document saved = documentCaptor.getValue();
        assertEquals(DocumentStatus.FAILED, saved.getStatus());
        assertEquals("INGESTION_PUBLISH_FAILED", saved.getErrorMessage());
    }

    @Test
    void getRejectsOtherTenantDocument() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> documentService.get(tenantId, documentId));
    }

    @Test
    void listReturnsTenantKnowledgeBaseDocumentsNewestFirst() {
        Document document = document();
        when(documentRepository.findByTenantIdAndKnowledgeBaseIdOrderByCreatedAtDesc(tenantId, knowledgeBaseId))
                .thenReturn(List.of(document));

        var response = documentService.list(tenantId, knowledgeBaseId);

        assertEquals(1, response.size());
        assertEquals(documentId, response.get(0).id());
        assertEquals(knowledgeBaseId, response.get(0).knowledgeBaseId());
        assertEquals("notes.txt", response.get(0).fileName());
        assertEquals(DocumentType.TXT, response.get(0).fileType());
        assertEquals(DocumentStatus.PENDING, response.get(0).status());
    }

    @Test
    void listRejectsWrongTenantKnowledgeBase() {
        when(knowledgeBaseRepository.findByIdAndTenantId(knowledgeBaseId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> documentService.list(tenantId, knowledgeBaseId));
    }

    @Test
    void statusEventUpdatesDocument() {
        Document document = document();
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));

        boolean updated = documentService.applyStatusEvent(new DocumentStatusEvent(
                "1.0",
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                documentId,
                "COMPLETED",
                12,
                null
        ));

        assertTrue(updated);
        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals(12, document.getChunkCount());
    }

    @Test
    void statusEventUpdatesFailureMessage() {
        Document document = document();
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));

        documentService.applyStatusEvent(new DocumentStatusEvent(
                "1.0",
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                documentId,
                "FAILED",
                null,
                "parse error"
        ));

        assertEquals(DocumentStatus.FAILED, document.getStatus());
        assertEquals("parse error", document.getErrorMessage());
    }

    @Test
    void tenantAdminDeletesIndexesStorageAndDatabase() {
        Document document = document();
        document.setStatus(DocumentStatus.COMPLETED);
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(document));

        documentService.delete(tenantId, "TENANT_ADMIN", documentId);

        verify(indexCleanup).delete(tenantId, knowledgeBaseId, documentId);
        verify(documentStorage).delete("storage-key");
        verify(documentRepository).delete(document);
    }

    @Test
    void deleteRejectsProcessingDocumentAndRegularEmployee() {
        Document document = document();
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class,
                () -> documentService.delete(tenantId, "USER", documentId));
        assertThrows(BadRequestException.class,
                () -> documentService.delete(tenantId, "TENANT_ADMIN", documentId));
        verify(indexCleanup, never()).delete(any(), any(), any());
    }

    private KnowledgeBase knowledgeBase(KnowledgeBaseStatus status) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(knowledgeBaseId);
        knowledgeBase.setStatus(status);
        return knowledgeBase;
    }

    private MockMultipartFile txtFile() {
        return new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
    }

    private Document document() {
        Document document = new Document();
        document.setId(documentId);
        document.setTenantId(tenantId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setUploadedBy(userId);
        document.setFileName("notes.txt");
        document.setFileType(DocumentType.TXT);
        document.setFileSizeBytes(5L);
        document.setStoragePath("storage-key");
        document.setStatus(DocumentStatus.PENDING);
        document.setJobId(UUID.randomUUID().toString());
        return document;
    }
}
