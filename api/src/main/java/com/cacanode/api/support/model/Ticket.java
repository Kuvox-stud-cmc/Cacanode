package com.cacanode.api.support.model;

import com.cacanode.api.common.model.BaseEntity;
import com.cacanode.api.support.enums.TicketPriority;
import com.cacanode.api.support.enums.TicketSource;
import com.cacanode.api.support.enums.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "chatbot_id", nullable = false)
    private UUID chatbotId;

    @Column(name = "chat_session_id", nullable = false)
    private UUID chatSessionId;

    @Column(name = "integration_token_id")
    private UUID integrationTokenId;

    @Column(name = "assigned_to")
    private UUID assignedToId;

    @Column(name = "external_user_id")
    private String externalUserId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 320)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private TicketSource source;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 50)
    private TicketPriority priority = TicketPriority.NORMAL;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
