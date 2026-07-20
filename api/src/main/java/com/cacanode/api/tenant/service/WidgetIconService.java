package com.cacanode.api.tenant.service;

import com.cacanode.api.common.cache.BusinessCacheInvalidationPublisher;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.InternalServerErrorException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.document.storage.DocumentStorage;
import com.cacanode.api.tenant.dto.WidgetConfigDtos;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
public class WidgetIconService {
    static final long MAX_ICON_SIZE_BYTES = 2L * 1024L * 1024L;
    private static final Logger log = LoggerFactory.getLogger(WidgetIconService.class);

    private final WidgetConfigRepository repository;
    private final WidgetConfigService widgetConfigService;
    private final DocumentStorage storage;
    private final BusinessCacheInvalidationPublisher invalidationPublisher;

    public WidgetIconService(
            WidgetConfigRepository repository,
            WidgetConfigService widgetConfigService,
            DocumentStorage storage,
            BusinessCacheInvalidationPublisher invalidationPublisher
    ) {
        this.repository = repository;
        this.widgetConfigService = widgetConfigService;
        this.storage = storage;
        this.invalidationPublisher = invalidationPublisher;
    }

    @Transactional
    public WidgetConfigDtos.Response upload(UUID tenantId, MultipartFile file) {
        IconFile icon = validate(file);
        WidgetConfig config = find(tenantId);
        String previousKey = config.getIconObjectKey();
        String newKey = "tenants/%s/widget/icon/%s.%s".formatted(tenantId, UUID.randomUUID(), icon.extension());

        storage.store(newKey, icon.content(), icon.contentType());
        try {
            config.setIconObjectKey(newKey);
            repository.saveAndFlush(config);
        } catch (RuntimeException exception) {
            deleteBestEffort(newKey);
            throw exception;
        }

        deleteAfterCommit(previousKey);
        invalidationPublisher.widget(tenantId);
        return widgetConfigService.toResponse(config);
    }

    @Transactional(readOnly = true)
    public WidgetIcon load(UUID tenantId) {
        String key = find(tenantId).getIconObjectKey();
        if (key == null) {
            throw new ResourceNotFoundException("Widget icon was not found");
        }
        var stored = storage.load(key);
        return new WidgetIcon(stored.content(), contentTypeForKey(key));
    }

    @Transactional
    public void delete(UUID tenantId) {
        WidgetConfig config = find(tenantId);
        String previousKey = config.getIconObjectKey();
        if (previousKey == null) {
            throw new ResourceNotFoundException("Widget icon was not found");
        }
        config.setIconObjectKey(null);
        repository.saveAndFlush(config);
        deleteAfterCommit(previousKey);
        invalidationPublisher.widget(tenantId);
    }

    private WidgetConfig find(UUID tenantId) {
        return repository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Widget configuration was not found"));
    }

    private IconFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Widget icon is required");
        }
        if (file.getSize() > MAX_ICON_SIZE_BYTES) {
            throw new BadRequestException("Widget icon must be 2 MB or smaller");
        }
        String declaredType = file.getContentType() == null
                ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        IconType declared = IconType.fromContentType(declaredType);
        if (declared == null) {
            throw new BadRequestException("Widget icon must be PNG, JPEG, or WebP");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InternalServerErrorException("Unable to read uploaded widget icon", e);
        }
        IconType actual = IconType.fromSignature(content);
        if (actual == null || actual != declared) {
            throw new BadRequestException("Widget icon content does not match its media type");
        }
        return new IconFile(content, actual.contentType, actual.extension);
    }

    private String contentTypeForKey(String key) {
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".jpg")) return "image/jpeg";
        if (key.endsWith(".webp")) return "image/webp";
        throw new InternalServerErrorException("Stored widget icon has an invalid format");
    }

    private void deleteAfterCommit(String key) {
        if (key == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteBestEffort(key);
                }
            });
        } else {
            deleteBestEffort(key);
        }
    }

    private void deleteBestEffort(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Unable to delete widget icon object {}", key, exception);
        }
    }

    public record WidgetIcon(byte[] content, String contentType) { }

    private record IconFile(byte[] content, String contentType, String extension) { }

    private enum IconType {
        PNG("image/png", "png"), JPEG("image/jpeg", "jpg"), WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        IconType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        static IconType fromContentType(String contentType) {
            return switch (contentType) {
                case "image/png" -> PNG;
                case "image/jpeg" -> JPEG;
                case "image/webp" -> WEBP;
                default -> null;
            };
        }

        static IconType fromSignature(byte[] bytes) {
            if (bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                    && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a) return PNG;
            if (bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff) return JPEG;
            if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                    && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P') return WEBP;
            return null;
        }
    }
}
