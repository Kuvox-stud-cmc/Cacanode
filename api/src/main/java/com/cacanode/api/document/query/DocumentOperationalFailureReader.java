package com.cacanode.api.document.query;

import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import com.cacanode.api.common.config.OperationalFailureProperties;
import com.cacanode.api.common.service.OperationalFailureQueryExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentOperationalFailureReader implements OperationalFailureReadApi {
    private static final String SQL = """
            SELECT d.id failure_id,d.tenant_id,d.id resource_id,'DOCUMENT' resource_type,'FAILED' state,
              'ERROR' severity,'DOCUMENT_PROCESSING_FAILED' error_code,1 attempts,
              d.created_at first_seen_at,d.updated_at last_seen_at,NULL::timestamp next_retry_at
            FROM documents d WHERE d.status='FAILED'
            UNION ALL
            SELECT o.id,d.tenant_id,d.id,'DOCUMENT',CASE WHEN o.status='DEAD' THEN 'DEAD' ELSE 'RETRYING' END,
              CASE WHEN o.status='DEAD' THEN 'CRITICAL' ELSE 'WARNING' END,'DOCUMENT_PUBLICATION_RETRY',
              o.attempt_count,o.created_at,o.updated_at,CASE WHEN o.status='DEAD' THEN NULL ELSE o.next_attempt_at END
            FROM internal_event_outbox o JOIN documents d ON d.id=o.aggregate_id
            WHERE o.aggregate_type='DOCUMENT' AND (o.status='DEAD' OR (o.status='PENDING' AND o.attempt_count>0))
            UNION ALL
            SELECT i.event_id,d.tenant_id,d.id,'DOCUMENT','STALLED','ERROR','DOCUMENT_CHECKPOINT_STALLED',0,
              i.received_at,i.received_at,NULL::timestamp
            FROM internal_event_inbox i JOIN documents d ON d.id=i.aggregate_id
            WHERE i.processed_at IS NULL AND i.received_at<?
            """;
    private final OperationalFailureQueryExecutor queries;
    private final OperationalFailureProperties properties;
    private final Clock clock;
    public Set<Source> sources() { return Set.of(Source.DOCUMENT_INGESTION); }
    public Summary summary(Source source, Optional<UUID> tenantId) {
        return queries.summary(SQL, args(), source, tenantId);
    }
    public Page failures(Source source, Query query) { return queries.page(SQL, args(), source, query); }
    public List<Failure> recent(Source source, Optional<UUID> tenantId, int limit) {
        return queries.recent(SQL, args(), source, tenantId, limit);
    }
    private List<?> args() {
        return List.of(LocalDateTime.now(clock).minus(properties.failureStalledAfter()));
    }
}
