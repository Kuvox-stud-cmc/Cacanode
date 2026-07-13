package com.cacanode.api.tenant.repository;

import com.cacanode.api.tenant.model.IntegrationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationTokenRepository extends JpaRepository<IntegrationToken, UUID> {
    List<IntegrationToken> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId);
    Optional<IntegrationToken> findByIdAndTenant_Id(UUID id, UUID tenantId);
    Optional<IntegrationToken> findByTokenHash(String tokenHash);
}
