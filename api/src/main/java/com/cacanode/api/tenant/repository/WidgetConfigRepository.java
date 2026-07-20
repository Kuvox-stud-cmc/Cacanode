package com.cacanode.api.tenant.repository;

import com.cacanode.api.tenant.model.WidgetConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface WidgetConfigRepository extends JpaRepository<WidgetConfig, UUID> {
    boolean existsByChatbot_Id(UUID chatbotId);
    Optional<WidgetConfig> findByChatbot_IdAndTenant_Id(UUID chatbotId, UUID tenantId);
    Optional<WidgetConfig> findFirstByTenant_IdOrderByCreatedAtAsc(UUID tenantId);
    boolean existsByManagedWidgetToken_IdAndTenant_Id(UUID tokenId, UUID tenantId);
}
