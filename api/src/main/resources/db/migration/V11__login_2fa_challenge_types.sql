ALTER TABLE login_2fa_state
    ADD COLUMN challenge_type VARCHAR(16) NOT NULL DEFAULT 'LINK',
    ADD COLUMN verification_attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE login_2fa_state
    ADD CONSTRAINT chk_login_2fa_challenge_type
        CHECK (challenge_type IN ('LINK', 'CODE'));
