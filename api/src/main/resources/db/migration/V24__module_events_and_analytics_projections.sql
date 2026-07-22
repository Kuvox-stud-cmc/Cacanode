ALTER TABLE chat_turns ADD COLUMN quota_consumption_id UUID NULL;

CREATE TABLE module_event_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT NULL,
    CONSTRAINT chk_module_event_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD'))
);

CREATE INDEX idx_module_event_outbox_due
    ON module_event_outbox (status, next_attempt_at, created_at);

CREATE TABLE module_event_inbox (
    consumer_name VARCHAR(160) NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE analytics_tenant_projection (
    tenant_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    plan VARCHAR(50) NOT NULL,
    max_storage_mb BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE analytics_user_projection (
    user_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_analytics_user_tenant_status_created
    ON analytics_user_projection (tenant_id, status, created_at);

CREATE TABLE analytics_invitation_projection (
    invitation_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_analytics_invitation_tenant_status
    ON analytics_invitation_projection (tenant_id, status, expires_at);

CREATE TABLE analytics_document_projection (
    document_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    visibility VARCHAR(50) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_analytics_document_tenant_created
    ON analytics_document_projection (tenant_id, created_at DESC);

CREATE TABLE analytics_conversation_projection (
    conversation_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_analytics_conversation_tenant_channel_created
    ON analytics_conversation_projection (tenant_id, channel, created_at);

CREATE TABLE analytics_message_projection (
    message_id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    question_text TEXT NULL,
    response_duration_ms BIGINT NULL,
    sequence_number INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_analytics_message_tenant_channel_created
    ON analytics_message_projection (tenant_id, channel, created_at);
CREATE INDEX idx_analytics_message_conversation_sequence
    ON analytics_message_projection (conversation_id, sequence_number);

CREATE TABLE analytics_ticket_projection (
    ticket_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_analytics_ticket_tenant_created_status
    ON analytics_ticket_projection (tenant_id, created_at, status);

INSERT INTO analytics_tenant_projection (
    tenant_id, name, status, plan, max_storage_mb, created_at, updated_at)
SELECT id, name, status, plan, COALESCE(max_storage_mb, 0), created_at, updated_at
FROM tenants;

INSERT INTO analytics_user_projection (
    user_id, tenant_id, status, role, created_at, updated_at)
SELECT id, tenant_id, status, role, created_at, updated_at
FROM users;

INSERT INTO analytics_invitation_projection (
    invitation_id, tenant_id, status, created_at, expires_at, updated_at)
SELECT id, tenant_id, status, created_at, expires_at, created_at
FROM invitations;

INSERT INTO analytics_document_projection (
    document_id, tenant_id, file_name, file_type, status, visibility,
    file_size_bytes, created_at, updated_at, deleted_at)
SELECT id, tenant_id, file_name, file_type, status, visibility,
       file_size_bytes, created_at, updated_at, NULL
FROM documents;

INSERT INTO analytics_conversation_projection (
    conversation_id, tenant_id, channel, status, created_at, closed_at, updated_at)
SELECT id, tenant_id, channel, status, created_at, closed_at, updated_at
FROM chat_sessions;

WITH ordered_messages AS (
    SELECT m.id,
           m.session_id,
           m.tenant_id,
           s.channel,
           m.role,
           m.content,
           m.sequence_number,
           m.created_at,
           LAG(m.role) OVER (PARTITION BY m.session_id ORDER BY m.sequence_number) AS previous_role,
           LAG(m.created_at) OVER (PARTITION BY m.session_id ORDER BY m.sequence_number) AS previous_created_at
    FROM chat_messages m
    JOIN chat_sessions s ON s.id = m.session_id
)
INSERT INTO analytics_message_projection (
    message_id, conversation_id, tenant_id, channel, role, question_text,
    response_duration_ms, sequence_number, created_at)
SELECT id,
       session_id,
       tenant_id,
       channel,
       role,
       CASE WHEN role = 'user' THEN content ELSE NULL END,
       CASE WHEN role = 'assistant' AND previous_role = 'user'
            THEN GREATEST(0, CAST(EXTRACT(EPOCH FROM (created_at - previous_created_at)) * 1000 AS BIGINT))
            ELSE NULL END,
       sequence_number,
       created_at
FROM ordered_messages;

INSERT INTO analytics_ticket_projection (
    ticket_id, tenant_id, status, priority, created_at, resolved_at, updated_at)
SELECT id, tenant_id, status, priority, created_at, resolved_at, updated_at
FROM tickets;
