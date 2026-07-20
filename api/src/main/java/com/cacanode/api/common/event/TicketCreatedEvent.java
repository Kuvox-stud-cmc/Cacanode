package com.cacanode.api.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class TicketCreatedEvent extends ApplicationEvent {
    private final UUID tenantId;
    private final UUID ticketId;
    private final String tenantName;
    private final String customerEmail;
    private final String customerName;
    private final String title;
    private final String description;
    private final String locale;

    public TicketCreatedEvent(
            Object source,
            UUID tenantId,
            UUID ticketId,
            String tenantName,
            String customerEmail,
            String customerName,
            String title,
            String description,
            String locale
    ) {
        super(source);
        this.tenantId = tenantId;
        this.ticketId = ticketId;
        this.tenantName = tenantName;
        this.customerEmail = customerEmail;
        this.customerName = customerName;
        this.title = title;
        this.description = description;
        this.locale = locale;
    }
}
