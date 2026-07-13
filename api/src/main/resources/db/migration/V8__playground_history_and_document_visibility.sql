ALTER TABLE chat_sessions
    ADD COLUMN hidden_at TIMESTAMP;

CREATE INDEX idx_chat_session_playground_history
    ON chat_sessions (tenant_id, user_id, channel, hidden_at, last_activity_at DESC);

ALTER TABLE documents
    ADD COLUMN visibility VARCHAR(50);

UPDATE documents
SET visibility = 'CUSTOMER_AND_EMPLOYEE'
WHERE visibility IS NULL;

ALTER TABLE documents
    ALTER COLUMN visibility SET NOT NULL,
    ALTER COLUMN visibility SET DEFAULT 'CUSTOMER_AND_EMPLOYEE',
    ADD CONSTRAINT documents_visibility_check
        CHECK (visibility IN ('EMPLOYEE_ONLY', 'CUSTOMER_AND_EMPLOYEE'));

CREATE INDEX idx_document_customer_visibility
    ON documents (tenant_id, knowledge_base_id, status, visibility);
