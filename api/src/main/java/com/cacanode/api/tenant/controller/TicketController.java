package com.cacanode.api.tenant.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.tenant.dto.TicketDtos;
import com.cacanode.api.tenant.enums.TicketPriority;
import com.cacanode.api.tenant.enums.TicketSource;
import com.cacanode.api.tenant.enums.TicketStatus;
import com.cacanode.api.tenant.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/tenants/me/tickets")
@RequiredArgsConstructor
public class TicketController extends BaseController {
    private final TicketService ticketService;

    public Page<TicketDtos.Response> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketSource source,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request
    ) {
        return ticketService.list(
                getTenantId(request), status, priority, source, assignedTo,
                Boolean.TRUE.equals(unassigned), page, size
        );
    }

    @GetMapping
    public Page<TicketDtos.Response> listResponse(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketSource source,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "created_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(value = "created_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            HttpServletRequest request
    ) {
        return ticketService.list(
                getTenantId(request), status, priority, source, assignedTo,
                Boolean.TRUE.equals(unassigned), page, size, query, createdFrom, createdTo,
                sort, direction);
    }

    @GetMapping("/assignees")
    public List<TicketDtos.Assignee> assignees(HttpServletRequest request) {
        return ticketService.assignees(getTenantId(request));
    }

    @GetMapping("/{ticketId}")
    public TicketDtos.Response get(@PathVariable UUID ticketId, HttpServletRequest request) {
        return ticketService.get(getTenantId(request), ticketId);
    }

    @PatchMapping("/{ticketId}")
    public TicketDtos.Response update(
            @PathVariable UUID ticketId,
            @RequestBody TicketDtos.UpdateRequest body,
            HttpServletRequest request
    ) {
        return ticketService.update(getTenantId(request), ticketId, body);
    }

    @PostMapping("/{ticketId}/notes")
    public TicketDtos.NoteResponse addNote(
            @PathVariable UUID ticketId,
            @Valid @RequestBody TicketDtos.NoteRequest body,
            HttpServletRequest request
    ) {
        return ticketService.addNote(getTenantId(request), getUserId(request), ticketId, body);
    }
}
