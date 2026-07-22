package com.cacanode.api.common.storage;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorage {
    void store(String key, MultipartFile file);

    void store(String key, byte[] content, String contentType);

    StoredDocument load(String key);

    void delete(String key);
}
