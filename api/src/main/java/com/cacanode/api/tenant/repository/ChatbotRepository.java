package com.cacanode.api.tenant.repository;

import com.cacanode.api.tenant.model.Chatbot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatbotRepository extends JpaRepository<Chatbot, UUID> {
    List<Chatbot> findByTenantId(UUID tenantId);
}
