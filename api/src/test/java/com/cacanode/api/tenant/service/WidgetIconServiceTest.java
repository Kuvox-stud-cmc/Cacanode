package com.cacanode.api.tenant.service;

import com.cacanode.api.common.cache.BusinessCacheInvalidationPublisher;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.common.storage.DocumentStorage;
import com.cacanode.api.common.storage.StoredDocument;
import com.cacanode.api.tenant.dto.WidgetConfigDtos;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WidgetIconServiceTest {
    private final WidgetConfigRepository repository = mock(WidgetConfigRepository.class);
    private final WidgetConfigService widgetConfigService = mock(WidgetConfigService.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final BusinessCacheInvalidationPublisher invalidationPublisher =
            mock(BusinessCacheInvalidationPublisher.class);
    private final WidgetIconService service = new WidgetIconService(
            repository, widgetConfigService, storage, invalidationPublisher);
    private final UUID tenantId = UUID.randomUUID();
    private final WidgetConfig config = new WidgetConfig();

    @BeforeEach
    void setUp() {
        when(repository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId)).thenReturn(Optional.of(config));
        when(repository.saveAndFlush(config)).thenReturn(config);
        when(widgetConfigService.toResponse(config)).thenReturn(new WidgetConfigDtos.Response(
                null, "Assistant", "Hello", "#4f46e5", null,
                true, java.util.List.of(), false, true, "/api/v1/public/widget/icon",
                com.cacanode.api.tenant.enums.WidgetIconStyle.STANDARD));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @ParameterizedTest
    @MethodSource("validIcons")
    void acceptsSupportedIcons(String contentType, byte[] bytes, String extension) {
        service.upload(tenantId, new MockMultipartFile("file", "icon", contentType, bytes));

        verify(storage).store(anyString(), eq(bytes), eq(contentType));
        assertTrue(config.getIconObjectKey().endsWith(extension));
        verify(invalidationPublisher).widget(tenantId);
    }

    @Test
    void rejectsEmptyOversizedSvgAndSignatureMismatch() {
        assertThrows(BadRequestException.class, () -> service.upload(tenantId,
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0])));
        assertThrows(BadRequestException.class, () -> service.upload(tenantId,
                new MockMultipartFile("file", "large.png", "image/png",
                        new byte[(int) WidgetIconService.MAX_ICON_SIZE_BYTES + 1])));
        assertThrows(BadRequestException.class, () -> service.upload(tenantId,
                new MockMultipartFile("file", "icon.svg", "image/svg+xml", "<svg/>".getBytes())));
        assertThrows(BadRequestException.class, () -> service.upload(tenantId,
                new MockMultipartFile("file", "fake.png", "image/png", jpeg())));
        verify(storage, never()).store(anyString(), any(byte[].class), anyString());
    }

    @Test
    void replacementDeletesOldObjectOnlyAfterCommit() {
        config.setIconObjectKey("tenants/old/widget/icon/old.png");
        TransactionSynchronizationManager.initSynchronization();

        service.upload(tenantId, new MockMultipartFile("file", "icon.png", "image/png", png()));

        verify(storage, never()).delete("tenants/old/widget/icon/old.png");
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete("tenants/old/widget/icon/old.png");
    }

    @Test
    void databaseFailureDeletesNewObjectAndPreservesOldObject() {
        config.setIconObjectKey("tenants/old/widget/icon/old.png");
        when(repository.saveAndFlush(config)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.upload(tenantId,
                new MockMultipartFile("file", "icon.png", "image/png", png())));

        verify(storage, never()).delete("tenants/old/widget/icon/old.png");
        verify(storage).delete(anyString());
        verify(invalidationPublisher, never()).widget(tenantId);
    }

    @Test
    void loadsOnlyTheRequestedTenantsConfiguredObject() {
        byte[] content = png();
        config.setIconObjectKey("tenants/%s/widget/icon/icon.png".formatted(tenantId));
        when(storage.load(config.getIconObjectKey())).thenReturn(new StoredDocument(content, "text/plain"));

        var icon = service.load(tenantId);

        assertArrayEquals(content, icon.content());
        assertEquals("image/png", icon.contentType());
        verify(repository).findFirstByTenant_IdOrderByCreatedAtAsc(tenantId);
    }

    @Test
    void missingIconReturnsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.load(tenantId));
    }

    private static Stream<Arguments> validIcons() {
        return Stream.of(
                Arguments.of("image/png", png(), ".png"),
                Arguments.of("image/jpeg", jpeg(), ".jpg"),
                Arguments.of("image/webp", webp(), ".webp")
        );
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
    }

    private static byte[] webp() {
        return new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    }
}
