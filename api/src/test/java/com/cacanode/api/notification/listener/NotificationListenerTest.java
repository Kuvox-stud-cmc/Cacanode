package com.cacanode.api.notification.listener;

import com.cacanode.api.support.api.event.TicketCreatedEvent;
import com.cacanode.api.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationListenerTest {

    @Test
    void ticketCreatedEventSendsCustomerConfirmation() {
        NotificationService notificationService = mock(NotificationService.class);
        NotificationListener listener = new NotificationListener(notificationService);
        UUID tenantId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        TicketCreatedEvent event = new TicketCreatedEvent(
                this, tenantId, ticketId, "Acme", "customer@example.com", "Ada",
                "Refund request", "Charged twice", "en");

        listener.handleTicketCreated(event);

        verify(notificationService).sendAndRecordTicketCreatedEmail(
                tenantId, ticketId, "customer@example.com", "Ada", "Acme",
                "Refund request", "Charged twice", "en");
    }
}
