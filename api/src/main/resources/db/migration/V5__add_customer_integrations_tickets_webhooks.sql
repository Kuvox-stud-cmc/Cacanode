ALTER TABLE chat_sessions
    ALTER COLUMN user_id DROP NOT NULL,
    ADD COLUMN channel VARCHAR(50) NOT NULL DEFAULT 'EMPLOYEE_PLAYGROUND',
    ADD COLUMN external_user_id VARCHAR(255),
    ADD COLUMN customer_name VARCHAR(255),
    ADD COLUMN customer_email VARCHAR(320),
    ADD COLUMN customer_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN integration_token_id UUID,
    ADD COLUMN last_activity_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE chat_messages
    ADD COLUMN action JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX idx_chat_session_tenant_channel ON chat_sessions(tenant_id, channel);
CREATE INDEX idx_chat_session_external_user ON chat_sessions(tenant_id, external_user_id);
CREATE INDEX idx_chat_session_last_activity ON chat_sessions(status, channel, last_activity_at);

CREATE TABLE integration_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    chatbot_id      UUID NOT NULL REFERENCES chatbots(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    token_prefix    VARCHAR(24) NOT NULL,
    token_hash      VARCHAR(64) UNIQUE NOT NULL,
    scopes          JSONB NOT NULL DEFAULT '[]'::jsonb,
    expires_at      TIMESTAMP,
    last_used_at    TIMESTAMP,
    revoked_at      TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_integration_token_tenant ON integration_tokens(tenant_id, created_at DESC);
CREATE INDEX idx_integration_token_chatbot ON integration_tokens(chatbot_id);

ALTER TABLE chat_sessions
    ADD CONSTRAINT fk_chat_session_integration_token
    FOREIGN KEY (integration_token_id) REFERENCES integration_tokens(id) ON DELETE SET NULL;

CREATE TABLE tickets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    chatbot_id          UUID NOT NULL REFERENCES chatbots(id) ON DELETE RESTRICT,
    chat_session_id     UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE RESTRICT,
    integration_token_id UUID REFERENCES integration_tokens(id) ON DELETE SET NULL,
    assigned_to         UUID REFERENCES users(id) ON DELETE SET NULL,
    external_user_id    VARCHAR(255),
    customer_name       VARCHAR(255),
    customer_email      VARCHAR(320) NOT NULL,
    source              VARCHAR(50) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT NOT NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority            VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    idempotency_key     VARCHAR(255),
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ticket_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_ticket_tenant_created ON tickets(tenant_id, created_at DESC);
CREATE INDEX idx_ticket_tenant_status ON tickets(tenant_id, status);
CREATE INDEX idx_ticket_session ON tickets(chat_session_id);

CREATE TABLE ticket_notes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    author_id   UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_note_ticket_created ON ticket_notes(ticket_id, created_at);

CREATE TABLE webhook_endpoints (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                VARCHAR(120) NOT NULL,
    url                 TEXT NOT NULL,
    encrypted_secret    TEXT NOT NULL,
    events              JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    last_delivery_at    TIMESTAMP,
    last_delivery_status VARCHAR(50),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_endpoint_tenant ON webhook_endpoints(tenant_id, created_at DESC);

CREATE TABLE webhook_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_type      VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_outbox_due ON webhook_outbox(status, next_attempt_at);

CREATE TABLE webhook_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL REFERENCES webhook_outbox(id) ON DELETE CASCADE,
    endpoint_id     UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    attempt_number  INTEGER NOT NULL,
    response_status INTEGER,
    error_message   TEXT,
    delivered_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_webhook_delivery_attempt UNIQUE (event_id, endpoint_id, attempt_number)
);

CREATE INDEX idx_webhook_delivery_event ON webhook_deliveries(event_id, created_at);
