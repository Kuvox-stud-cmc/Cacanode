package com.cacanode.api.tenant.repository;

import com.cacanode.api.tenant.enums.TicketStatus;
import com.cacanode.api.tenant.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {
    Page<Ticket> findByTenant_Id(UUID tenantId, Pageable pageable);
    Page<Ticket> findByTenant_IdAndStatus(UUID tenantId, TicketStatus status, Pageable pageable);
    Optional<Ticket> findByIdAndTenant_Id(UUID id, UUID tenantId);
    Optional<Ticket> findByTenant_IdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
