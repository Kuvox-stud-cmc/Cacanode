ALTER TABLE recruitment_jobs DROP CONSTRAINT ck_recruitment_job_cv_policy;
UPDATE recruitment_jobs SET cv_policy = 'DISABLED' WHERE cv_policy = 'NOT_ACCEPTED';
ALTER TABLE recruitment_jobs ADD CONSTRAINT ck_recruitment_job_cv_policy
    CHECK (cv_policy IN ('DISABLED','OPTIONAL','REQUIRED'));

ALTER TABLE recruitment_jobs ADD COLUMN experience_level VARCHAR(30);
UPDATE recruitment_jobs SET employment_type = upper(replace(employment_type, '-', '_'))
WHERE employment_type IS NOT NULL;
UPDATE recruitment_jobs SET employment_type = NULL
WHERE employment_type IS NOT NULL
  AND employment_type NOT IN ('FULL_TIME','PART_TIME','CONTRACT','TEMPORARY','INTERNSHIP');
UPDATE recruitment_jobs SET work_mode = upper(replace(work_mode, '-', '_'))
WHERE work_mode IS NOT NULL;
UPDATE recruitment_jobs SET work_mode = NULL
WHERE work_mode IS NOT NULL AND work_mode NOT IN ('ONSITE','REMOTE','HYBRID');
ALTER TABLE recruitment_jobs ADD CONSTRAINT ck_recruitment_job_employment_type
    CHECK (employment_type IS NULL OR employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','TEMPORARY','INTERNSHIP'));
ALTER TABLE recruitment_jobs ADD CONSTRAINT ck_recruitment_job_work_mode
    CHECK (work_mode IS NULL OR work_mode IN ('ONSITE','REMOTE','HYBRID'));
ALTER TABLE recruitment_jobs ADD CONSTRAINT ck_recruitment_job_experience_level
    CHECK (experience_level IS NULL OR experience_level IN ('ENTRY','JUNIOR','MID','SENIOR','LEAD','EXECUTIVE'));

ALTER TABLE recruitment_applications ADD COLUMN verified_at TIMESTAMP;
ALTER TABLE recruitment_applications ADD COLUMN withdrawn_at TIMESTAMP;
ALTER TABLE recruitment_applications ADD COLUMN locale VARCHAR(5);
ALTER TABLE recruitment_applications ADD COLUMN privacy_consent_at TIMESTAMP;
ALTER TABLE recruitment_applications ADD COLUMN cv_use_disclosed_at TIMESTAMP;
UPDATE recruitment_applications a
SET locale = j.language, privacy_consent_at = a.submitted_at,
    verified_at = CASE WHEN a.status <> 'SUBMITTED_UNVERIFIED' THEN a.submitted_at ELSE NULL END,
    withdrawn_at = CASE WHEN a.status = 'WITHDRAWN' THEN a.updated_at ELSE NULL END
FROM recruitment_jobs j
WHERE j.tenant_id = a.tenant_id AND j.id = a.job_id;
ALTER TABLE recruitment_applications ALTER COLUMN locale SET NOT NULL;
ALTER TABLE recruitment_applications ALTER COLUMN privacy_consent_at SET NOT NULL;
ALTER TABLE recruitment_applications ADD CONSTRAINT ck_recruitment_application_locale
    CHECK (locale IN ('vi-VN','en-US'));
ALTER TABLE recruitment_applications ADD CONSTRAINT ck_recruitment_application_verification
    CHECK ((status = 'SUBMITTED_UNVERIFIED' AND verified_at IS NULL)
        OR status = 'WITHDRAWN'
        OR (status <> 'SUBMITTED_UNVERIFIED' AND verified_at IS NOT NULL));
ALTER TABLE recruitment_applications ADD CONSTRAINT ck_recruitment_application_withdrawal
    CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL));
ALTER TABLE recruitment_applications ADD CONSTRAINT uq_recruitment_application_job_ref
    UNIQUE (tenant_id, id, job_id);

