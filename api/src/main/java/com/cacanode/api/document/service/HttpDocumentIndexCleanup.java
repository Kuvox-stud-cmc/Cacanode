package com.cacanode.api.document.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.cacanode.api.common.exception.custom.InternalServerErrorException;

@Component
public class HttpDocumentIndexCleanup implements DocumentIndexCleanup {

    private final RestClient client;
    private final String token;

    public HttpDocumentIndexCleanup(
            @Value("${app.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.ai.ingestion-token:development-ingestion-token}") String token
    ) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.token = token;
    }

    @Override
    public void delete(UUID tenantId, UUID knowledgeBaseId, UUID documentId) {
        try {
            client.method(HttpMethod.DELETE)
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/ingestion/internal/sources/{documentId}")
                            .queryParam("tenant_id", tenantId)
                            .queryParam("knowledge_base_id", knowledgeBaseId)
                            .build(documentId))
                    .header("X-Ingestion-Token", token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new InternalServerErrorException(
                    "Unable to delete document indexes: cleanup service returned "
                            + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new InternalServerErrorException("Unable to delete document indexes", e);
        }
    }
}
