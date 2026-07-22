package com.cacanode.api.support.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.chat.api.ChatApi;
import com.cacanode.api.tenant.api.IntegrationAccessApi;
import com.cacanode.api.tenant.api.TenantIdentityApi;
import com.cacanode.api.support.dto.TicketDtos;
import com.cacanode.api.support.enums.TicketPriority;
import com.cacanode.api.support.enums.TicketSource;
import com.cacanode.api.support.enums.TicketStatus;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.support.model.Ticket;
import com.cacanode.api.support.model.TicketNote;
import com.cacanode.api.tenant.model.User;
import com.cacanode.api.support.repository.TicketNoteRepository;
import com.cacanode.api.support.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;

import com.cacanode.api.support.api.event.TicketCreatedEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private TicketRepository ticketRepository;
    private TicketNoteRepository noteRepository;
    private TenantIdentityApi tenantIdentityApi;
    private ChatApi chatApi;
    private TicketService service;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);
        noteRepository = mock(TicketNoteRepository.class);
        tenantIdentityApi = mock(TenantIdentityApi.class);
        chatApi = mock(ChatApi.class);
        service = new TicketService(
                ticketRepository, noteRepository, tenantIdentityApi, chatApi,
                mock(org.springframework.context.ApplicationEventPublisher.class)
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void listUsesFullFiltersStableOrderingAndEmptyNotes() {
        Ticket ticket = ticket();
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket)));

        var response = service.list(
                tenantId,
                TicketStatus.IN_PROGRESS,
                TicketPriority.HIGH,
                TicketSource.WIDGET,
                userId,
                false,
                2,
                25
        );

        assertEquals(1, response.getTotalElements());
        assertEquals(List.of(), response.getContent().getFirst().notes());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(25, pageable.getValue().getPageSize());
        assertEquals("createdAt: DESC,id: DESC", pageable.getValue().getSort().toString());
    }

    @Test
    void listRejectsBoundsAndConflictingAssignmentFilters() {
        assertThrows(BadRequestException.class, () -> service.list(
                tenantId, null, null, null, null, false, -1, 50));
        assertThrows(BadRequestException.class, () -> service.list(
                tenantId, null, null, null, null, false, 0, 0));
        assertThrows(BadRequestException.class, () -> service.list(
                tenantId, null, null, null, null, false, 0, 101));
        assertThrows(BadRequestException.class, () -> service.list(
                tenantId, null, null, null, userId, true, 0, 50));
        verify(ticketRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void detailRejectsAnotherTenantsTicket() {
        when(ticketRepository.findByIdAndTenantId(ticketId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(tenantId, ticketId));
        verify(noteRepository, never()).findByTicketIdAndTenantIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void statusUpdatesSetAndClearResolvedTimestamp() {
        Ticket ticket = ticket();
        when(ticketRepository.findByIdAndTenantId(ticketId, tenantId)).thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicketIdAndTenantIdOrderByCreatedAtAsc(ticketId, tenantId))
                .thenReturn(List.of());

        service.update(tenantId, ticketId, new TicketDtos.UpdateRequest(
                TicketStatus.RESOLVED, null, null, null));
        assertNotNull(ticket.getResolvedAt());

        service.update(tenantId, ticketId, new TicketDtos.UpdateRequest(
                TicketStatus.IN_PROGRESS, null, null, null));
        assertNull(ticket.getResolvedAt());
    }

    @Test
    void assignmentMustBelongToTenantAndCanBeCleared() {
        Ticket ticket = ticket();
        when(ticketRepository.findByIdAndTenantId(ticketId, tenantId)).thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicketIdAndTenantIdOrderByCreatedAtAsc(ticketId, tenantId))
                .thenReturn(List.of());
        when(tenantIdentityApi.requireUser(tenantId, userId)).thenReturn(userSnapshot(userId, "Assignee"));

        service.update(tenantId, ticketId, new TicketDtos.UpdateRequest(
                null, TicketPriority.URGENT, userId, false));
        assertEquals(userId, ticket.getAssignedToId());
        assertEquals(TicketPriority.URGENT, ticket.getPriority());

        service.update(tenantId, ticketId, new TicketDtos.UpdateRequest(
                null, null, null, true));
        assertNull(ticket.getAssignedToId());

        UUID otherTenantUser = UUID.randomUUID();
        when(tenantIdentityApi.requireUser(tenantId, otherTenantUser))
                .thenThrow(new ResourceNotFoundException("missing"));
        assertThrows(BadRequestException.class, () -> service.update(
                tenantId, ticketId, new TicketDtos.UpdateRequest(null, null, otherTenantUser, false)));
    }

    @Test
    void noteIsTrimmedTenantScopedAndReturnedWithAuthor() {
        Ticket ticket = ticket();
        when(ticketRepository.findByIdAndTenantId(ticketId, tenantId)).thenReturn(Optional.of(ticket));
        when(tenantIdentityApi.requireUser(tenantId, userId)).thenReturn(userSnapshot(userId, "Ada"));
        when(noteRepository.save(any(TicketNote.class))).thenAnswer(invocation -> {
            TicketNote note = invocation.getArgument(0);
            note.setId(UUID.randomUUID());
            note.setCreatedAt(LocalDateTime.of(2026, 7, 14, 12, 0));
            return note;
        });

        var response = service.addNote(
                tenantId, userId, ticketId, new TicketDtos.NoteRequest("  Internal follow-up  "));

        assertEquals("Internal follow-up", response.content());
        assertEquals(userId, response.authorId());
        assertEquals("Ada", response.authorName());
        ArgumentCaptor<TicketNote> note = ArgumentCaptor.forClass(TicketNote.class);
        verify(noteRepository).save(note.capture());
        assertEquals(ticket.getTenantId(), note.getValue().getTenantId());
        assertEquals(ticket.getId(), note.getValue().getTicketId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void publicTicketPublishesOneCustomerEmailEventForNewTicket() throws Exception {
        TicketRepository tickets = mock(TicketRepository.class);
        TenantIdentityApi tenants = mock(TenantIdentityApi.class);
        ChatApi chats = mock(ChatApi.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TicketService ticketService = new TicketService(
                tickets, mock(TicketNoteRepository.class), tenants, chats, events);
        UUID chatbotId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(tenants.getTenant(tenantId)).thenReturn(new TenantIdentityApi.TenantSnapshot(tenantId, "Acme Support"));
        when(chats.validateExternalConversation(tenantId, chatbotId, tokenId, sessionId))
                .thenReturn(new ChatApi.ExternalConversationContext(sessionId, "visitor-1", "WIDGET", "vi-VN"));
        when(tickets.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            saved.setId(ticketId);
            return saved;
        });
        var principal = new IntegrationAccessApi.IntegrationPrincipal(
                tokenId, tenantId, chatbotId, UUID.randomUUID(),
                List.of(IntegrationAccessApi.WIDGET_SCOPE));

        ticketService.createPublic(principal, new TicketDtos.CreatePublicRequest(
                sessionId, "Customer@Example.com", "Ada", "Refund request",
                "Charged twice"), "ticket-key");

        ArgumentCaptor<TicketCreatedEvent> event = ArgumentCaptor.forClass(TicketCreatedEvent.class);
        verify(events).publishEvent(event.capture());
        TicketCreatedEvent created = event.getValue();
        assertEquals(ticketId, created.getTicketId());
        assertEquals("customer@example.com", created.getCustomerEmail());
        assertEquals("vi-VN", created.getLocale());
    }

    @Test
    void idempotentTicketReplayDoesNotSendAnotherCustomerEmail() {
        TicketRepository tickets = mock(TicketRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Ticket existing = ticket();
        when(tickets.findByTenantIdAndIdempotencyKey(tenantId, "ticket-key"))
                .thenReturn(Optional.of(existing));
        TicketService ticketService = new TicketService(
                tickets, mock(TicketNoteRepository.class), mock(TenantIdentityApi.class),
                mock(ChatApi.class), events);
        var principal = new IntegrationAccessApi.IntegrationPrincipal(
                UUID.randomUUID(), tenantId, existing.getChatbotId(), UUID.randomUUID(),
                List.of(IntegrationAccessApi.WIDGET_SCOPE));

        ticketService.createPublic(principal, new TicketDtos.CreatePublicRequest(
                existing.getChatSessionId(), existing.getCustomerEmail(), null,
                existing.getTitle(), existing.getDescription()), "ticket-key");

        verify(events, never()).publishEvent(any());
    }

    private Ticket ticket() {
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantId);
        ticket.setChatbotId(UUID.randomUUID());
        ticket.setChatSessionId(UUID.randomUUID());
        ticket.setCustomerEmail("customer@example.com");
        ticket.setSource(TicketSource.WIDGET);
        ticket.setTitle("Refund request");
        ticket.setDescription("Customer needs help.");
        ticket.setCreatedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        ticket.setUpdatedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        return ticket;
    }

    private TenantIdentityApi.UserSnapshot userSnapshot(UUID id, String name) {
        return new TenantIdentityApi.UserSnapshot(id, tenantId, name,
                name.toLowerCase() + "@example.com", "USER", "ACTIVE");
    }
}
