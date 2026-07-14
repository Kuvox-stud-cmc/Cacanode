package com.cacanode.api.document.storage;

public record StoredDocument(
        byte[] content,
        String contentType
) {
}
