package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.integration.service.WebhookService;
import com.cacanode.api.tenant.dto.TicketDtos;
import com.cacanode.api.tenant.enums.TicketPriority;
import com.cacanode.api.tenant.enums.TicketSource;
import com.cacanode.api.tenant.enums.TicketStatus;
import com.cacanode.api.tenant.model.Ticket;
import com.cacanode.api.tenant.model.TicketNote;
import com.cacanode.api.tenant.model.User;
import com.cacanode.api.tenant.repository.ChatbotRepository;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.TicketNoteRepository;
import com.cacanode.api.tenant.repository.TicketRepository;
import com.cacanode.api.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketNoteRepository noteRepository;
    private final TenantRepository tenantRepository;
    private final ChatbotRepository chatbotRepository;
    private final IntegrationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final WebhookService webhookService;

    @Transactional
    public TicketDtos.Response createPublic(
            IntegrationTokenService.Principal principal,
            TicketDtos.CreatePublicRequest request,
            String idempotencyKey
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = ticketRepository.findByTenant_IdAndIdempotencyKey(
                    principal.tenantId(), idempotencyKey
            );
            if (existing.isPresent()) {
                return toResponse(existing.get(), List.of());
            }
        }

        SessionRow session = externalSession(principal, request.sessionId());
        Ticket ticket = new Ticket();
        ticket.setTenant(tenantRepository.getReferenceById(principal.tenantId()));
        ticket.setChatbot(chatbotRepository.getReferenceById(principal.chatbotId()));
        ticket.setChatSessionId(request.sessionId());
        ticket.setIntegrationToken(tokenRepository.getReferenceById(principal.tokenId()));
        ticket.setExternalUserId(session.externalUserId());
        ticket.setCustomerName(clean(request.customerName()));
        ticket.setCustomerEmail(request.customerEmail().trim().toLowerCase());
        ticket.setSource(TicketSource.valueOf(session.channel()));
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setIdempotencyKey(clean(idempotencyKey));
        ticket = ticketRepository.save(ticket);

        Map<String, Object> event = new HashMap<>();
        event.put("ticketId", ticket.getId());
        event.put("conversationId", ticket.getChatSessionId());
        event.put("chatbotId", principal.chatbotId());
        event.put("customerEmail", ticket.getCustomerEmail());
        event.put("title", ticket.getTitle());
        event.put("status", ticket.getStatus().name());
        webhookService.enqueue(principal.tenantId(), "ticket.created", ticket.getId(), event);
        return toResponse(ticket, List.of());
    }

    @Transactional(readOnly = true)
    public Page<TicketDtos.Response> list(UUID tenantId, TicketStatus status, Pageable pageable) {
        Page<Ticket> tickets = status == null
                ? ticketRepository.findByTenant_Id(tenantId, pageable)
                : ticketRepository.findByTenant_IdAndStatus(tenantId, status, pageable);
        return tickets.map(ticket -> toResponse(ticket, List.of()));
    }

    @Transactional(readOnly = true)
    public TicketDtos.Response get(UUID tenantId, UUID ticketId) {
        Ticket ticket = find(tenantId, ticketId);
        return toResponse(ticket, noteRepository.findByTicket_IdAndTenant_IdOrderByCreatedAtAsc(
                ticketId, tenantId
        ));
    }

    @Transactional
    public TicketDtos.Response update(UUID tenantId, UUID ticketId, TicketDtos.UpdateRequest request) {
        Ticket ticket = find(tenantId, ticketId);
        if (request.status() != null) {
            ticket.setStatus(request.status());
            ticket.setResolvedAt(
                    request.status() == TicketStatus.RESOLVED || request.status() == TicketStatus.CLOSED
                            ? LocalDateTime.now() : null
            );
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (Boolean.TRUE.equals(request.clearAssignee())) {
            ticket.setAssignedTo(null);
        } else if (request.assignedTo() != null) {
            User user = userRepository.findByIdAndTenant_Id(request.assignedTo(), tenantId)
                    .orElseThrow(() -> new BadRequestException("Assignee does not belong to this tenant"));
            ticket.setAssignedTo(user);
        }
        return toResponse(ticket, noteRepository.findByTicket_IdAndTenant_IdOrderByCreatedAtAsc(
                ticketId, tenantId
        ));
    }

    @Transactional
    public TicketDtos.NoteResponse addNote(
            UUID tenantId,
            UUID userId,
            UUID ticketId,
            TicketDtos.NoteRequest request
    ) {
        Ticket ticket = find(tenantId, ticketId);
        User author = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        TicketNote note = new TicketNote();
        note.setTicket(ticket);
        note.setTenant(ticket.getTenant());
        note.setAuthor(author);
        note.setContent(request.content().trim());
        return toNote(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.Assignee> assignees(UUID tenantId) {
        return userRepository.findByTenant_IdOrderByFullNameAsc(tenantId).stream()
                .map(user -> new TicketDtos.Assignee(user.getId(), user.getFullName(), user.getEmail()))
                .toList();
    }

    private SessionRow externalSession(
            IntegrationTokenService.Principal principal,
            UUID sessionId
    ) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT external_user_id, channel
                    FROM chat_sessions
                    WHERE id = ? AND tenant_id = ? AND chatbot_id = ? AND integration_token_id = ?
                      AND channel IN ('WIDGET', 'CUSTOM_API')
                    """,
                    (rs, rowNum) -> new SessionRow(rs.getString("external_user_id"), rs.getString("channel")),
                    sessionId, principal.tenantId(), principal.chatbotId(), principal.tokenId()
            );
        } catch (EmptyResultDataAccessException e) {
            throw new BadRequestException("Customer chat session was not found");
        }
    }

    private Ticket find(UUID tenantId, UUID ticketId) {
        return ticketRepository.findByIdAndTenant_Id(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket was not found"));
    }

    private TicketDtos.Response toResponse(Ticket ticket, List<TicketNote> notes) {
        return new TicketDtos.Response(
                ticket.getId(), ticket.getChatbot().getId(), ticket.getChatSessionId(),
                ticket.getExternalUserId(), ticket.getCustomerName(), ticket.getCustomerEmail(),
                ticket.getSource(), ticket.getTitle(), ticket.getDescription(), ticket.getStatus(),
                ticket.getPriority(), ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getFullName(),
                ticket.getResolvedAt(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                notes.stream().map(this::toNote).toList()
        );
    }

    private TicketDtos.NoteResponse toNote(TicketNote note) {
        return new TicketDtos.NoteResponse(
                note.getId(), note.getAuthor().getId(), note.getAuthor().getFullName(),
                note.getContent(), note.getCreatedAt()
        );
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record SessionRow(String externalUserId, String channel) {
    }
}
