package com.cacanode.api.integration.repository;

import com.cacanode.api.integration.model.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<WebhookEndpoint> findByTenantIdAndActiveTrue(UUID tenantId);
    Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, UUID tenantId);
}
