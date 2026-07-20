package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.common.event.TicketCreatedEvent;
import com.cacanode.api.integration.service.WebhookService;
import com.cacanode.api.tenant.dto.TicketDtos;
import com.cacanode.api.tenant.enums.TicketPriority;
import com.cacanode.api.tenant.enums.TicketSource;
import com.cacanode.api.tenant.enums.TicketStatus;
import com.cacanode.api.tenant.model.Ticket;
import com.cacanode.api.tenant.model.Tenant;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 200;

    private final TicketRepository ticketRepository;
    private final TicketNoteRepository noteRepository;
    private final TenantRepository tenantRepository;
    private final ChatbotRepository chatbotRepository;
    private final IntegrationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final WebhookService webhookService;
    private final ApplicationEventPublisher eventPublisher;

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
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant was not found"));
        Ticket ticket = new Ticket();
        ticket.setTenant(tenant);
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
        eventPublisher.publishEvent(new TicketCreatedEvent(
                this, principal.tenantId(), ticket.getId(), tenant.getName(),
                ticket.getCustomerEmail(), ticket.getCustomerName(), ticket.getTitle(),
                ticket.getDescription(), session.locale()));
        return toResponse(ticket, List.of());
    }

    @Transactional(readOnly = true)
    public Page<TicketDtos.Response> list(
            UUID tenantId,
            TicketStatus status,
            TicketPriority priority,
            TicketSource source,
            UUID assignedTo,
            boolean unassigned,
            Integer page,
            Integer size
    ) {
        return list(tenantId, status, priority, source, assignedTo, unassigned, page, size,
                null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<TicketDtos.Response> list(
            UUID tenantId,
            TicketStatus status,
            TicketPriority priority,
            TicketSource source,
            UUID assignedTo,
            boolean unassigned,
            Integer page,
            Integer size,
            String searchText,
            LocalDate createdFrom,
            LocalDate createdTo,
            String sort,
            String direction
    ) {
        int requestedPage = page == null ? 0 : page;
        int requestedSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (requestedPage < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (requestedSize < 1 || requestedSize > MAX_PAGE_SIZE) {
            throw new BadRequestException("Size must be between 1 and 100");
        }
        if (assignedTo != null && unassigned) {
            throw new BadRequestException("Assigned user and unassigned filters cannot be combined");
        }
        String query = searchText == null || searchText.isBlank() ? null : searchText.strip();
        if (query != null && query.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Search text must be 200 characters or fewer");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BadRequestException("Creation start date must not be after end date");
        }
        String requestedSort = normalizeTicketSort(sort);
        boolean ascending = normalizeDirection(direction).equals("asc");

        Specification<Ticket> specification = (root, criteriaQuery, builder) ->
                builder.equal(root.get("tenant").get("id"), tenantId);
        if (status != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.get("status"), status));
        }
        if (priority != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.get("priority"), priority));
        }
        if (source != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.get("source"), source));
        }
        if (query != null) {
            String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
            specification = specification.and((root, criteriaQuery, builder) -> builder.or(
                    builder.like(builder.lower(builder.function("str", String.class, root.get("id"))), pattern, '\\'),
                    builder.like(builder.lower(root.get("title")), pattern, '\\'),
                    builder.like(builder.lower(root.get("description")), pattern, '\\'),
                    builder.like(builder.lower(root.get("customerName")), pattern, '\\'),
                    builder.like(builder.lower(root.get("customerEmail")), pattern, '\\'),
                    builder.like(builder.lower(root.get("externalUserId")), pattern, '\\')
            ));
        }
        if (createdFrom != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom.atStartOfDay()));
        }
        if (createdTo != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.lessThan(root.get("createdAt"), createdTo.plusDays(1).atStartOfDay()));
        }
        if (assignedTo != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.get("assignedTo").get("id"), assignedTo));
        } else if (unassigned) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.isNull(root.get("assignedTo")));
        }
        if (requestedSort.equals("priority") || requestedSort.equals("customer")) {
            specification = specification.and((root, criteriaQuery, builder) -> {
                if (criteriaQuery.getResultType() != Long.class && criteriaQuery.getResultType() != long.class) {
                jakarta.persistence.criteria.Expression<?> primary = switch (requestedSort) {
                    case "priority" -> builder.<Integer>selectCase()
                            .when(builder.equal(root.get("priority"), TicketPriority.URGENT), 4)
                            .when(builder.equal(root.get("priority"), TicketPriority.HIGH), 3)
                            .when(builder.equal(root.get("priority"), TicketPriority.NORMAL), 2)
                            .otherwise(1);
                    case "customer" -> builder.lower(builder.coalesce(
                            root.<String>get("customerName"), root.<String>get("customerEmail")));
                    default -> builder.lower(builder.coalesce(
                            root.<String>get("customerName"), root.<String>get("customerEmail")));
                };
                criteriaQuery.orderBy(
                        ascending ? builder.asc(primary) : builder.desc(primary),
                        ascending ? builder.asc(root.get("id")) : builder.desc(root.get("id"))
                );
                }
                return builder.conjunction();
            });
        }

        Sort pageableSort = Sort.unsorted();
        if (requestedSort.equals("created") || requestedSort.equals("updated")) {
            Sort.Direction sortDirection = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
            pageableSort = Sort.by(
                    new Sort.Order(sortDirection, requestedSort.equals("updated") ? "updatedAt" : "createdAt"),
                    new Sort.Order(sortDirection, "id"));
        }

        var pageable = PageRequest.of(
                requestedPage,
                requestedSize,
                pageableSort
        );
        return ticketRepository.findAll(specification, pageable)
                .map(ticket -> toResponse(ticket, List.of()));
    }

    private String normalizeTicketSort(String value) {
        if (value == null || value.isBlank() || "created".equalsIgnoreCase(value)
                || "createdAt".equalsIgnoreCase(value)) return "created";
        if ("updated".equalsIgnoreCase(value) || "updatedAt".equalsIgnoreCase(value)) return "updated";
        if ("priority".equalsIgnoreCase(value)) return "priority";
        if ("customer".equalsIgnoreCase(value)) return "customer";
        throw new BadRequestException("Sort must be created, updated, priority, or customer");
    }

    private String normalizeDirection(String value) {
        if (value == null || value.isBlank() || "desc".equalsIgnoreCase(value)) return "desc";
        if ("asc".equalsIgnoreCase(value)) return "asc";
        throw new BadRequestException("Direction must be asc or desc");
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
                    SELECT external_user_id, channel, locale
                    FROM chat_sessions
                    WHERE id = ? AND tenant_id = ? AND chatbot_id = ? AND integration_token_id = ?
                      AND channel IN ('WIDGET', 'CUSTOM_API')
                    """,
                    (rs, rowNum) -> new SessionRow(
                            rs.getString("external_user_id"), rs.getString("channel"),
                            rs.getString("locale")),
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

    private record SessionRow(String externalUserId, String channel, String locale) {
    }
}
