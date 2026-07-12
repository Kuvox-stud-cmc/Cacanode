CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ─── TENANTS ────────────────────────────────────────────────────────────────
CREATE TABLE tenants (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    slug              VARCHAR(100) UNIQUE NOT NULL,
    plan              VARCHAR(50) NOT NULL DEFAULT 'TRIAL',
    status            VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    trial_ends_at     TIMESTAMP,
    suspended_at      TIMESTAMP,
    suspended_reason  VARCHAR(255),
    max_documents     INTEGER NOT NULL DEFAULT 150,
    max_messages      INTEGER NOT NULL DEFAULT 5000,
    max_storage_mb    INTEGER NOT NULL DEFAULT 5120,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─── PLATFORM MODEL CONFIG VERSIONS ─────────────────────────────────────────
CREATE TABLE model_config_versions (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                       VARCHAR(100) UNIQUE NOT NULL,
    version_label              VARCHAR(100) NOT NULL,
    generation_model_id        VARCHAR(255) NOT NULL,
    generation_adapter_id      VARCHAR(255),
    generation_runtime         VARCHAR(100) NOT NULL DEFAULT 'vLLM',
    generation_endpoint        VARCHAR(255) NOT NULL DEFAULT 'internal://model-gateway/generation',
    text_embedding_model_id    VARCHAR(255) NOT NULL,
    text_embedding_dimension   INTEGER NOT NULL,
    text_embedding_runtime     VARCHAR(100) NOT NULL DEFAULT 'internal',
    image_embedding_model_id   VARCHAR(255),
    audio_embedding_model_id   VARCHAR(255),
    asr_model_id               VARCHAR(255),
    ocr_model_id               VARCHAR(255),
    status                     VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at                 TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_model_config_version_status ON model_config_versions(status);

-- ─── USERS ──────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255),
    role            VARCHAR(50) NOT NULL DEFAULT 'USER',
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    invited_by      UUID REFERENCES users(id),
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);

-- ─── REFRESH TOKENS ─────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) UNIQUE NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    persistent      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- ─── INVITATIONS ────────────────────────────────────────────────────────────
CREATE TABLE invitations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invited_by      UUID NOT NULL REFERENCES users(id),
    email           VARCHAR(255) NOT NULL,
    role            VARCHAR(50) NOT NULL DEFAULT 'USER',
    token           VARCHAR(255) UNIQUE NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invitations_tenant_id ON invitations(tenant_id);
CREATE INDEX idx_invitations_token ON invitations(token);

