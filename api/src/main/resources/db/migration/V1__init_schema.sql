-- ─── TENANTS ────────────────────────────────────────────────────────────────
CREATE TABLE tenants (
                         id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         name            VARCHAR(255) NOT NULL,
                         slug            VARCHAR(100) UNIQUE NOT NULL,  -- used in API paths
                         plan            VARCHAR(50) NOT NULL DEFAULT 'pro',
                         status          VARCHAR(50) NOT NULL DEFAULT 'active',
    -- Usage quotas
                         max_documents   INTEGER NOT NULL DEFAULT 150,
                         max_messages    INTEGER NOT NULL DEFAULT 5000,
                         max_storage_mb  INTEGER NOT NULL DEFAULT 5120,
    -- LLM config
                         llm_provider    VARCHAR(50) DEFAULT 'groq',
                         llm_model       VARCHAR(100) DEFAULT 'llama-3.3-70b-versatile',
                         embed_provider  VARCHAR(50) DEFAULT 'voyageai',
                         embed_model     VARCHAR(100) DEFAULT 'voyage-3',
                         api_key_enc     TEXT,                          -- encrypted BYOK key
    -- Timestamps
                         created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                         updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─── USERS ──────────────────────────────────────────────────────────────────
CREATE TABLE users (
                       id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                       email           VARCHAR(255) UNIQUE NOT NULL,
                       password_hash   VARCHAR(255) NOT NULL,
                       full_name       VARCHAR(255),
                       role            VARCHAR(50) NOT NULL DEFAULT 'user',
    -- role values: 'platform_admin' | 'tenant_admin' | 'user'
                       status          VARCHAR(50) NOT NULL DEFAULT 'active',
    -- status values: 'active' | 'invited' | 'suspended'
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
                             role            VARCHAR(50) NOT NULL DEFAULT 'user',
                             token           VARCHAR(255) UNIQUE NOT NULL,
                             status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    -- status values: 'pending' | 'accepted' | 'expired'
                             expires_at      TIMESTAMP NOT NULL,
                             created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invitations_tenant_id ON invitations(tenant_id);
CREATE INDEX idx_invitations_token ON invitations(token);

-- ─── DOCUMENTS ──────────────────────────────────────────────────────────────
CREATE TABLE documents (
                           id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                           uploaded_by     UUID NOT NULL REFERENCES users(id),
                           file_name       VARCHAR(500) NOT NULL,
                           file_type       VARCHAR(50) NOT NULL,
    -- file_type values: 'pdf' | 'docx' | 'txt' | 'html' | 'csv'
                           file_size_bytes BIGINT NOT NULL,
                           storage_path    TEXT NOT NULL,               -- SeaweedFS path
                           status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    -- status values: 'pending' | 'processing' | 'completed' | 'failed'
                           job_id          VARCHAR(255),                -- RabbitMQ job reference
                           chunk_count     INTEGER,                     -- populated after ingestion
                           error_message   TEXT,                        -- populated on failure
                           created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                           updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_tenant_id ON documents(tenant_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_job_id ON documents(job_id);

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
                               UNIQUE(tenant_id, period_year, period_month)
);

CREATE INDEX idx_usage_metrics_tenant_id ON usage_metrics(tenant_id);

-- ─── NOTIFICATIONS ──────────────────────────────────────────────────────────
CREATE TABLE notifications (
                               id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                               user_id         UUID REFERENCES users(id),
                               type            VARCHAR(100) NOT NULL,
    -- type values: 'document_completed' | 'document_failed'
    --              'user_invited' | 'quota_warning' | 'quota_exceeded'
                               title           VARCHAR(255) NOT NULL,
                               message         TEXT NOT NULL,
                               status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    -- status values: 'pending' | 'sent' | 'failed'
                               sent_at         TIMESTAMP,
                               created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_tenant_id ON notifications(tenant_id);

-- ─── AUDIT LOGS ─────────────────────────────────────────────────────────────
CREATE TABLE audit_logs (
                            id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                            user_id         UUID REFERENCES users(id),
                            action          VARCHAR(100) NOT NULL,
    -- action values: 'user.login' | 'user.logout' | 'document.upload'
    --                'document.delete' | 'user.invite' | 'user.remove'
                            resource_type   VARCHAR(100),
                            resource_id     UUID,
                            ip_address      VARCHAR(45),
                            user_agent      TEXT,
                            metadata        JSONB,
                            created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- ─── WIDGET CONFIG ───────────────────────────────────────────────────────────
CREATE TABLE widget_configs (
                                id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                tenant_id       UUID UNIQUE NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                                display_name    VARCHAR(255) NOT NULL DEFAULT 'Assistant',
                                welcome_message TEXT NOT NULL DEFAULT 'Hi! How can I help you today?',
                                primary_color   VARCHAR(7) NOT NULL DEFAULT '#4f46e5',
                                position        VARCHAR(20) NOT NULL DEFAULT 'bottom-right',
    -- position values: 'bottom-right' | 'bottom-left'
                                is_active       BOOLEAN NOT NULL DEFAULT TRUE,
                                created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                                updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);