CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_documents_file_name_trgm
    ON documents USING gin (lower(file_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_documents_list_order
    ON documents (tenant_id, knowledge_base_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_content_trgm
    ON chat_messages USING gin (lower(content) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_role_sequence
    ON chat_messages (session_id, role, sequence_number);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_customer_name_trgm
    ON chat_sessions USING gin (lower(customer_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_customer_email_trgm
    ON chat_sessions USING gin (lower(customer_email) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_external_user_trgm
    ON chat_sessions USING gin (lower(external_user_id) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_playground_activity
    ON chat_sessions (tenant_id, user_id, channel, last_activity_at DESC, id DESC)
    WHERE hidden_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_sessions_conversation_activity
    ON chat_sessions (tenant_id, channel, last_activity_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_tickets_title_trgm
    ON tickets USING gin (lower(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_tickets_description_trgm
    ON tickets USING gin (lower(description) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_tickets_customer_name_trgm
    ON tickets USING gin (lower(customer_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_tickets_customer_email_trgm
    ON tickets USING gin (lower(customer_email) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_tickets_external_user_trgm
    ON tickets USING gin (lower(external_user_id) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_tickets_tenant_created
    ON tickets (tenant_id, created_at DESC, id DESC);
