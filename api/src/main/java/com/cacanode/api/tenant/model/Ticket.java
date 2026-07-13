package com.cacanode.api.tenant.model;

import com.cacanode.api.common.model.BaseEntity;
import com.cacanode.api.tenant.enums.TicketPriority;
import com.cacanode.api.tenant.enums.TicketSource;
import com.cacanode.api.tenant.enums.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private Chatbot chatbot;

    @Column(name = "chat_session_id", nullable = false)
    private UUID chatSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_token_id")
    private IntegrationToken integrationToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

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
