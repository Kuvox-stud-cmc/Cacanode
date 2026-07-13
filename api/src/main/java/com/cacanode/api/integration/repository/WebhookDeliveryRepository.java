package com.cacanode.api.integration.repository;

import com.cacanode.api.integration.model.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
}
