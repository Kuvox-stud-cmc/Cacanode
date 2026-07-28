CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE recruitment_tenant_settings DROP CONSTRAINT ck_recruitment_settings_automation;
UPDATE recruitment_tenant_settings
SET default_automation_mode = 'AUTO_INVITE_ALL'
WHERE default_automation_mode = 'AUTOMATIC';
ALTER TABLE recruitment_tenant_settings
    ADD CONSTRAINT ck_recruitment_settings_automation
        CHECK (default_automation_mode IN ('MANUAL','AUTO_INVITE_ALL','AUTO_INVITE_MATCHING')),
    ADD COLUMN scheduling_timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    ADD COLUMN slot_grid_minutes INTEGER NOT NULL DEFAULT 15,
    ADD COLUMN minimum_notice_minutes INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN booking_horizon_days INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN invitation_lifetime_days INTEGER NOT NULL DEFAULT 7,
    ADD COLUMN reschedule_cutoff_minutes INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN reminder_offsets_minutes INTEGER[] NOT NULL DEFAULT ARRAY[1440,60],
    ADD CONSTRAINT ck_recruitment_settings_scheduling CHECK (
        btrim(scheduling_timezone) <> '' AND slot_grid_minutes = 15 AND
        minimum_notice_minutes >= 0 AND booking_horizon_days BETWEEN 1 AND 365 AND
        invitation_lifetime_days BETWEEN 1 AND 30 AND reschedule_cutoff_minutes >= 0 AND
        cardinality(reminder_offsets_minutes) BETWEEN 0 AND 10 AND
        0 < ALL(reminder_offsets_minutes)
    );

ALTER TABLE recruitment_jobs DROP CONSTRAINT ck_recruitment_job_automation;
ALTER TABLE recruitment_jobs DROP CONSTRAINT ck_recruitment_job_effective_modes;
UPDATE recruitment_jobs SET automation_mode_override = 'AUTO_INVITE_ALL'
WHERE automation_mode_override = 'AUTOMATIC';
UPDATE recruitment_jobs SET effective_automation_mode = 'AUTO_INVITE_ALL'
WHERE effective_automation_mode = 'AUTOMATIC';
ALTER TABLE recruitment_jobs
    ADD COLUMN screening_config JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_recruitment_job_automation CHECK (
        automation_mode_override IS NULL OR automation_mode_override IN ('MANUAL','AUTO_INVITE_ALL','AUTO_INVITE_MATCHING')),
    ADD CONSTRAINT ck_recruitment_job_effective_modes CHECK (
        (effective_automation_mode IS NULL OR effective_automation_mode IN ('MANUAL','AUTO_INVITE_ALL','AUTO_INVITE_MATCHING')) AND
        (effective_cv_ai_mode IS NULL OR effective_cv_ai_mode IN ('OFF','OPTIONAL','REQUIRED'))
    ),
    ADD CONSTRAINT ck_recruitment_job_screening_json CHECK (
        jsonb_typeof(screening_config) = 'array' AND jsonb_array_length(screening_config) <= 10
    );

ALTER TABLE recruitment_public_jobs
    ADD COLUMN screening_questions JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_recruitment_public_job_screening CHECK (jsonb_typeof(screening_questions) = 'array');

ALTER TABLE recruitment_applications
    ADD COLUMN screening_config_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN screening_answers JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN automation_mode_snapshot VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN automation_outcome VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN automation_evaluated_at TIMESTAMP,
    ADD CONSTRAINT ck_recruitment_application_screening CHECK (
        jsonb_typeof(screening_config_snapshot) = 'array' AND
        jsonb_typeof(screening_answers) = 'array'
    ),
    ADD CONSTRAINT ck_recruitment_application_automation_mode CHECK (
        automation_mode_snapshot IN ('MANUAL','AUTO_INVITE_ALL','AUTO_INVITE_MATCHING')
    ),
    ADD CONSTRAINT ck_recruitment_application_automation_outcome CHECK (
        automation_outcome IN ('PENDING','MANUAL','INVITED','NOT_MATCHED','INELIGIBLE')
    );
UPDATE recruitment_applications a
SET screening_config_snapshot = j.screening_config,
    automation_mode_snapshot = COALESCE(j.effective_automation_mode, 'MANUAL'),
    automation_outcome = CASE
        WHEN a.status IN ('SUBMITTED_UNVERIFIED','SUBMITTED') THEN 'PENDING'
        WHEN a.status IN ('INTERVIEW_INVITED','INTERVIEW_SCHEDULED','INTERVIEW_COMPLETED') THEN 'INVITED'
        ELSE 'INELIGIBLE'
    END,
    automation_evaluated_at = CASE WHEN a.status IN ('SUBMITTED_UNVERIFIED','SUBMITTED') THEN NULL ELSE a.updated_at END