CREATE TABLE recruitment_public_jobs (
    job_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    public_id UUID NOT NULL,
    tenant_slug VARCHAR(160) NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    department VARCHAR(120),
    location VARCHAR(160),
    employment_type VARCHAR(30),
    work_mode VARCHAR(20),
    experience_level VARCHAR(30),
    language VARCHAR(5) NOT NULL,
    cv_policy VARCHAR(20) NOT NULL,
    cv_ai_disclosed BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP NOT NULL,
    closing_at TIMESTAMP NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(company_name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(department, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(location, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(description, '')), 'C')
    ) STORED,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_public_job_tenant_id UNIQUE (tenant_id, job_id),
    CONSTRAINT uq_recruitment_public_job_public_id UNIQUE (public_id),
    CONSTRAINT fk_recruitment_public_job_job FOREIGN KEY (tenant_id, job_id)
        REFERENCES recruitment_jobs(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_public_job_text CHECK (
        btrim(tenant_slug) <> '' AND btrim(company_name) <> '' AND
        btrim(title) <> '' AND btrim(description) <> ''),
    CONSTRAINT ck_recruitment_public_job_locale CHECK (language IN ('vi-VN','en-US')),
    CONSTRAINT ck_recruitment_public_job_cv_policy CHECK (cv_policy IN ('DISABLED','OPTIONAL','REQUIRED')),
    CONSTRAINT ck_recruitment_public_job_employment CHECK (employment_type IS NULL OR employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','TEMPORARY','INTERNSHIP')),
    CONSTRAINT ck_recruitment_public_job_work_mode CHECK (work_mode IS NULL OR work_mode IN ('ONSITE','REMOTE','HYBRID')),
    CONSTRAINT ck_recruitment_public_job_experience CHECK (experience_level IS NULL OR experience_level IN ('ENTRY','JUNIOR','MID','SENIOR','LEAD','EXECUTIVE')),
    CONSTRAINT ck_recruitment_public_job_dates CHECK (closing_at > published_at)
);

CREATE TABLE recruitment_application_email_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    application_id UUID NOT NULL,
    job_id UUID NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_email_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_recruitment_email_token_application FOREIGN KEY (tenant_id, application_id, job_id)
        REFERENCES recruitment_applications(tenant_id, id, job_id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_email_token_purpose CHECK (purpose IN ('VERIFICATION','MANAGEMENT')),
    CONSTRAINT ck_recruitment_email_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_email_token_expiry CHECK (
        expires_at > created_at AND
        ((purpose = 'VERIFICATION' AND expires_at <= created_at + INTERVAL '24 hours') OR
         (purpose = 'MANAGEMENT' AND expires_at <= created_at + INTERVAL '30 days'))),
    CONSTRAINT ck_recruitment_email_token_terminal CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);

CREATE TABLE recruitment_candidate_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    application_id UUID NOT NULL,
    job_id UUID NOT NULL,
    access_token_hash VARCHAR(64) NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    csrf_token_hash VARCHAR(64) NOT NULL,
    access_expires_at TIMESTAMP NOT NULL,
    refresh_expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_candidate_access_hash UNIQUE (access_token_hash),
    CONSTRAINT uq_recruitment_candidate_refresh_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_recruitment_candidate_session_application FOREIGN KEY (tenant_id, application_id, job_id)
        REFERENCES recruitment_applications(tenant_id, id, job_id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_candidate_session_hashes CHECK (
        access_token_hash ~ '^[0-9a-f]{64}$' AND refresh_token_hash ~ '^[0-9a-f]{64}$' AND
        csrf_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_candidate_session_expiry CHECK (
        access_expires_at > created_at AND refresh_expires_at > access_expires_at)
);

CREATE TABLE recruitment_application_cvs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    application_id UUID,
    job_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    storage_state VARCHAR(30) NOT NULL,
    quarantine_object_key VARCHAR(700),
    promoted_object_key VARCHAR(700),
    storage_reservation_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    retained_until TIMESTAMP,
    deletion_attempts INTEGER NOT NULL DEFAULT 0,
    deletion_next_attempt_at TIMESTAMP,
    deletion_last_error VARCHAR(500),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_cv_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_recruitment_cv_job FOREIGN KEY (tenant_id, job_id)
        REFERENCES recruitment_jobs(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_cv_application FOREIGN KEY (tenant_id, application_id, job_id)
        REFERENCES recruitment_applications(tenant_id, id, job_id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_cv_size CHECK (byte_size > 0 AND byte_size <= 5242880),
    CONSTRAINT ck_recruitment_cv_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_cv_state CHECK (storage_state IN ('QUARANTINED','PROMOTED','DELETION_PENDING','DELETION_FAILED','DELETED')),
    CONSTRAINT ck_recruitment_cv_attempts CHECK (deletion_attempts BETWEEN 0 AND 10),
    CONSTRAINT ck_recruitment_cv_objects CHECK (
        (storage_state = 'QUARANTINED' AND quarantine_object_key IS NOT NULL AND promoted_object_key IS NULL AND deleted_at IS NULL) OR
        (storage_state IN ('PROMOTED','DELETION_PENDING','DELETION_FAILED') AND promoted_object_key IS NOT NULL AND deleted_at IS NULL) OR
        (storage_state = 'DELETED' AND deleted_at IS NOT NULL AND active = FALSE)),
    CONSTRAINT ck_recruitment_cv_application_state CHECK (
        (storage_state = 'QUARANTINED' AND application_id IS NULL) OR
        (storage_state <> 'QUARANTINED' AND application_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_recruitment_active_cv_per_application
    ON recruitment_application_cvs(tenant_id, application_id) WHERE active AND application_id IS NOT NULL;
CREATE INDEX idx_recruitment_public_jobs_newest ON recruitment_public_jobs(published_at DESC, public_id);
CREATE INDEX idx_recruitment_public_jobs_closing ON recruitment_public_jobs(closing_at, public_id);
CREATE INDEX idx_recruitment_public_jobs_tenant_newest ON recruitment_public_jobs(tenant_slug, published_at DESC, public_id);
CREATE INDEX idx_recruitment_public_jobs_search_vector ON recruitment_public_jobs USING gin(search_vector);
CREATE INDEX idx_recruitment_public_jobs_trigram ON recruitment_public_jobs USING gin (
    (coalesce(title,'') || ' ' || coalesce(company_name,'') || ' ' || coalesce(department,'') || ' ' || coalesce(location,'')) gin_trgm_ops);
CREATE INDEX idx_recruitment_email_tokens_cleanup ON recruitment_application_email_tokens(expires_at)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
CREATE INDEX idx_recruitment_sessions_access ON recruitment_candidate_sessions(access_expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_recruitment_sessions_refresh ON recruitment_candidate_sessions(refresh_expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_recruitment_cv_cleanup ON recruitment_application_cvs(deletion_next_attempt_at, retained_until, id)
    WHERE storage_state IN ('PROMOTED','DELETION_PENDING','DELETION_FAILED') AND active;

INSERT INTO recruitment_public_jobs (
    job_id, tenant_id, public_id, tenant_slug, company_name, title, description,
    department, location, employment_type, work_mode, experience_level, language,
    cv_policy, cv_ai_disclosed, published_at, closing_at, created_at, updated_at)
SELECT id, tenant_id, public_id, frozen_company_slug, frozen_company_name, title, description,
       department, location, employment_type, work_mode, experience_level, language,
       cv_policy, effective_cv_ai_mode <> 'OFF', published_at, closing_at, created_at, updated_at
FROM recruitment_jobs
WHERE status = 'PUBLISHED' AND closing_at > NOW();
