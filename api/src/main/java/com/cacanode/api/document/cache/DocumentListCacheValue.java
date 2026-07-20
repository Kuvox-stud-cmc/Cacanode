package com.cacanode.api.document.cache;

import com.cacanode.api.document.dto.DocumentListItemResponse;

import java.util.List;

public record DocumentListCacheValue(List<DocumentListItemResponse> documents) {
    public DocumentListCacheValue {
        documents = List.copyOf(documents);
    }
}
