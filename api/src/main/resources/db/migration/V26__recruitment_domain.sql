CREATE TABLE recruitment_interview_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    locale VARCHAR(5) NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_template_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_recruitment_template_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_recruitment_template_locale CHECK (locale IN ('vi-VN', 'en-US')),
    CONSTRAINT ck_recruitment_template_archive CHECK (archived = (archived_at IS NOT NULL))
);

CREATE TABLE recruitment_interview_template_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    template_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    content JSONB NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_revision_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_recruitment_revision_number UNIQUE (tenant_id, template_id, revision_number),
    CONSTRAINT fk_recruitment_revision_template FOREIGN KEY (tenant_id, template_id)
        REFERENCES recruitment_interview_templates(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_revision_number CHECK (revision_number > 0),
    CONSTRAINT ck_recruitment_revision_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_revision_json CHECK (
        jsonb_typeof(content) = 'object' AND
        content ?& ARRAY['introductionText','disclosureText','closingText','durationLimitSeconds','interactionLimits','sections'] AND
        jsonb_typeof(content->'interactionLimits') = 'object' AND
        (content->'interactionLimits') ?& ARRAY['repetitionLimit','clarificationLimit','silenceTimeoutSeconds','silencePromptLimit'] AND
        CASE WHEN jsonb_typeof(content->'interactionLimits'->'repetitionLimit') = 'number'
            THEN (content->'interactionLimits'->>'repetitionLimit')::numeric >= 0 ELSE FALSE END AND
        CASE WHEN jsonb_typeof(content->'interactionLimits'->'clarificationLimit') = 'number'
            THEN (content->'interactionLimits'->>'clarificationLimit')::numeric >= 0 ELSE FALSE END AND
        CASE WHEN jsonb_typeof(content->'interactionLimits'->'silenceTimeoutSeconds') = 'number'
            THEN (content->'interactionLimits'->>'silenceTimeoutSeconds')::numeric > 0 ELSE FALSE END AND
        CASE WHEN jsonb_typeof(content->'interactionLimits'->'silencePromptLimit') = 'number'
            THEN (content->'interactionLimits'->>'silencePromptLimit')::numeric >= 0 ELSE FALSE END AND
        jsonb_typeof(content->'sections') = 'array' AND
        jsonb_array_length(content->'sections') > 0 AND
        (content->>'durationLimitSeconds') ~ '^[1-9][0-9]*$'
    )
);

CREATE TABLE recruitment_tenant_settings (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    default_automation_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    cv_ai_mode VARCHAR(20) NOT NULL DEFAULT 'OFF',
    default_template_revision_id UUID,
    recording_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    recording_retention_days INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_recruitment_settings_revision FOREIGN KEY (tenant_id, default_template_revision_id)
        REFERENCES recruitment_interview_template_revisions(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_settings_automation CHECK (default_automation_mode IN ('MANUAL','AUTOMATIC')),
    CONSTRAINT ck_recruitment_settings_cv_ai CHECK (cv_ai_mode IN ('OFF','OPTIONAL','REQUIRED')),
    CONSTRAINT ck_recruitment_settings_recording CHECK (
        (recording_enabled AND recording_retention_days > 0) OR
        (NOT recording_enabled AND recording_retention_days = 0)
    )
);

CREATE TABLE recruitment_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    department VARCHAR(120),
    location VARCHAR(160),
    employment_type VARCHAR(30),
    work_mode VARCHAR(20),
    language VARCHAR(5) NOT NULL DEFAULT 'vi-VN',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    cv_policy VARCHAR(20) NOT NULL DEFAULT 'OPTIONAL',
    automation_mode_override VARCHAR(20),
    cv_ai_mode_override VARCHAR(20),
    effective_automation_mode VARCHAR(20),
    effective_cv_ai_mode VARCHAR(20),
    recording_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    recording_retention_days INTEGER NOT NULL DEFAULT 0,
    template_revision_id UUID,
    closing_at TIMESTAMP,
    published_at TIMESTAMP,
    paused_at TIMESTAMP,
    closed_at TIMESTAMP,
    archived_at TIMESTAMP,
    active_job_reservation_id UUID,
    frozen_company_name VARCHAR(200),
    frozen_company_slug VARCHAR(160),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_job_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_recruitment_job_public_id UNIQUE (public_id),
    CONSTRAINT fk_recruitment_job_revision FOREIGN KEY (tenant_id, template_revision_id)
        REFERENCES recruitment_interview_template_revisions(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_job_text CHECK (btrim(title) <> '' AND btrim(description) <> ''),
    CONSTRAINT ck_recruitment_job_locale CHECK (language IN ('vi-VN','en-US')),
    CONSTRAINT ck_recruitment_job_status CHECK (status IN ('DRAFT','PUBLISHED','PAUSED','CLOSED','ARCHIVED')),
    CONSTRAINT ck_recruitment_job_cv_policy CHECK (cv_policy IN ('NOT_ACCEPTED','OPTIONAL','REQUIRED')),
    CONSTRAINT ck_recruitment_job_automation CHECK (automation_mode_override IS NULL OR automation_mode_override IN ('MANUAL','AUTOMATIC')),
    CONSTRAINT ck_recruitment_job_cv_ai CHECK (cv_ai_mode_override IS NULL OR cv_ai_mode_override IN ('OFF','OPTIONAL','REQUIRED')),
    CONSTRAINT ck_recruitment_job_effective_modes CHECK (
        (effective_automation_mode IS NULL OR effective_automation_mode IN ('MANUAL','AUTOMATIC')) AND
        (effective_cv_ai_mode IS NULL OR effective_cv_ai_mode IN ('OFF','OPTIONAL','REQUIRED'))
    ),
    CONSTRAINT ck_recruitment_job_recording CHECK (
        (recording_enabled AND recording_retention_days > 0) OR
        (NOT recording_enabled AND recording_retention_days = 0)
    ),
    CONSTRAINT ck_recruitment_job_publication_snapshot CHECK (
        (published_at IS NULL AND active_job_reservation_id IS NULL AND frozen_company_name IS NULL AND frozen_company_slug IS NULL)
        OR
        (published_at IS NOT NULL AND active_job_reservation_id IS NOT NULL AND
         btrim(frozen_company_name) <> '' AND btrim(frozen_company_slug) <> '' AND template_revision_id IS NOT NULL AND
         effective_automation_mode IS NOT NULL AND effective_cv_ai_mode IS NOT NULL)
    ),
    CONSTRAINT ck_recruitment_job_lifecycle CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND paused_at IS NULL AND closed_at IS NULL AND archived_at IS NULL) OR
        (status = 'PUBLISHED' AND published_at IS NOT NULL AND closed_at IS NULL AND archived_at IS NULL) OR
        (status = 'PAUSED' AND published_at IS NOT NULL AND paused_at IS NOT NULL AND closed_at IS NULL AND archived_at IS NULL) OR
        (status = 'CLOSED' AND published_at IS NOT NULL AND closed_at IS NOT NULL AND archived_at IS NULL) OR
        (status = 'ARCHIVED' AND published_at IS NOT NULL AND closed_at IS NOT NULL AND archived_at IS NOT NULL)
    )
);

