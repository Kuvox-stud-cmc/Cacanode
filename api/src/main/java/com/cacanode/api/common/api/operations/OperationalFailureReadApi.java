package com.cacanode.api.common.api.operations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OperationalFailureReadApi {
    Set<Source> sources();
    Summary summary(Source source, Optional<UUID> tenantId);
    Page failures(Source source, Query query);
    List<Failure> recent(Source source, Optional<UUID> tenantId, int limit);

    enum Source { MODULE_EVENTS, DOCUMENT_INGESTION, WEBHOOKS, BILLING, CV_ANALYSIS,
        INTERVIEW_TRANSPORT, CANDIDATE_EMAIL, RECORDING, PRIVACY_ERASURE }
    enum State { RETRYING, FAILED, DEAD, REVIEW, STALLED }
    enum Severity { WARNING, ERROR, CRITICAL }
    enum ResourceType { MODULE_EVENT, DOCUMENT, WEBHOOK_EVENT, PAYMENT_ORDER,
        BILLING_WEBHOOK, JOB }
    enum Code { MODULE_EVENT_RETRY, MODULE_EVENT_DEAD, DOCUMENT_PROCESSING_FAILED,
        DOCUMENT_PUBLICATION_RETRY, DOCUMENT_CHECKPOINT_STALLED, WEBHOOK_DELIVERY_RETRY,
        WEBHOOK_DELIVERY_FAILED, PAYMENT_FAILED, PAYMENT_REVIEW, BILLING_WEBHOOK_INVALID,
        BILLING_WEBHOOK_REVIEW, CV_ANALYSIS_FAILED, CV_ANALYSIS_PUBLICATION_FAILED,
        INTERVIEW_TRANSPORT_FAILED, INTERVIEW_TRANSPORT_STALLED, INTERVIEW_RESULT_FAILED,
        INTERVIEW_RESULT_STALLED, CANDIDATE_EMAIL_RETRY,
        CANDIDATE_EMAIL_EXHAUSTED, RECORDING_OPERATION_RETRY, RECORDING_OPERATION_DEAD,
        RECORDING_NOTIFICATION_RETRY, RECORDING_NOTIFICATION_DEAD, RECORDING_FAILED,
        PRIVACY_ERASURE_RETRY, PRIVACY_ERASURE_EXHAUSTED,
        UNKNOWN_MODULE_EVENT, UNKNOWN_DOCUMENT_FAILURE, UNKNOWN_WEBHOOK_FAILURE,
        UNKNOWN_BILLING_FAILURE, UNKNOWN_CV_ANALYSIS_FAILURE, UNKNOWN_INTERVIEW_TRANSPORT_FAILURE,
        UNKNOWN_CANDIDATE_EMAIL_FAILURE, UNKNOWN_RECORDING_FAILURE, UNKNOWN_PRIVACY_ERASURE_FAILURE }

    record Query(Optional<UUID> tenantId, State state, Severity severity, int page, int size,
                 String sort, String direction) {
        public Query { tenantId = tenantId == null ? Optional.empty() : tenantId; }
    }
    record Summary(long total, Map<State, Long> states, Map<Severity, Long> severities) {
        public Summary { states = Map.copyOf(states); severities = Map.copyOf(severities); }
    }
    record Page(List<Failure> items, long total) {
        public Page { items = List.copyOf(items); }
    }
    record Failure(Source source, UUID failureId, UUID tenantId, UUID resourceId, ResourceType resourceType,
                   State state, Severity severity, Code errorCode, int attempts,
                   LocalDateTime firstSeenAt, LocalDateTime lastSeenAt, LocalDateTime nextRetryAt) {}
}
