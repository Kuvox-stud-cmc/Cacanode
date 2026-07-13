package com.cacanode.api.integration.repository;

import com.cacanode.api.integration.model.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId);
    List<WebhookEndpoint> findByTenant_IdAndActiveTrue(UUID tenantId);
    Optional<WebhookEndpoint> findByIdAndTenant_Id(UUID id, UUID tenantId);
}
