package com.cacanode.api.billing.query;

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
public class BillingOperationalFailureReader implements OperationalFailureReadApi {
    private static final String SQL = """
            SELECT o.id failure_id,o.tenant_id,o.id resource_id,'PAYMENT_ORDER' resource_type,
              CASE WHEN o.status='REVIEW' THEN 'REVIEW' ELSE 'FAILED' END state,
              CASE WHEN o.status='REVIEW' THEN 'WARNING' ELSE 'ERROR' END severity,
              CASE WHEN o.status='REVIEW' THEN 'PAYMENT_REVIEW' ELSE 'PAYMENT_FAILED' END error_code,
              1 attempts,o.created_at first_seen_at,o.updated_at last_seen_at,NULL::timestamp next_retry_at
            FROM billing_payment_orders o WHERE o.status IN ('FAILED','REVIEW')
            UNION ALL
            SELECT w.id,o.tenant_id,w.id,'BILLING_WEBHOOK',
              CASE WHEN w.processing_result='REVIEW' THEN 'REVIEW' ELSE 'FAILED' END,
              CASE WHEN w.processing_result='REVIEW' THEN 'WARNING' ELSE 'ERROR' END,
              CASE WHEN w.processing_result='REVIEW' THEN 'BILLING_WEBHOOK_REVIEW' ELSE 'BILLING_WEBHOOK_INVALID' END,
              1,w.received_at,COALESCE(w.processed_at,w.received_at),NULL::timestamp
            FROM billing_webhook_events w LEFT JOIN billing_payment_orders o ON o.id=w.payment_order_id
            WHERE w.signature_valid=FALSE OR w.processing_result='REVIEW'
            """;
    private final OperationalFailureQueryExecutor queries;
    public Set<Source> sources() { return Set.of(Source.BILLING); }
    public Summary summary(Source source, Optional<UUID> tenantId) { return queries.summary(SQL, List.of(), source, tenantId); }
    public Page failures(Source source, Query query) { return queries.page(SQL, List.of(), source, query); }
    public List<Failure> recent(Source source, Optional<UUID> tenantId, int limit) {
        return queries.recent(SQL, List.of(), source, tenantId, limit);
    }
}
