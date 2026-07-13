ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS action JSONB NOT NULL DEFAULT '{}'::jsonb;
