package com.cacanode.api.tenant.repository;

import com.cacanode.api.tenant.model.TicketNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketNoteRepository extends JpaRepository<TicketNote, UUID> {
    List<TicketNote> findByTicket_IdAndTenant_IdOrderByCreatedAtAsc(UUID ticketId, UUID tenantId);
}