CREATE TABLE recruitment_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    full_name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    phone VARCHAR(20),
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_candidate_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_recruitment_candidate_email UNIQUE (tenant_id, normalized_email),
    CONSTRAINT ck_recruitment_candidate_text CHECK (btrim(full_name) <> '' AND btrim(normalized_name) <> '' AND btrim(email) <> '' AND normalized_email = lower(btrim(email))),
    CONSTRAINT ck_recruitment_candidate_phone CHECK (phone IS NULL OR phone ~ '^\\+84[0-9]{9,10}$')
);

CREATE TABLE recruitment_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    job_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED_UNVERIFIED',
    submitted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    cv_present BOOLEAN NOT NULL DEFAULT FALSE,
    cv_analysis_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    template_revision_id UUID NOT NULL,
    template_snapshot JSONB NOT NULL,
    template_snapshot_sha256 VARCHAR(64) NOT NULL,
    template_snapshot_version VARCHAR(40) NOT NULL,
    overall_score NUMERIC(5,2),
    english_band VARCHAR(10),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_application_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_recruitment_application_interview_ref UNIQUE (tenant_id, id, job_id, template_revision_id),
    CONSTRAINT uq_recruitment_application_job_candidate UNIQUE (tenant_id, job_id, candidate_id),
    CONSTRAINT fk_recruitment_application_job FOREIGN KEY (tenant_id, job_id)
        REFERENCES recruitment_jobs(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_application_candidate FOREIGN KEY (tenant_id, candidate_id)
        REFERENCES recruitment_candidates(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_application_revision FOREIGN KEY (tenant_id, template_revision_id)
        REFERENCES recruitment_interview_template_revisions(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_application_status CHECK (status IN (
        'SUBMITTED_UNVERIFIED','SUBMITTED','INTERVIEW_INVITED','INTERVIEW_SCHEDULED','INTERVIEW_COMPLETED',
        'UNDER_REVIEW','SHORTLISTED','REJECTED','WITHDRAWN')),
    CONSTRAINT ck_recruitment_application_cv_analysis CHECK (cv_analysis_status IN ('NOT_REQUESTED','PENDING','COMPLETED','FAILED')),
    CONSTRAINT ck_recruitment_application_snapshot CHECK (
        jsonb_typeof(template_snapshot) = 'object' AND
        template_snapshot ?& ARRAY['introductionText','disclosureText','closingText','durationLimitSeconds','interactionLimits','sections'] AND
        jsonb_typeof(template_snapshot->'interactionLimits') = 'object' AND
        (template_snapshot->'interactionLimits') ?& ARRAY['repetitionLimit','clarificationLimit','silenceTimeoutSeconds','silencePromptLimit'] AND
        jsonb_typeof(template_snapshot->'sections') = 'array' AND jsonb_array_length(template_snapshot->'sections') > 0 AND
        (template_snapshot->>'durationLimitSeconds') ~ '^[1-9][0-9]*$' AND
        template_snapshot_sha256 ~ '^[0-9a-f]{64}$' AND btrim(template_snapshot_version) <> ''
    ),
    CONSTRAINT ck_recruitment_application_score CHECK (overall_score IS NULL OR (overall_score >= 0 AND overall_score <= 100))
);

CREATE TABLE recruitment_interviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    application_id UUID NOT NULL,
    job_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'INVITED',
    template_revision_id UUID NOT NULL,
    template_snapshot JSONB NOT NULL,
    template_snapshot_sha256 VARCHAR(64) NOT NULL,
    template_snapshot_version VARCHAR(40) NOT NULL,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    overall_score NUMERIC(5,2),
    english_band VARCHAR(10),
    recording_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    recording_retention_days INTEGER NOT NULL DEFAULT 0,
    recording_expires_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_interview_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_recruitment_interview_application UNIQUE (tenant_id, application_id),
    CONSTRAINT fk_recruitment_interview_application FOREIGN KEY (tenant_id, application_id, job_id, template_revision_id)
        REFERENCES recruitment_applications(tenant_id, id, job_id, template_revision_id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_interview_job FOREIGN KEY (tenant_id, job_id)
        REFERENCES recruitment_jobs(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_interview_revision FOREIGN KEY (tenant_id, template_revision_id)
        REFERENCES recruitment_interview_template_revisions(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_interview_status CHECK (status IN (
        'INVITED','SCHEDULED','PREPARING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS','COMPLETED',
        'NO_ANSWER','DECLINED','FAILED','CANCELLED','EXPIRED')),
    CONSTRAINT ck_recruitment_interview_snapshot CHECK (
        jsonb_typeof(template_snapshot) = 'object' AND
        template_snapshot ?& ARRAY['introductionText','disclosureText','closingText','durationLimitSeconds','interactionLimits','sections'] AND
        jsonb_typeof(template_snapshot->'interactionLimits') = 'object' AND
        (template_snapshot->'interactionLimits') ?& ARRAY['repetitionLimit','clarificationLimit','silenceTimeoutSeconds','silencePromptLimit'] AND
        jsonb_typeof(template_snapshot->'sections') = 'array' AND jsonb_array_length(template_snapshot->'sections') > 0 AND
        (template_snapshot->>'durationLimitSeconds') ~ '^[1-9][0-9]*$' AND
        template_snapshot_sha256 ~ '^[0-9a-f]{64}$' AND btrim(template_snapshot_version) <> ''
    ),
    CONSTRAINT ck_recruitment_interview_score CHECK (overall_score IS NULL OR (overall_score >= 0 AND overall_score <= 100)),
    CONSTRAINT ck_recruitment_interview_recording CHECK (
        (recording_enabled AND recording_retention_days > 0) OR
        (NOT recording_enabled AND recording_retention_days = 0 AND recording_expires_at IS NULL)
    )
);

CREATE INDEX idx_recruitment_jobs_tenant_status_created_id ON recruitment_jobs(tenant_id, status, created_at DESC, id);
CREATE INDEX idx_recruitment_jobs_tenant_closing_id ON recruitment_jobs(tenant_id, closing_at, id);
CREATE INDEX idx_recruitment_jobs_stable_created ON recruitment_jobs(tenant_id, created_at DESC, id);
CREATE INDEX idx_recruitment_jobs_search ON recruitment_jobs USING gin ((coalesce(title,'') || ' ' || coalesce(department,'') || ' ' || coalesce(location,'')) gin_trgm_ops);
CREATE INDEX idx_recruitment_templates_tenant_archived_name_id ON recruitment_interview_templates(tenant_id, archived, name, id);
CREATE INDEX idx_recruitment_templates_search ON recruitment_interview_templates USING gin (name gin_trgm_ops);
CREATE INDEX idx_recruitment_revisions_template_number ON recruitment_interview_template_revisions(tenant_id, template_id, revision_number DESC, id);
CREATE INDEX idx_recruitment_candidates_tenant_name_id ON recruitment_candidates(tenant_id, normalized_name, id);
CREATE INDEX idx_recruitment_candidates_search ON recruitment_candidates USING gin ((normalized_name || ' ' || normalized_email || ' ' || coalesce(phone,'')) gin_trgm_ops);
CREATE INDEX idx_recruitment_applications_tenant_status_submitted_id ON recruitment_applications(tenant_id, status, submitted_at DESC, id);
CREATE INDEX idx_recruitment_applications_tenant_job_submitted_id ON recruitment_applications(tenant_id, job_id, submitted_at DESC, id);
CREATE INDEX idx_recruitment_applications_score_id ON recruitment_applications(tenant_id, overall_score, id);
CREATE INDEX idx_recruitment_interviews_tenant_status_scheduled_id ON recruitment_interviews(tenant_id, status, scheduled_at DESC, id);
CREATE INDEX idx_recruitment_interviews_tenant_job_scheduled_id ON recruitment_interviews(tenant_id, job_id, scheduled_at DESC, id);
CREATE INDEX idx_recruitment_interviews_score_id ON recruitment_interviews(tenant_id, overall_score, id);
