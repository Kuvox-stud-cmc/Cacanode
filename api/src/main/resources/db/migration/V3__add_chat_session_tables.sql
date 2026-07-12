CREATE TABLE chat_sessions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chatbot_id         UUID NOT NULL REFERENCES chatbots(id) ON DELETE RESTRICT,
    knowledge_base_id  UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE RESTRICT,
    locale             VARCHAR(20) NOT NULL DEFAULT 'vi-VN',
    status             VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at          TIMESTAMP
);

CREATE INDEX idx_chat_session_tenant_id ON chat_sessions(tenant_id);
CREATE INDEX idx_chat_session_user_id ON chat_sessions(user_id);
CREATE INDEX idx_chat_session_chatbot_id ON chat_sessions(chatbot_id);
CREATE INDEX idx_chat_session_knowledge_base_id ON chat_sessions(knowledge_base_id);
CREATE INDEX idx_chat_session_tenant_status ON chat_sessions(tenant_id, status);

CREATE TABLE chat_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id          UUID REFERENCES users(id) ON DELETE SET NULL,
    role             VARCHAR(50) NOT NULL,
    content          TEXT NOT NULL,
    citations        JSONB NOT NULL DEFAULT '[]'::jsonb,
    sequence_number  INTEGER NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chat_message_session_sequence UNIQUE (session_id, sequence_number),
    CONSTRAINT chat_messages_role_check CHECK (role IN ('user', 'assistant', 'system'))
);

CREATE INDEX idx_chat_message_session_id ON chat_messages(session_id);
CREATE INDEX idx_chat_message_tenant_id ON chat_messages(tenant_id);
CREATE INDEX idx_chat_message_session_sequence ON chat_messages(session_id, sequence_number);
