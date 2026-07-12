-- Align Flyway-managed schema with the current JPA mappings.
-- Hibernate is configured to validate only; future entity schema changes need new migrations.

-- TENANTS
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS suspended_reason VARCHAR(255);

UPDATE tenants
SET plan = UPPER(plan)
WHERE plan IS NOT NULL AND plan <> UPPER(plan);

UPDATE tenants
SET status = UPPER(status)
WHERE status IS NOT NULL AND status <> UPPER(status);

UPDATE tenants
SET
    plan = COALESCE(plan, 'TRIAL'),
    status = COALESCE(status, 'PENDING'),
    max_documents = COALESCE(max_documents, 150),
    max_messages = COALESCE(max_messages, 5000),
    max_storage_mb = COALESCE(max_storage_mb, 5120),
    llm_provider = COALESCE(llm_provider, 'groq'),
    llm_model = COALESCE(llm_model, 'llama-3.3-70b-versatile'),
    embed_provider = COALESCE(embed_provider, 'voyageai'),
    embed_model = COALESCE(embed_model, 'voyage-3');

ALTER TABLE tenants
    ALTER COLUMN plan SET DEFAULT 'TRIAL',
    ALTER COLUMN status SET DEFAULT 'PENDING',
    ALTER COLUMN max_documents SET DEFAULT 150,
    ALTER COLUMN max_messages SET DEFAULT 5000,
    ALTER COLUMN max_storage_mb SET DEFAULT 5120,
    ALTER COLUMN llm_provider SET DEFAULT 'groq',
    ALTER COLUMN llm_provider SET NOT NULL,
    ALTER COLUMN llm_model SET DEFAULT 'llama-3.3-70b-versatile',
    ALTER COLUMN llm_model SET NOT NULL,
    ALTER COLUMN embed_provider SET DEFAULT 'voyageai',
    ALTER COLUMN embed_provider SET NOT NULL,
    ALTER COLUMN embed_model SET DEFAULT 'voyage-3',
    ALTER COLUMN embed_model SET NOT NULL;

-- USERS
UPDATE users
SET role = UPPER(role)
WHERE role IS NOT NULL AND role <> UPPER(role);

UPDATE users
SET status = UPPER(status)
WHERE status IS NOT NULL AND status <> UPPER(status);

UPDATE users
SET
    role = COALESCE(role, 'USER'),
    status = COALESCE(status, 'ACTIVE');

ALTER TABLE users
    ALTER COLUMN role SET DEFAULT 'USER',
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

-- INVITATIONS
UPDATE invitations
SET role = UPPER(role)
WHERE role IS NOT NULL AND role <> UPPER(role);

UPDATE invitations
SET status = UPPER(status)
WHERE status IS NOT NULL AND status <> UPPER(status);

UPDATE invitations
SET
    role = COALESCE(role, 'USER'),
    status = COALESCE(status, 'PENDING');

ALTER TABLE invitations
    ALTER COLUMN role SET DEFAULT 'USER',
    ALTER COLUMN status SET DEFAULT 'PENDING';

-- DOCUMENTS
UPDATE documents
SET file_type = UPPER(file_type)
WHERE file_type IS NOT NULL AND file_type <> UPPER(file_type);

UPDATE documents
SET status = UPPER(status)
WHERE status IS NOT NULL AND status <> UPPER(status);

UPDATE documents
SET status = COALESCE(status, 'PENDING');

ALTER TABLE documents
    ALTER COLUMN status SET DEFAULT 'PENDING';

-- NOTIFICATIONS
UPDATE notifications
SET type = UPPER(type)
WHERE type IS NOT NULL AND type <> UPPER(type);

UPDATE notifications
SET status = UPPER(status)
WHERE status IS NOT NULL AND status <> UPPER(status);

UPDATE notifications
SET status = COALESCE(status, 'PENDING');

ALTER TABLE notifications
    ALTER COLUMN status SET DEFAULT 'PENDING';

-- WIDGET CONFIG
UPDATE widget_configs
SET position = REPLACE(UPPER(position), '-', '_')
WHERE position IS NOT NULL AND position <> REPLACE(UPPER(position), '-', '_');

UPDATE widget_configs
SET
    display_name = COALESCE(display_name, 'Assistant'),
    welcome_message = COALESCE(welcome_message, 'Hi! How can I help you today?'),
    primary_color = COALESCE(primary_color, '#4f46e5'),
    position = COALESCE(position, 'BOTTOM_RIGHT'),
    is_active = COALESCE(is_active, TRUE);

ALTER TABLE widget_configs
    ALTER COLUMN display_name SET DEFAULT 'Assistant',
    ALTER COLUMN welcome_message SET DEFAULT 'Hi! How can I help you today?',
    ALTER COLUMN primary_color SET DEFAULT '#4f46e5',
    ALTER COLUMN position SET DEFAULT 'BOTTOM_RIGHT',
    ALTER COLUMN is_active SET DEFAULT TRUE;

-- LOGIN 2FA STATE
UPDATE login_2fa_state
SET
    used = COALESCE(used, FALSE),
    attempt_count = COALESCE(attempt_count, 0);

ALTER TABLE login_2fa_state
    ALTER COLUMN used SET DEFAULT FALSE,
    ALTER COLUMN attempt_count SET DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_login_2fa_token_hash ON login_2fa_state(token_hash);
CREATE INDEX IF NOT EXISTS idx_login_2fa_expires_at ON login_2fa_state(expires_at);

-- USER SUSPENSION STATE
UPDATE user_suspension_state
SET
    reason = COALESCE(reason, 'unspecified'),
    suspended_at = COALESCE(suspended_at, NOW());

ALTER TABLE user_suspension_state
    ALTER COLUMN reason TYPE VARCHAR(100),
    ALTER COLUMN reason SET NOT NULL,
    ALTER COLUMN suspended_at SET DEFAULT NOW(),
    ALTER COLUMN suspended_at SET NOT NULL;

-- VERIFICATION RESEND STATE
UPDATE verification_resend_state
SET attempt_count = COALESCE(attempt_count, 0);

ALTER TABLE verification_resend_state
    ALTER COLUMN attempt_count SET DEFAULT 0;
