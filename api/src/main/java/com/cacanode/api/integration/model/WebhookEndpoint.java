package com.cacanode.api.integration.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT")
    private String encryptedSecret;

    @Column(name = "events", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> events = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_delivery_at")
    private LocalDateTime lastDeliveryAt;

    @Column(name = "last_delivery_status", length = 50)
    private String lastDeliveryStatus;
}
