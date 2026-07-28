ALTER TABLE recruitment_interviews
    ADD COLUMN active_call_attempt_id UUID,
    ADD CONSTRAINT uq_recruitment_interview_call_binding
        UNIQUE (tenant_id,id,application_id,job_id);

ALTER TABLE recruitment_interviews DROP CONSTRAINT fk_recruitment_interview_application;
ALTER TABLE recruitment_interviews ADD CONSTRAINT fk_recruitment_interview_application
    FOREIGN KEY (tenant_id,application_id,job_id,template_revision_id)
    REFERENCES recruitment_applications(tenant_id,id,job_id,template_revision_id) ON DELETE CASCADE;

CREATE TABLE recruitment_interview_call_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    interview_id UUID NOT NULL,
    application_id UUID NOT NULL,
    job_id UUID NOT NULL,
    session_id UUID NOT NULL,
    schedule_version BIGINT NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    template_revision_id UUID NOT NULL,
    prepared_session JSONB,
    prepared_session_sha256 VARCHAR(64),
    prepared_snapshot_version VARCHAR(40),
    cv_analysis_id UUID,
    cv_analysis_sha256 VARCHAR(64),
    destination_e164 VARCHAR(20),
    runtime_token_sha256 VARCHAR(64),
    runtime_token_expires_at TIMESTAMPTZ,
    twilio_call_sid VARCHAR(40),
    callback_sequence BIGINT NOT NULL DEFAULT -1,
    preparation_attempts INTEGER NOT NULL DEFAULT 0,
    cancellation_attempts INTEGER NOT NULL DEFAULT 0,
    termination_attempts INTEGER NOT NULL DEFAULT 0,
    create_outcome_uncertain BOOLEAN NOT NULL DEFAULT FALSE,
    create_uncertain_until TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,
    failure_code VARCHAR(100),
    answered_at TIMESTAMPTZ,
    consented_at TIMESTAMPTZ,
    terminal_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_call_attempt_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT uq_recruitment_call_attempt_interview_ref UNIQUE (tenant_id,id,interview_id),
    CONSTRAINT uq_recruitment_call_attempt_number UNIQUE (tenant_id,interview_id,attempt_number),
    CONSTRAINT uq_recruitment_call_attempt_session UNIQUE (session_id,attempt_number),
    CONSTRAINT uq_recruitment_call_attempt_twilio_sid UNIQUE (twilio_call_sid),
    CONSTRAINT fk_recruitment_call_attempt_interview FOREIGN KEY
        (tenant_id,interview_id,application_id,job_id)
        REFERENCES recruitment_interviews(tenant_id,id,application_id,job_id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_call_attempt_revision FOREIGN KEY (tenant_id,template_revision_id)
        REFERENCES recruitment_interview_template_revisions(tenant_id,id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_call_attempt_cv_analysis FOREIGN KEY
        (tenant_id,cv_analysis_id,application_id)
        REFERENCES recruitment_cv_analyses(tenant_id,id,application_id) ON DELETE SET NULL (cv_analysis_id),
    CONSTRAINT ck_recruitment_call_attempt_status CHECK (status IN (
        'PREPARING','READY','DIALING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS',
        'COMPLETED','NO_ANSWER','DECLINED','FAILED','CANCELLED','EXPIRED')),
    CONSTRAINT ck_recruitment_call_attempt_numbers CHECK (
        schedule_version >= 0 AND attempt_number > 0 AND callback_sequence >= -1 AND
        preparation_attempts BETWEEN 0 AND 3 AND cancellation_attempts BETWEEN 0 AND 3 AND
        termination_attempts BETWEEN 0 AND 3),
    CONSTRAINT ck_recruitment_call_attempt_destination CHECK
        (destination_e164 IS NULL OR destination_e164 ~ '^\+84[0-9]{9,10}$'),
    CONSTRAINT ck_recruitment_call_attempt_hashes CHECK (
        (prepared_session_sha256 IS NULL OR prepared_session_sha256 ~ '^[0-9a-f]{64}$') AND
        (cv_analysis_sha256 IS NULL OR cv_analysis_sha256 ~ '^[0-9a-f]{64}$') AND
        (runtime_token_sha256 IS NULL OR runtime_token_sha256 ~ '^[0-9a-f]{64}$')),
    CONSTRAINT ck_recruitment_call_attempt_json CHECK (
        prepared_session IS NULL OR (
            jsonb_typeof(prepared_session)='object' AND
            prepared_session ?& ARRAY['snapshotVersion','sessionId','callAttemptId','tenantId',
                'templateRevisionId','snapshotSha256','companyDisplayName','candidateDisplayName',
                'introductionText','disclosureText','closingText','durationLimitSeconds',
                'interactionLimits','recordingEnabled','cvPersonalizationEnabled','sections'] AND
            jsonb_typeof(prepared_session->'sections')='array' AND
            jsonb_array_length(prepared_session->'sections') > 0)),
    CONSTRAINT ck_recruitment_call_attempt_prepared CHECK (
        (prepared_session IS NULL AND prepared_session_sha256 IS NULL AND prepared_snapshot_version IS NULL) OR
        (prepared_session IS NOT NULL AND prepared_session_sha256 IS NOT NULL AND
            btrim(prepared_snapshot_version) <> '')),
    CONSTRAINT ck_recruitment_call_attempt_token CHECK (
        (runtime_token_sha256 IS NULL AND runtime_token_expires_at IS NULL) OR
        (runtime_token_sha256 IS NOT NULL AND runtime_token_expires_at IS NOT NULL)),
    CONSTRAINT ck_recruitment_call_attempt_uncertain CHECK (
        (NOT create_outcome_uncertain AND create_uncertain_until IS NULL) OR
        (create_outcome_uncertain AND create_uncertain_until IS NOT NULL)),
    CONSTRAINT ck_recruitment_call_attempt_terminal CHECK (
        (status IN ('PREPARING','READY','DIALING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS')
            AND terminal_at IS NULL) OR
        (status IN ('COMPLETED','NO_ANSWER','DECLINED','FAILED','CANCELLED','EXPIRED')
            AND terminal_at IS NOT NULL)),
    CONSTRAINT ck_recruitment_call_attempt_failure CHECK (
        failure_code IS NULL OR (btrim(failure_code) <> '' AND length(failure_code) <= 100)),
    CONSTRAINT ck_recruitment_call_attempt_twilio_sid CHECK (
        twilio_call_sid IS NULL OR twilio_call_sid ~ '^CA[0-9a-fA-F]{32}$')
);

CREATE UNIQUE INDEX uq_recruitment_call_attempt_active
    ON recruitment_interview_call_attempts(tenant_id,interview_id)
    WHERE status IN ('PREPARING','READY','DIALING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS');
CREATE INDEX idx_recruitment_call_attempt_due
    ON recruitment_interview_call_attempts(next_retry_at,id)
    WHERE status IN ('PREPARING','DIALING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS');
CREATE INDEX idx_recruitment_call_attempt_reconcile
    ON recruitment_interview_call_attempts(create_uncertain_until,id)
    WHERE create_outcome_uncertain;
CREATE INDEX idx_recruitment_call_attempt_tenant_active
    ON recruitment_interview_call_attempts(tenant_id,status,id)
    WHERE status IN ('DIALING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS');

ALTER TABLE recruitment_interviews
    ADD CONSTRAINT fk_recruitment_interview_active_call_attempt FOREIGN KEY
        (tenant_id,active_call_attempt_id,id)
        REFERENCES recruitment_interview_call_attempts(tenant_id,id,interview_id)
        ON DELETE SET NULL (active_call_attempt_id);

CREATE TABLE recruitment_twilio_callback_inbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    call_attempt_id UUID NOT NULL,
    twilio_call_sid VARCHAR(40),
    callback_kind VARCHAR(30) NOT NULL,
    sequence_number BIGINT,
    semantic_key VARCHAR(120) NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    processing_result VARCHAR(40) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_twilio_inbox_semantic UNIQUE
        (call_attempt_id,callback_kind,semantic_key),
    CONSTRAINT fk_recruitment_twilio_inbox_attempt FOREIGN KEY
        (tenant_id,call_attempt_id)
        REFERENCES recruitment_interview_call_attempts(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_twilio_inbox_kind CHECK
        (callback_kind IN ('VOICE','CONSENT','STATUS','STREAM_STATUS','FALLBACK')),
    CONSTRAINT ck_recruitment_twilio_inbox_hash CHECK
        (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_twilio_inbox_sequence CHECK
        (sequence_number IS NULL OR sequence_number >= 0),
    CONSTRAINT ck_recruitment_twilio_inbox_result CHECK (processing_result IN (
        'APPLIED','DUPLICATE','IGNORED_OLDER','IGNORED_TERMINAL','REJECTED_CONFLICT','REJECTED_BINDING')),
    CONSTRAINT ck_recruitment_twilio_inbox_sid CHECK
        (twilio_call_sid IS NULL OR twilio_call_sid ~ '^CA[0-9a-fA-F]{32}$'),
    CONSTRAINT ck_recruitment_twilio_inbox_privacy CHECK (btrim(semantic_key) <> '')
);
CREATE INDEX idx_recruitment_twilio_inbox_attempt
    ON recruitment_twilio_callback_inbox(tenant_id,call_attempt_id,processed_at DESC);
