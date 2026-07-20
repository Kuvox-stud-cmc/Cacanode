package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.integration.service.WebhookService;
import com.cacanode.api.tenant.dto.TicketDtos;
import com.cacanode.api.tenant.enums.TicketPriority;
import com.cacanode.api.tenant.enums.TicketSource;
import com.cacanode.api.tenant.enums.TicketStatus;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.model.Ticket;
import com.cacanode.api.tenant.model.TicketNote;
import com.cacanode.api.tenant.model.User;
import com.cacanode.api.tenant.repository.ChatbotRepository;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.TicketNoteRepository;
import com.cacanode.api.tenant.repository.TicketRepository;
import com.cacanode.api.tenant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.context.ApplicationEventPublisher;

import com.cacanode.api.common.event.TicketCreatedEvent;

import java.sql.ResultSet;

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
    private UserRepository userRepository;
    private TicketService service;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);
        noteRepository = mock(TicketNoteRepository.class);
        userRepository = mock(UserRepository.class);
        service = new TicketService(
                ticketRepository,
                noteRepository,
                mock(TenantRepository.class),
                mock(ChatbotRepository.class),
                mock(IntegrationTokenRepository.class),
                userRepository,
                mock(JdbcTemplate.class),
                mock(WebhookService.class),
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
        when(ticketRepository.findByIdAndTenant_Id(ticketId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(tenantId, ticketId));
        verify(noteRepository, never()).findByTicket_IdAndTenant_IdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void statusUpdatesSetAndClearResolvedTimestamp() {
        Ticket ticket = ticket();
        when(ticketRepository.findByIdAndTenant_Id(ticketId, tenantId)).thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicket_IdAndTenant_IdOrderByCreatedAtAsc(ticketId, tenantId))
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
        User assignee = user(userId, "Assignee");
        when(ticketRepository.findByIdAndTenant_Id(ticketId, tenantId)).thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicket_IdAndTenant_IdOrderByCreatedAtAsc(ticketId, tenantId))
                .thenReturn(List.of());
        when(userRepository.findByIdAndTenant_Id(userId, tenantId)).thenReturn(Optional.of(assignee));

        service.update(tenantId, ticketId, new TicketDtos.UpdateRequest(
                null, TicketPriority.URGENT, userId, false));
        assertEquals(assignee, ticket.getAssignedTo());
        assertEquals(TicketPriority.URGENT, ticket.getPriority());

        service.update(tenantId, ticketId, new TicketDtos.UpdateRequest(
                null, null, null, true));
        assertNull(ticket.getAssignedTo());

        UUID otherTenantUser = UUID.randomUUID();
        when(userRepository.findByIdAndTenant_Id(otherTenantUser, tenantId)).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.update(
                tenantId, ticketId, new TicketDtos.UpdateRequest(null, null, otherTenantUser, false)));
    }

    @Test
    void noteIsTrimmedTenantScopedAndReturnedWithAuthor() {
        Ticket ticket = ticket();
        User author = user(userId, "Ada");
        when(ticketRepository.findByIdAndTenant_Id(ticketId, tenantId)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdAndTenant_Id(userId, tenantId)).thenReturn(Optional.of(author));
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
        assertEquals(ticket.getTenant(), note.getValue().getTenant());
        assertEquals(ticket, note.getValue().getTicket());
    }

    @SuppressWarnings("unchecked")
    @Test
    void publicTicketPublishesOneCustomerEmailEventForNewTicket() throws Exception {
        TicketRepository tickets = mock(TicketRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        ChatbotRepository chatbots = mock(ChatbotRepository.class);
        IntegrationTokenRepository tokens = mock(IntegrationTokenRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WebhookService webhooks = mock(WebhookService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TicketService ticketService = new TicketService(
                tickets, mock(TicketNoteRepository.class), tenants, chatbots, tokens,
                mock(UserRepository.class), jdbc, webhooks, events);
        UUID chatbotId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Acme Support");
        Chatbot chatbot = new Chatbot();
        chatbot.setId(chatbotId);
        IntegrationToken integrationToken = new IntegrationToken();
        integrationToken.setId(tokenId);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(chatbots.getReferenceById(chatbotId)).thenReturn(chatbot);
        when(tokens.getReferenceById(tokenId)).thenReturn(integrationToken);
        when(jdbc.queryForObject(
                any(String.class), any(RowMapper.class), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("external_user_id")).thenReturn("visitor-1");
                    when(resultSet.getString("channel")).thenReturn("WIDGET");
                    when(resultSet.getString("locale")).thenReturn("vi-VN");
                    return mapper.mapRow(resultSet, 0);
                });
        when(tickets.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            saved.setId(ticketId);
            return saved;
        });
        var principal = new IntegrationTokenService.Principal(
                tokenId, tenantId, chatbotId, UUID.randomUUID(),
                List.of(IntegrationTokenService.WIDGET_SCOPE));

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
        when(tickets.findByTenant_IdAndIdempotencyKey(tenantId, "ticket-key"))
                .thenReturn(Optional.of(existing));
        TicketService ticketService = new TicketService(
                tickets, mock(TicketNoteRepository.class), mock(TenantRepository.class),
                mock(ChatbotRepository.class), mock(IntegrationTokenRepository.class),
                mock(UserRepository.class), mock(JdbcTemplate.class), mock(WebhookService.class), events);
        var principal = new IntegrationTokenService.Principal(
                UUID.randomUUID(), tenantId, existing.getChatbot().getId(), UUID.randomUUID(),
                List.of(IntegrationTokenService.WIDGET_SCOPE));

        ticketService.createPublic(principal, new TicketDtos.CreatePublicRequest(
                existing.getChatSessionId(), existing.getCustomerEmail(), null,
                existing.getTitle(), existing.getDescription()), "ticket-key");

        verify(events, never()).publishEvent(any());
    }

    private Ticket ticket() {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        Chatbot chatbot = new Chatbot();
        chatbot.setId(UUID.randomUUID());
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenant(tenant);
        ticket.setChatbot(chatbot);
        ticket.setChatSessionId(UUID.randomUUID());
        ticket.setCustomerEmail("customer@example.com");
        ticket.setSource(TicketSource.WIDGET);
        ticket.setTitle("Refund request");
        ticket.setDescription("Customer needs help.");
        ticket.setCreatedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        ticket.setUpdatedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        return ticket;
    }

    private User user(UUID id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        return user;
    }
}
