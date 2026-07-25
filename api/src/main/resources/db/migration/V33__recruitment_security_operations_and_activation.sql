CREATE TABLE recruitment_tenant_activation (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    rollout_stage VARCHAR(20) NOT NULL DEFAULT 'OFF',
    master_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    automation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    cv_ai_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    calling_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    recording_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    public_discovery_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_recruitment_activation_stage
        CHECK (rollout_stage IN ('OFF','INTERNAL','PILOT','GA')),
    CONSTRAINT ck_recruitment_activation_master
        CHECK (master_enabled OR NOT (automation_enabled OR cv_ai_enabled OR calling_enabled
            OR recording_enabled OR public_discovery_enabled)),
    CONSTRAINT ck_recruitment_activation_dependencies
        CHECK ((NOT recording_enabled OR calling_enabled)
            AND (rollout_stage <> 'OFF' OR NOT master_enabled)
            AND (rollout_stage = 'GA' OR NOT public_discovery_enabled))
);

CREATE UNIQUE INDEX uq_recruitment_single_internal_tenant
    ON recruitment_tenant_activation (rollout_stage) WHERE rollout_stage='INTERNAL';
CREATE INDEX idx_recruitment_activation_stage_enabled
    ON recruitment_tenant_activation (rollout_stage,master_enabled);

INSERT INTO recruitment_tenant_activation(tenant_id)
SELECT id FROM tenants ON CONFLICT (tenant_id) DO NOTHING;

CREATE FUNCTION initialize_recruitment_tenant_activation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO recruitment_tenant_activation(tenant_id) VALUES (NEW.id)
    ON CONFLICT (tenant_id) DO NOTHING;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_initialize_recruitment_tenant_activation
AFTER INSERT ON tenants FOR EACH ROW EXECUTE FUNCTION initialize_recruitment_tenant_activation();

CREATE FUNCTION validate_recruitment_activation_plan() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE effective_plan VARCHAR(30);
BEGIN
    IF NEW.rollout_stage = 'PILOT' THEN
        SELECT COALESCE(s.plan_code,t.plan) INTO effective_plan
        FROM tenants t LEFT JOIN billing_subscriptions s ON s.tenant_id=t.id
        WHERE t.id=NEW.tenant_id;
        IF effective_plan NOT IN ('PRO','BUSINESS') THEN
            RAISE EXCEPTION 'PILOT recruitment activation requires Pro or Business plan'
                USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_validate_recruitment_activation_plan
BEFORE INSERT OR UPDATE ON recruitment_tenant_activation
FOR EACH ROW EXECUTE FUNCTION validate_recruitment_activation_plan();

CREATE TABLE recruitment_privacy_deletion_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    application_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    requester_kind VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    verification_reference VARCHAR(255),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    confirmed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    exhausted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_privacy_request_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT ck_recruitment_privacy_requester
        CHECK (requester_kind IN ('CANDIDATE','TENANT_ADMIN')),
    CONSTRAINT ck_recruitment_privacy_status
        CHECK (status IN ('PENDING_CONFIRMATION','PENDING','PROCESSING','RETRY','COMPLETED','EXHAUSTED','CANCELLED')),
    CONSTRAINT ck_recruitment_privacy_attempts CHECK (attempts BETWEEN 0 AND 10),
    CONSTRAINT ck_recruitment_privacy_verification CHECK (
        (requester_kind='CANDIDATE') OR
        (requester_kind='TENANT_ADMIN' AND verification_reference IS NOT NULL
            AND btrim(verification_reference) <> ''))
);

CREATE INDEX idx_recruitment_privacy_requests_tenant_status
    ON recruitment_privacy_deletion_requests(tenant_id,status,created_at DESC);
CREATE INDEX idx_recruitment_privacy_requests_due
    ON recruitment_privacy_deletion_requests(next_attempt_at,id)
    WHERE status IN ('PENDING','RETRY');

ALTER TABLE recruitment_public_jobs
    ADD COLUMN discoverable BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_recruitment_public_jobs_discoverable
    ON recruitment_public_jobs(published_at DESC,public_id DESC)
    WHERE discoverable;

ALTER TABLE recruitment_interview_call_attempts
    ADD COLUMN call_duration_seconds INTEGER,
    ADD CONSTRAINT ck_recruitment_call_duration
        CHECK (call_duration_seconds IS NULL OR call_duration_seconds BETWEEN 0 AND 14400);

ALTER TABLE recruitment_interview_recordings
    ADD COLUMN recording_duration_seconds INTEGER,
    ADD CONSTRAINT ck_recruitment_recording_duration
        CHECK (recording_duration_seconds IS NULL OR recording_duration_seconds BETWEEN 0 AND 14400);

ALTER TABLE recruitment_recording_operations
    DROP CONSTRAINT ck_recruitment_recording_operation_kind;
ALTER TABLE recruitment_recording_operations
    ADD CONSTRAINT ck_recruitment_recording_operation_kind
        CHECK (operation_kind IN ('START','STOP','COPY','DELETE_PROVIDER','DELETE_STORAGE','VERIFY_DELETION'));

ALTER TABLE recruitment_application_email_tokens
    DROP CONSTRAINT ck_recruitment_email_token_purpose,
    DROP CONSTRAINT ck_recruitment_email_token_expiry;
ALTER TABLE recruitment_application_email_tokens
    ADD CONSTRAINT ck_recruitment_email_token_purpose
        CHECK (purpose IN ('VERIFICATION','MANAGEMENT','DELETION_CONFIRMATION')),
    ADD CONSTRAINT ck_recruitment_email_token_expiry CHECK (
        expires_at > created_at AND
        ((purpose='VERIFICATION' AND expires_at <= created_at + INTERVAL '24 hours') OR
         (purpose='MANAGEMENT' AND expires_at <= created_at + INTERVAL '30 days') OR
         (purpose='DELETION_CONFIRMATION' AND expires_at <= created_at + INTERVAL '1 hour')));
