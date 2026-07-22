package com.cacanode.api.support.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ticket_notes")
public class TicketNote extends BaseEntity {
    @Column(name = "ticket_id", nullable = false)
    private java.util.UUID ticketId;

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @Column(name = "author_id", nullable = false)
    private java.util.UUID authorId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}
