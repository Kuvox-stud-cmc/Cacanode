package com.cacanode.api.tenant.repository;

import com.cacanode.api.tenant.model.WidgetConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WidgetConfigRepository extends JpaRepository<WidgetConfig, UUID> {
    Optional<WidgetConfig> findByChatbotId(UUID chatbotId);
}
