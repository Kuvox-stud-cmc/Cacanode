package com.cacanode.api.support.repository;

import com.cacanode.api.support.enums.TicketStatus;
import com.cacanode.api.support.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {
    Page<Ticket> findByTenantId(UUID tenantId, Pageable pageable);
    Page<Ticket> findByTenantIdAndStatus(UUID tenantId, TicketStatus status, Pageable pageable);
    Optional<Ticket> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Ticket> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    default Optional<Ticket> findByIdAndTenant_Id(UUID id, UUID tenantId) {
        return findByIdAndTenantId(id, tenantId);
    }

    default Optional<Ticket> findByTenant_IdAndIdempotencyKey(UUID tenantId, String idempotencyKey) {
        return findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
    }
}
