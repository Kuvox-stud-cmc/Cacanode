CREATE INDEX idx_document_tenant_created
    ON documents(tenant_id, created_at DESC);

CREATE INDEX idx_chat_session_tenant_channel_created
    ON chat_sessions(tenant_id, channel, created_at);

CREATE INDEX idx_chat_message_tenant_created
    ON chat_messages(tenant_id, created_at);
