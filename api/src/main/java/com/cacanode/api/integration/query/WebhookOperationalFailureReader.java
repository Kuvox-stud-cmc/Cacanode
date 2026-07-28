package com.cacanode.api.integration.query;

import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import com.cacanode.api.common.service.OperationalFailureQueryExecutor;
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
public class WebhookOperationalFailureReader implements OperationalFailureReadApi {
    private static final String SQL = """
            SELECT o.id failure_id,o.tenant_id,o.id resource_id,'WEBHOOK_EVENT' resource_type,
              CASE WHEN o.status='FAILED' THEN 'FAILED' ELSE 'RETRYING' END state,
              CASE WHEN o.status='FAILED' THEN 'ERROR' ELSE 'WARNING' END severity,
              CASE WHEN o.status='FAILED' THEN 'WEBHOOK_DELIVERY_FAILED' ELSE 'WEBHOOK_DELIVERY_RETRY' END error_code,
              o.attempt_count attempts,o.created_at first_seen_at,
              GREATEST(o.updated_at,COALESCE(MAX(d.created_at) FILTER (WHERE d.delivered_at IS NULL),o.updated_at)) last_seen_at,
              CASE WHEN o.status='FAILED' THEN NULL ELSE o.next_attempt_at END next_retry_at
            FROM webhook_outbox o LEFT JOIN webhook_deliveries d ON d.event_id=o.id
            WHERE o.status='FAILED' OR (o.status='PENDING' AND o.attempt_count>0)
            GROUP BY o.id,o.tenant_id,o.status,o.attempt_count,o.created_at,o.updated_at,o.next_attempt_at
            """;
    private final OperationalFailureQueryExecutor queries;
    public Set<Source> sources() { return Set.of(Source.WEBHOOKS); }
    public Summary summary(Source source, Optional<UUID> tenantId) { return queries.summary(SQL, List.of(), source, tenantId); }
    public Page failures(Source source, Query query) { return queries.page(SQL, List.of(), source, query); }
    public List<Failure> recent(Source source, Optional<UUID> tenantId, int limit) {
        return queries.recent(SQL, List.of(), source, tenantId, limit);
    }
}
