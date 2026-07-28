package com.cacanode.api.support.repository;

import com.cacanode.api.support.model.TicketNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketNoteRepository extends JpaRepository<TicketNote, UUID> {
    List<TicketNote> findByTicketIdAndTenantIdOrderByCreatedAtAsc(UUID ticketId, UUID tenantId);

    default List<TicketNote> findByTicket_IdAndTenant_IdOrderByCreatedAtAsc(UUID ticketId, UUID tenantId) {
        return findByTicketIdAndTenantIdOrderByCreatedAtAsc(ticketId, tenantId);
    }
}
