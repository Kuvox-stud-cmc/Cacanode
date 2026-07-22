package com.cacanode.api.support.controller;

import com.cacanode.api.support.dto.TicketDtos;
import com.cacanode.api.tenant.api.IntegrationAccessApi;
import com.cacanode.api.support.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/external/tickets")
@RequiredArgsConstructor
public class PublicTicketController {
    private final IntegrationAccessApi tokenService;
    private final TicketService ticketService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDtos.Response create(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TicketDtos.CreatePublicRequest body
    ) {
        var principal = tokenService.authenticateForAnyChatScope(authorization);
        return ticketService.createPublic(principal, body, idempotencyKey);
    }
}
