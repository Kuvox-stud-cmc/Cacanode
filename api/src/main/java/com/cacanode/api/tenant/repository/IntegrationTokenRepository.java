package com.cacanode.api.tenant.repository;

import com.cacanode.api.tenant.model.IntegrationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationTokenRepository extends JpaRepository<IntegrationToken, UUID> {
    List<IntegrationToken> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId);
    Optional<IntegrationToken> findByIdAndTenant_Id(UUID id, UUID tenantId);
    Optional<IntegrationToken> findByTokenHash(String tokenHash);

    @Query("select token.tokenHash as tokenHash from IntegrationToken token where token.chatbot.id = :chatbotId")
    List<TokenHashProjection> findTokenHashesByChatbotId(@Param("chatbotId") UUID chatbotId);

    @Query("select token.tokenHash as tokenHash from IntegrationToken token where token.tenant.id = :tenantId")
    List<TokenHashProjection> findTokenHashesByTenantId(@Param("tenantId") UUID tenantId);

    interface TokenHashProjection {
        String getTokenHash();
    }
}
