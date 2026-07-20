ALTER TABLE chat_sessions
    ADD COLUMN next_sequence_number INTEGER NOT NULL DEFAULT 1;

UPDATE chat_sessions session
SET next_sequence_number = COALESCE((
    SELECT MAX(message.sequence_number) + 1
    FROM chat_messages message
    WHERE message.session_id = session.id
), 1);

CREATE TABLE chat_turns (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id            UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    tenant_id             UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    generation_id         UUID NOT NULL UNIQUE,
    status                VARCHAR(30) NOT NULL,
    idempotency_key_hash  VARCHAR(64),
    request_fingerprint   VARCHAR(64) NOT NULL,
    user_message_id       UUID NOT NULL REFERENCES chat_messages(id) ON DELETE RESTRICT,
    assistant_message_id  UUID REFERENCES chat_messages(id) ON DELETE RESTRICT,
    knowledge_revision    BIGINT NOT NULL,
    generation_context    JSONB NOT NULL,
    attempt_count         INTEGER NOT NULL DEFAULT 0,
    failure_code          VARCHAR(100),
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_turn_status_check CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_chat_turn_session_idempotency
    ON chat_turns(session_id, idempotency_key_hash)
    WHERE idempotency_key_hash IS NOT NULL;
CREATE INDEX idx_chat_turn_tenant_created ON chat_turns(tenant_id, created_at DESC);
CREATE INDEX idx_chat_turn_session_status ON chat_turns(session_id, status);

CREATE TABLE internal_event_outbox (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id         UUID NOT NULL UNIQUE,
    aggregate_type   VARCHAR(100) NOT NULL,
    aggregate_id     UUID NOT NULL,
    event_type       VARCHAR(150) NOT NULL,
    payload          JSONB NOT NULL,
    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count    INTEGER NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_internal_outbox_due
    ON internal_event_outbox(status, next_attempt_at, created_at);

CREATE TABLE internal_event_inbox (
    event_id          UUID PRIMARY KEY,
    event_type        VARCHAR(150) NOT NULL,
    aggregate_id      UUID NOT NULL,
    payload           JSONB NOT NULL,
    received_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at      TIMESTAMP,
    processing_result VARCHAR(100)
);
CREATE INDEX idx_internal_inbox_unprocessed
    ON internal_event_inbox(processed_at, received_at);
