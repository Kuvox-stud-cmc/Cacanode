# Recruitment operations runbook

Never copy tenant IDs, candidate data, phone numbers, tokens, provider SIDs, storage keys, transcripts, or scores into metric labels, tickets, or chat. Use request/event IDs and controlled error codes.

## Provider failures

Pause calling or recording at the tenant gate first, then the global flag if impact is broad. Check Twilio/Cartesia/model health and aggregate result codes. Do not retry calls manually until the fraud guard and quota reservation are understood.

## Queues and DLQs

Compare ready, unacknowledged, consumer, oldest-work, and DLQ metrics. Stop producers when lag continues to grow. Inspect a redacted payload schema, correct the consumer, replay with the original stable event ID, and verify deduplication.

## Fraud rejections

Confirm whether the spike is destination, tenant, cross-tenant, invalid-number, or Redis-unavailable. Redis failures intentionally stop new calls. Never print or search using raw phone numbers; use a separately generated support reference.

## Privacy erasure

For `EXHAUSTED` requests, inspect only the request ID and last error code. Verify provider recording deletion and object deletion before retrying. Never mark a request complete manually while an external object remains.

## Tenant isolation

Treat any non-zero tenant-integrity gauge as a security incident. Disable recruitment globally, preserve non-PII evidence, identify the violated composite binding, notify the security owner, and do not re-enable until the integrity query and affected data have been reviewed.

## Recovery

Use `deploy/restore.sh` with a unique `RESTORE_PROJECT`. Validate checksums, Flyway, Redis `appendonly=yes`, `appendfsync=everysec`, `maxmemory-policy=noeviction`, RabbitMQ durable queues/DLQs, Qdrant/Kuzu availability, SeaweedFS objects, tenant isolation, readiness, Prometheus, and public/authenticated smoke routes. Live overwrite requires `ALLOW_LIVE_VOLUME_OVERWRITE=I_UNDERSTAND_DATA_WILL_BE_REPLACED` and an approved incident ticket.
