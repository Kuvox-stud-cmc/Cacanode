package com.cacanode.api.document.service;

import java.util.UUID;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
            @Value("${app.ai.ingestion-token:development-ingestion-token}") String token,
            @Value("${app.ai.cleanup-connect-timeout-seconds:2}") long connectTimeoutSeconds,
            @Value("${app.ai.cleanup-read-timeout-seconds:10}") long readTimeoutSeconds
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
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
