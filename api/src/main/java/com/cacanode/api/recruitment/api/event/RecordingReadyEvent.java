package com.cacanode.api.recruitment.api.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record RecordingReadyEvent(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("aggregate_id") UUID aggregateId,
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("call_attempt_id") UUID callAttemptId,
        @JsonProperty("storage_key") String storageKey,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("size_bytes") long sizeBytes,
        String sha256,
        @JsonProperty("retained_until") Instant retainedUntil) {
}