FROM recruitment_jobs j
WHERE j.tenant_id = a.tenant_id AND j.id = a.job_id;

CREATE TABLE recruitment_availability_windows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL,
    start_local TIME NOT NULL,
    end_local TIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_availability_window_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT ck_recruitment_availability_window_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_recruitment_availability_window_range CHECK (end_local > start_local),
    CONSTRAINT ex_recruitment_availability_window_overlap EXCLUDE USING gist (
        tenant_id WITH =, day_of_week WITH =,
        int8range(extract(epoch FROM start_local)::bigint,extract(epoch FROM end_local)::bigint,'[)') WITH &&
    )
);

CREATE TABLE recruitment_availability_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    exception_date DATE NOT NULL,
    kind VARCHAR(10) NOT NULL,
    start_local TIME NOT NULL,
    end_local TIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_availability_exception_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT ck_recruitment_availability_exception_kind CHECK (kind IN ('BLACKOUT','EXTRA')),
    CONSTRAINT ck_recruitment_availability_exception_range CHECK (end_local > start_local),
    CONSTRAINT ex_recruitment_availability_exception_overlap EXCLUDE USING gist (
        tenant_id WITH =, exception_date WITH =, kind WITH =,
        int8range(extract(epoch FROM start_local)::bigint,extract(epoch FROM end_local)::bigint,'[)') WITH &&
    )
);
CREATE INDEX idx_recruitment_availability_exception_date
    ON recruitment_availability_exceptions(tenant_id,exception_date);

