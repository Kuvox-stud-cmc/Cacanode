package com.cacanode.api.common.service;

import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModuleEventOperationalFailureReader implements OperationalFailureReadApi {
    private static final String SQL = """
            SELECT event_id failure_id,NULL::uuid tenant_id,event_id resource_id,'MODULE_EVENT' resource_type,
              CASE WHEN status='DEAD' THEN 'DEAD' ELSE 'RETRYING' END state,
              CASE WHEN status='DEAD' THEN 'CRITICAL' ELSE 'WARNING' END severity,
              CASE WHEN status='DEAD' THEN 'MODULE_EVENT_DEAD' ELSE 'MODULE_EVENT_RETRY' END error_code,
              attempts,created_at first_seen_at,COALESCE(next_attempt_at,created_at) last_seen_at,
              CASE WHEN status='DEAD' THEN NULL ELSE next_attempt_at END next_retry_at
            FROM module_event_outbox WHERE status='DEAD' OR (status='PENDING' AND attempts>0)
            """;
    private final OperationalFailureQueryExecutor queries;

    public Set<Source> sources() { return Set.of(Source.MODULE_EVENTS); }
    public Summary summary(Source source, Optional<UUID> tenantId) {
        return source == Source.MODULE_EVENTS && tenantId.isEmpty()
                ? queries.summary(SQL, List.of(), source, tenantId) : emptySummary();
    }
    public Page failures(Source source, Query query) {
        return source == Source.MODULE_EVENTS && query.tenantId().isEmpty()
                ? queries.page(SQL, List.of(), source, query) : new Page(List.of(), 0);
    }
    public List<Failure> recent(Source source, Optional<UUID> tenantId, int limit) {
        return source == Source.MODULE_EVENTS && tenantId.isEmpty()
                ? queries.recent(SQL, List.of(), source, tenantId, limit) : List.of();
    }
    private Summary emptySummary() { return new Summary(0, java.util.Map.of(), java.util.Map.of()); }
}
