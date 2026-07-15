package com.cacanode.api.billing.repository;

import com.cacanode.api.billing.model.BillingWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEvent, UUID> {
    Optional<BillingWebhookEvent> findByPayloadHash(String payloadHash);
}
