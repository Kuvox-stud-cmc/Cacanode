package com.cacanode.api.support.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.chat.api.ChatApi;
import com.cacanode.api.support.api.event.TicketCreatedEvent;
import com.cacanode.api.support.api.event.TicketStatusChangedEvent;
import com.cacanode.api.support.dto.TicketDtos;
import com.cacanode.api.support.enums.TicketPriority;
import com.cacanode.api.support.enums.TicketSource;
import com.cacanode.api.support.enums.TicketStatus;
import com.cacanode.api.support.model.Ticket;
import com.cacanode.api.support.model.TicketNote;
import com.cacanode.api.support.repository.TicketNoteRepository;
import com.cacanode.api.support.repository.TicketRepository;
import com.cacanode.api.tenant.api.IntegrationAccessApi.IntegrationPrincipal;
import com.cacanode.api.tenant.api.TenantIdentityApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final TenantIdentityApi tenantIdentityApi;
    private final ChatApi chatApi;
    private final ApplicationEventPublisher eventPublisher;
    @Autowired(required = false)
    private DurableEventPublisher durableEventPublisher;

    @Transactional
    public TicketDtos.Response createPublic(
            IntegrationPrincipal principal,
            TicketDtos.CreatePublicRequest request,
            String idempotencyKey
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = ticketRepository.findByTenantIdAndIdempotencyKey(
                    principal.tenantId(), idempotencyKey
            );
            if (existing.isPresent()) {
                return toResponse(existing.get(), List.of());
            }
        }

        var session = chatApi.validateExternalConversation(
                principal.tenantId(), principal.chatbotId(), principal.tokenId(), request.sessionId());
        var tenant = tenantIdentityApi.getTenant(principal.tenantId());
        Ticket ticket = new Ticket();
        ticket.setTenantId(principal.tenantId());
        ticket.setChatbotId(principal.chatbotId());
        ticket.setChatSessionId(request.sessionId());
        ticket.setIntegrationTokenId(principal.tokenId());
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
        publishBusinessEvent("support.ticket.created.v1", new TicketCreatedEvent(
                principal.tenantId(), ticket.getId(), ticket.getChatSessionId(), principal.chatbotId(), tenant.name(),
                ticket.getCustomerEmail(), ticket.getCustomerName(), ticket.getTitle(),
                ticket.getDescription(), ticket.getStatus().name(), session.locale(), Map.copyOf(event),
                ticket.getPriority().name(), timestamp(ticket.getCreatedAt()), timestamp(ticket.getUpdatedAt())));
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
                builder.equal(root.get("tenantId"), tenantId);
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
                    builder.equal(root.get("assignedToId"), assignedTo));
        } else if (unassigned) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.isNull(root.get("assignedToId")));
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
        return toResponse(ticket, noteRepository.findByTicketIdAndTenantIdOrderByCreatedAtAsc(
                ticketId, tenantId
        ));
    }

    @Transactional
    public TicketDtos.Response update(UUID tenantId, UUID ticketId, TicketDtos.UpdateRequest request) {
        Ticket ticket = find(tenantId, ticketId);
        TicketStatus previousStatus = ticket.getStatus();
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
            ticket.setAssignedToId(null);
        } else if (request.assignedTo() != null) {
            try {
                tenantIdentityApi.requireUser(tenantId, request.assignedTo());
            } catch (ResourceNotFoundException exception) {
                throw new BadRequestException("Assignee does not belong to this tenant");
            }
            ticket.setAssignedToId(request.assignedTo());
        }
        if (request.status() != null && request.status() != previousStatus) {
            publishBusinessEvent("support.ticket.status-changed.v1",
                    new TicketStatusChangedEvent(tenantId, ticketId, request.status().name(),
                            ticket.getResolvedAt(), LocalDateTime.now()));
        }
        return toResponse(ticket, noteRepository.findByTicketIdAndTenantIdOrderByCreatedAtAsc(
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
        tenantIdentityApi.requireUser(tenantId, userId);
        TicketNote note = new TicketNote();
        note.setTicketId(ticket.getId());
        note.setTenantId(ticket.getTenantId());
        note.setAuthorId(userId);
        note.setContent(request.content().trim());
        return toNote(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.Assignee> assignees(UUID tenantId) {
        return tenantIdentityApi.listUsers(tenantId).stream()
                .map(user -> new TicketDtos.Assignee(user.id(), user.fullName(), user.email()))
                .toList();
    }

    private Ticket find(UUID tenantId, UUID ticketId) {
        return ticketRepository.findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket was not found"));
    }

    private TicketDtos.Response toResponse(Ticket ticket, List<TicketNote> notes) {
        return new TicketDtos.Response(
                ticket.getId(), ticket.getChatbotId(), ticket.getChatSessionId(),
                ticket.getExternalUserId(), ticket.getCustomerName(), ticket.getCustomerEmail(),
                ticket.getSource(), ticket.getTitle(), ticket.getDescription(), ticket.getStatus(),
                ticket.getPriority(), ticket.getAssignedToId(), assigneeName(ticket),
                ticket.getResolvedAt(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                notes.stream().map(this::toNote).toList()
        );
    }

    private TicketDtos.NoteResponse toNote(TicketNote note) {
        return new TicketDtos.NoteResponse(
                note.getId(), note.getAuthorId(), tenantIdentityApi.requireUser(note.getTenantId(), note.getAuthorId()).fullName(),
                note.getContent(), note.getCreatedAt()
        );
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String assigneeName(Ticket ticket) {
        return ticket.getAssignedToId() == null ? null
                : tenantIdentityApi.requireUser(ticket.getTenantId(), ticket.getAssignedToId()).fullName();
    }

    private void publishBusinessEvent(String stableType, Object event) {
        if (durableEventPublisher != null) {
            durableEventPublisher.publish(stableType, 1, event);
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    private LocalDateTime timestamp(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }
}
