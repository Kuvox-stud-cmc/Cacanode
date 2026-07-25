# AI interview Phase 1 contracts

Phase 1 adds dormant contract surfaces only. There are no recruitment tables,
controllers, provider SDKs, active consumers, CV analysis workflows, calls, or media
processing. Every recruitment and interview feature flag defaults to `false`.

## Ownership and boundaries

- Java `recruitment` owns jobs, applications, interview business state, Twilio
  recording callbacks, verified recording copies, and the local durable
  `recruitment.recording.ready` event.
- Python `interview` owns recoverable live runtime state and future CV/interview
  execution. It never connects to PostgreSQL.
- Cross-Java-module dependencies may use only the target module's `api..` package.
- Python `interview` may use `ingestion.api`, `model.api`, and `common`; framework,
  Redis, RabbitMQ, and generated protobuf imports remain internal or transport code.
- The stable media endpoint is
  `/ws/v1/interviews/twilio/media?token=...`. It is registered even while disabled,
  but is not exposed through the gateway in Phase 1.

## RabbitMQ topology

The durable topic exchange is `cacanode.interview.v1`; its dead-letter exchange is
`cacanode.interview.dlx.v1`.

| Owner | Queue | Dead-letter queue |
| --- | --- | --- |
| Python interview | `cacanode.interview.resume-analysis.v1` | `cacanode.interview.resume-analysis.dlq.v1` |
| Java recruitment | `cacanode.recruitment.interview-events.v1` | `cacanode.recruitment.interview-events.dlq.v1` |

Routing keys are `interview.resume-analysis.requested`,
`interview.resume-analysis.outcome`, `interview.turn.finalized`,
`interview.session.completed`, `interview.session.failed`, and
`interview.provider.usage`. Recording-ready is Java-local and is not routed through
the Python exchange.

Messages are persistent and mandatory. Publishers use confirms with a five-second
timeout. Future consumers may make at most three transient retries; malformed and
exhausted messages go directly to the owning DLQ. Phase 1 declares no consumers.

## Event identity

Event IDs are UUIDv5 values in namespace
`95f2198b-9bb1-5895-87ce-324f54c90d63` with the exact name:

`{event_type}|{aggregate_id}|{semantic_key}`

Schemas and cross-language fixtures live in `contracts/ai-interview/v1` and use
JSON Schema Draft 2020-12, schema version `1.0`, bounded fields, and
`additionalProperties: false`.

## Redis ownership

Interview keys derive from `CACHE_KEY_PREFIX` but do not depend on `CACHE_ENABLED`.
Identifiers are opaque; PII is prohibited in keys.

| Key suffix | Retention |
| --- | --- |
| `:interview:session:{sessionId}` | 7 days |
| `:interview:checkpoint:{sessionId}` | 7 days |
| `:interview:lease:{sessionId}` | 30 seconds; heartbeat every 10 seconds |
| `:interview:token:{sha256(token)}` | 900 seconds |
| `:interview:resume:{analysisId}` | 30 days |
| `:interview:event:{eventId}` | 30 days |

## Recovery ordering

For future runtime publication, persist the deterministic session/checkpoint, acquire
the session lease, publish the event with a broker confirm, write the confirmed-event
marker, then advance the checkpoint. On restart, reacquire the lease, compare the
prepared payload hash, skip confirmed event IDs, and resume from the last confirmed
checkpoint. Never advance a checkpoint before broker confirmation.

Operational DLQ recovery validates the payload against the versioned schema and its
UUIDv5 identity before republishing to the original routing key. Java remains the
business source of truth; Redis is recoverable runtime state and RabbitMQ is durable
transport.