ALTER TABLE recruitment_interviews
    ADD COLUMN invited_at TIMESTAMP,
    ADD COLUMN invitation_expires_at TIMESTAMP,
    ADD COLUMN scheduled_start_at TIMESTAMPTZ,
    ADD COLUMN scheduled_end_at TIMESTAMPTZ,
    ADD COLUMN scheduling_timezone VARCHAR(80),
    ADD COLUMN schedule_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN cancelled_at TIMESTAMP,
    ADD COLUMN expired_at TIMESTAMP,
    ADD COLUMN quota_reservation_id UUID,
    ADD COLUMN quota_reserved_seconds BIGINT,
    ADD COLUMN quota_reservation_expires_at TIMESTAMP,
    ADD COLUMN reschedule_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_recruitment_interview_invitation_dates CHECK (
        invitation_expires_at IS NULL OR (invited_at IS NOT NULL AND invitation_expires_at > invited_at)
    ),
    ADD CONSTRAINT ck_recruitment_interview_schedule_range CHECK (
        (scheduled_start_at IS NULL AND scheduled_end_at IS NULL AND scheduling_timezone IS NULL) OR
        (scheduled_start_at IS NOT NULL AND scheduled_end_at > scheduled_start_at AND btrim(scheduling_timezone) <> '')
    ),
    ADD CONSTRAINT ck_recruitment_interview_quota CHECK (
        (quota_reservation_id IS NULL AND quota_reserved_seconds IS NULL AND quota_reservation_expires_at IS NULL) OR
        (quota_reservation_id IS NOT NULL AND quota_reserved_seconds > 0 AND quota_reservation_expires_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_recruitment_interview_reschedules CHECK (reschedule_count >= 0);

UPDATE recruitment_interviews
SET invited_at = created_at,
    invitation_expires_at = created_at + INTERVAL '7 days'
WHERE status = 'INVITED';
UPDATE recruitment_interviews
SET scheduled_start_at = scheduled_at AT TIME ZONE 'UTC',
    scheduled_end_at = (scheduled_at + make_interval(secs =>
        CEIL(((template_snapshot->>'durationLimitSeconds')::numeric) / 900.0)::integer * 900)) AT TIME ZONE 'UTC',
    scheduling_timezone = 'UTC'
WHERE scheduled_at IS NOT NULL;

INSERT INTO hiring_quota_reservations(
    id,tenant_id,quota_kind,aggregate_id,state,reserved_amount,expires_at,created_at,updated_at)
SELECT gen_random_uuid(),i.tenant_id,'INTERVIEW_SECONDS',i.id,'RESERVED',
       (i.template_snapshot->>'durationLimitSeconds')::bigint,
       (i.scheduled_end_at AT TIME ZONE 'UTC') + INTERVAL '24 hours',i.created_at,NOW()
FROM recruitment_interviews i
WHERE i.scheduled_start_at IS NOT NULL
ON CONFLICT (tenant_id,quota_kind,aggregate_id) DO NOTHING;
UPDATE recruitment_interviews i
SET quota_reservation_id = r.id,
    quota_reserved_seconds = r.reserved_amount,
    quota_reservation_expires_at = r.expires_at
FROM hiring_quota_reservations r
WHERE r.tenant_id=i.tenant_id AND r.quota_kind='INTERVIEW_SECONDS' AND r.aggregate_id=i.id
  AND i.scheduled_start_at IS NOT NULL;

ALTER TABLE recruitment_interviews
    ADD CONSTRAINT fk_recruitment_interview_quota_reservation FOREIGN KEY (quota_reservation_id)
        REFERENCES hiring_quota_reservations(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ex_recruitment_interview_active_schedule EXCLUDE USING gist (
        tenant_id WITH =, tstzrange(scheduled_start_at,scheduled_end_at,'[)') WITH &&
    ) WHERE (status IN ('SCHEDULED','PREPARING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS'));
CREATE INDEX idx_recruitment_interviews_invitation_expiry
    ON recruitment_interviews(invitation_expires_at,id) WHERE status='INVITED';
CREATE INDEX idx_recruitment_interviews_schedule_start
    ON recruitment_interviews(tenant_id,scheduled_start_at,id) WHERE scheduled_start_at IS NOT NULL;

CREATE TABLE recruitment_interview_invitation_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    interview_id UUID NOT NULL,
    application_id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    last_used_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_recruitment_invitation_token_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT fk_recruitment_invitation_token_interview FOREIGN KEY (tenant_id,interview_id)
        REFERENCES recruitment_interviews(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_invitation_token_application FOREIGN KEY (tenant_id,application_id)
        REFERENCES recruitment_applications(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_invitation_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_invitation_token_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_recruitment_invitation_tokens_cleanup
    ON recruitment_interview_invitation_tokens(expires_at,id) WHERE revoked_at IS NULL;
CREATE INDEX idx_recruitment_invitation_tokens_interview
    ON recruitment_interview_invitation_tokens(tenant_id,interview_id) WHERE revoked_at IS NULL;

CREATE TABLE recruitment_candidate_email_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    interview_id UUID NOT NULL,
    application_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL,
    dedupe_key VARCHAR(180) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    due_at TIMESTAMP NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_email_delivery_dedupe UNIQUE (tenant_id,dedupe_key),
    CONSTRAINT uq_recruitment_email_delivery_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT fk_recruitment_email_delivery_interview FOREIGN KEY (tenant_id,interview_id)
        REFERENCES recruitment_interviews(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_email_delivery_application FOREIGN KEY (tenant_id,application_id)
        REFERENCES recruitment_applications(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_email_delivery_kind CHECK (kind IN ('INVITATION','CONFIRMATION','RESCHEDULE_CONFIRMATION','REMINDER')),
    CONSTRAINT ck_recruitment_email_delivery_state CHECK (state IN ('PENDING','DISPATCHING','SENT','FAILED','CANCELLED')),
    CONSTRAINT ck_recruitment_email_delivery_attempts CHECK (attempts BETWEEN 0 AND 10),
    CONSTRAINT ck_recruitment_email_delivery_terminal CHECK (
        (state='SENT' AND sent_at IS NOT NULL AND cancelled_at IS NULL) OR
        (state='CANCELLED' AND cancelled_at IS NOT NULL AND sent_at IS NULL) OR
        (state IN ('PENDING','DISPATCHING','FAILED') AND sent_at IS NULL)
    )
);
CREATE INDEX idx_recruitment_email_delivery_retry
    ON recruitment_candidate_email_deliveries(next_attempt_at,id)
    WHERE state IN ('PENDING','FAILED') AND attempts < 10 AND cancelled_at IS NULL;
CREATE INDEX idx_recruitment_email_delivery_cleanup
    ON recruitment_candidate_email_deliveries(sent_at,cancelled_at,id)
    WHERE state IN ('SENT','CANCELLED');
ALTER TABLE recruitment_interview_invitation_tokens
    ADD CONSTRAINT fk_recruitment_invitation_token_delivery FOREIGN KEY (tenant_id,delivery_id)
        REFERENCES recruitment_candidate_email_deliveries(tenant_id,id) ON DELETE CASCADE;
