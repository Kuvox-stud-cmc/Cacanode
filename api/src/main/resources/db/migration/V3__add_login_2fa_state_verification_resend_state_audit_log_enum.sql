-- ─── DROP OLD notifications_type_check CONSTRAINT ───────────────────────────
-- The constraint was created without LOGIN_2FA_EMAIL; recreate it with all current values.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
        CHECK (type IN (
            'WELCOME_EMAIL',
            'LOGIN_2FA_EMAIL',
            'DOCUMENT_COMPLETED',
            'DOCUMENT_FAILED',
            'USER_INVITED',
            'QUOTA_WARNING',
            'QUOTA_EXCEEDED'
        ));

-- ─── CREATE login_2fa_state TABLE ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS login_2fa_state (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    token_hash      VARCHAR(255) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_login_2fa_state_user_id ON login_2fa_state(user_id);
CREATE INDEX IF NOT EXISTS idx_login_2fa_state_email ON login_2fa_state(email);

-- ─── CREATE user_suspension_state TABLE ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_suspension_state (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    reason          VARCHAR(255),
    suspended_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_suspension_state_user_id ON user_suspension_state(user_id);

-- ─── CREATE verification_resend_state TABLE ───────────────────────────────────
CREATE TABLE IF NOT EXISTS verification_resend_state (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    attempt_count   INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    suspended_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_verification_resend_state_user_id ON verification_resend_state(user_id);
