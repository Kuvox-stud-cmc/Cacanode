package com.cacanode.api.support.service;

import com.cacanode.api.support.api.SupportAnalyticsExportApi;
import com.cacanode.api.support.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportAnalyticsExportApiImpl implements SupportAnalyticsExportApi {
    private final TicketRepository repository;

    @Override
    public TicketPage projectionTickets(int page, int size) {
        var result = repository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new TicketPage(result.stream().map(ticket -> new TicketSnapshot(
                ticket.getId(), ticket.getTenantId(), ticket.getStatus().name(),
                ticket.getPriority().name(), ticket.getCreatedAt(), ticket.getResolvedAt(),
                ticket.getUpdatedAt())).toList(), result.hasNext());
    }
}