-- ─── KNOWLEDGE BASES ────────────────────────────────────────────────────────
CREATE TABLE knowledge_bases (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    description     TEXT,
    default_locale  VARCHAR(20) NOT NULL DEFAULT 'vi-VN',
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_knowledge_base_tenant_slug UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_knowledge_base_tenant_id ON knowledge_bases(tenant_id);
CREATE INDEX idx_knowledge_base_status ON knowledge_bases(status);

-- ─── CHATBOTS ───────────────────────────────────────────────────────────────
CREATE TABLE chatbots (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                  UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    knowledge_base_id          UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE RESTRICT,
    model_config_version_id    UUID NOT NULL REFERENCES model_config_versions(id) ON DELETE RESTRICT,
    display_name               VARCHAR(255) NOT NULL,
    default_locale             VARCHAR(20) NOT NULL DEFAULT 'vi-VN',
    welcome_message            TEXT NOT NULL,
    safe_instructions          TEXT NOT NULL,
    response_tone              VARCHAR(100) NOT NULL DEFAULT 'HELPFUL',
    citation_policy            VARCHAR(100) NOT NULL DEFAULT 'REQUIRED_FOR_KNOWLEDGE',
    general_knowledge_policy   VARCHAR(100) NOT NULL DEFAULT 'ALLOW_WITH_DISCLOSURE',
    retrieval_settings         JSONB NOT NULL DEFAULT '{}'::jsonb,
    allowed_origins            JSONB NOT NULL DEFAULT '[]'::jsonb,
    status                     VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at                 TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chatbot_tenant_id ON chatbots(tenant_id);
CREATE INDEX idx_chatbot_knowledge_base_id ON chatbots(knowledge_base_id);
CREATE INDEX idx_chatbot_model_config_version_id ON chatbots(model_config_version_id);
CREATE INDEX idx_chatbot_status ON chatbots(status);

-- ─── DOCUMENTS ──────────────────────────────────────────────────────────────
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    uploaded_by     UUID NOT NULL REFERENCES users(id),
    file_name       VARCHAR(500) NOT NULL,
    file_type       VARCHAR(50) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    storage_path    TEXT NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    job_id          VARCHAR(255),
    chunk_count     INTEGER,
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_tenant_id ON documents(tenant_id);
CREATE INDEX idx_document_status ON documents(status);
CREATE INDEX idx_document_job_id ON documents(job_id);

-- ─── USAGE METRICS ──────────────────────────────────────────────────────────
CREATE TABLE usage_metrics (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    period_year     INTEGER NOT NULL,
    period_month    INTEGER NOT NULL,
    message_count   INTEGER NOT NULL DEFAULT 0,
    document_count  INTEGER NOT NULL DEFAULT 0,
    storage_mb_used DECIMAL(10,2) NOT NULL DEFAULT 0,
    token_count     BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_usage_metric_tenant_period UNIQUE (tenant_id, period_year, period_month)
);

CREATE INDEX idx_usage_metric_tenant_id ON usage_metrics(tenant_id);

-- ─── NOTIFICATIONS ──────────────────────────────────────────────────────────
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id),
    type            VARCHAR(100) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    message         TEXT NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT notifications_type_check CHECK (type IN (
        'WELCOME_EMAIL',
        'LOGIN_2FA_EMAIL',
        'DOCUMENT_COMPLETED',
        'DOCUMENT_FAILED',
        'USER_INVITED',
        'QUOTA_WARNING',
        'QUOTA_EXCEEDED'
    ))
);

CREATE INDEX idx_notification_tenant_id ON notifications(tenant_id);

-- ─── AUDIT LOGS ─────────────────────────────────────────────────────────────
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100),
    resource_id     UUID,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    metadata        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_tenant_id ON audit_logs(tenant_id);
CREATE INDEX idx_audit_log_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_log_created_at ON audit_logs(created_at);

-- ─── WIDGET CONFIG ──────────────────────────────────────────────────────────
CREATE TABLE widget_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    chatbot_id      UUID UNIQUE NOT NULL REFERENCES chatbots(id) ON DELETE CASCADE,
    display_name    VARCHAR(255) NOT NULL DEFAULT 'Assistant',
    welcome_message TEXT NOT NULL DEFAULT 'Hi! How can I help you today?',
    primary_color   VARCHAR(7) NOT NULL DEFAULT '#4f46e5',
    position        VARCHAR(20) NOT NULL DEFAULT 'BOTTOM_RIGHT',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_widget_config_tenant_id ON widget_configs(tenant_id);
CREATE INDEX idx_widget_config_chatbot_id ON widget_configs(chatbot_id);

-- ─── LOGIN 2FA STATE ────────────────────────────────────────────────────────
CREATE TABLE login_2fa_state (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    token_hash      VARCHAR(255) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_2fa_user_id ON login_2fa_state(user_id);
CREATE INDEX idx_login_2fa_state_email ON login_2fa_state(email);
CREATE INDEX idx_login_2fa_token_hash ON login_2fa_state(token_hash);
CREATE INDEX idx_login_2fa_expires_at ON login_2fa_state(expires_at);

-- ─── USER SUSPENSION STATE ──────────────────────────────────────────────────
CREATE TABLE user_suspension_state (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    reason          VARCHAR(100) NOT NULL,
    suspended_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_suspension_user_id ON user_suspension_state(user_id);

-- ─── VERIFICATION RESEND STATE ──────────────────────────────────────────────
CREATE TABLE verification_resend_state (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    suspended_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_resend_user_id ON verification_resend_state(user_id);
