package com.cacanode.api.document.storage;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorage {
    void store(String key, MultipartFile file);
}
