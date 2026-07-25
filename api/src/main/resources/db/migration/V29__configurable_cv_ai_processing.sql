ALTER TABLE recruitment_tenant_settings DROP CONSTRAINT ck_recruitment_settings_cv_ai;
ALTER TABLE recruitment_tenant_settings ALTER COLUMN cv_ai_mode TYPE VARCHAR(30);
UPDATE recruitment_tenant_settings SET cv_ai_mode = CASE cv_ai_mode
    WHEN 'OPTIONAL' THEN 'SUMMARY_ONLY'
    WHEN 'REQUIRED' THEN 'PERSONALIZED_QUESTIONS'
    ELSE cv_ai_mode END;
ALTER TABLE recruitment_tenant_settings ADD CONSTRAINT ck_recruitment_settings_cv_ai
    CHECK (cv_ai_mode IN ('OFF','SUMMARY_ONLY','PERSONALIZED_QUESTIONS'));

ALTER TABLE recruitment_jobs DROP CONSTRAINT ck_recruitment_job_cv_ai;
ALTER TABLE recruitment_jobs DROP CONSTRAINT ck_recruitment_job_effective_modes;
ALTER TABLE recruitment_jobs ALTER COLUMN cv_ai_mode_override TYPE VARCHAR(30);
ALTER TABLE recruitment_jobs ALTER COLUMN effective_cv_ai_mode TYPE VARCHAR(30);
UPDATE recruitment_jobs SET cv_ai_mode_override = CASE cv_ai_mode_override
    WHEN 'OPTIONAL' THEN 'SUMMARY_ONLY'
    WHEN 'REQUIRED' THEN 'PERSONALIZED_QUESTIONS'
    ELSE cv_ai_mode_override END;
UPDATE recruitment_jobs SET effective_cv_ai_mode = CASE effective_cv_ai_mode
    WHEN 'OPTIONAL' THEN 'SUMMARY_ONLY'
    WHEN 'REQUIRED' THEN 'PERSONALIZED_QUESTIONS'
    ELSE effective_cv_ai_mode END;
ALTER TABLE recruitment_jobs ADD CONSTRAINT ck_recruitment_job_cv_ai
    CHECK (cv_ai_mode_override IS NULL OR cv_ai_mode_override IN ('OFF','SUMMARY_ONLY','PERSONALIZED_QUESTIONS'));
ALTER TABLE recruitment_jobs ADD CONSTRAINT ck_recruitment_job_effective_modes CHECK (
    (effective_automation_mode IS NULL OR effective_automation_mode IN ('MANUAL','AUTO_INVITE_ALL','AUTO_INVITE_MATCHING')) AND
    (effective_cv_ai_mode IS NULL OR effective_cv_ai_mode IN ('OFF','SUMMARY_ONLY','PERSONALIZED_QUESTIONS'))
);

ALTER TABLE recruitment_public_jobs
    ADD COLUMN cv_ai_mode VARCHAR(30) NOT NULL DEFAULT 'OFF',
    ADD CONSTRAINT ck_recruitment_public_job_cv_ai_mode
        CHECK (cv_ai_mode IN ('OFF','SUMMARY_ONLY','PERSONALIZED_QUESTIONS'));
UPDATE recruitment_public_jobs p SET cv_ai_mode = COALESCE(j.effective_cv_ai_mode,'OFF'),
    cv_ai_disclosed = COALESCE(j.effective_cv_ai_mode,'OFF') <> 'OFF'
FROM recruitment_jobs j WHERE j.tenant_id=p.tenant_id AND j.id=p.job_id;

ALTER TABLE recruitment_applications DROP CONSTRAINT ck_recruitment_application_cv_analysis;
ALTER TABLE recruitment_applications
    ADD COLUMN cv_ai_mode_snapshot VARCHAR(30) NOT NULL DEFAULT 'OFF',
    ADD COLUMN cv_ai_consent_at TIMESTAMP,
    ADD COLUMN cv_ai_policy_version VARCHAR(80) NOT NULL DEFAULT 'cv-redaction-v1',
    ADD COLUMN cv_ai_model_version VARCHAR(120) NOT NULL DEFAULT 'resume-analysis-v1',
    ADD COLUMN active_cv_analysis_id UUID,
    ADD CONSTRAINT ck_recruitment_application_cv_ai_snapshot
        CHECK (cv_ai_mode_snapshot IN ('OFF','SUMMARY_ONLY','PERSONALIZED_QUESTIONS')),
    ADD CONSTRAINT ck_recruitment_application_cv_analysis
        CHECK (cv_analysis_status IN ('NOT_REQUESTED','PENDING','COMPLETED','FAILED','SKIPPED_QUOTA','CANCELLED')),
    ADD CONSTRAINT ck_recruitment_application_cv_ai_versions CHECK (
        btrim(cv_ai_policy_version) <> '' AND btrim(cv_ai_model_version) <> ''
    );
UPDATE recruitment_applications a SET
    cv_ai_mode_snapshot=COALESCE(j.effective_cv_ai_mode,'OFF'),
    cv_ai_consent_at=CASE
        WHEN a.cv_present AND COALESCE(j.effective_cv_ai_mode,'OFF') <> 'OFF'
            THEN a.cv_use_disclosed_at
        ELSE NULL END
FROM recruitment_jobs j WHERE j.tenant_id=a.tenant_id AND j.id=a.job_id;

CREATE TABLE recruitment_cv_analyses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    application_id UUID NOT NULL,
    cv_id UUID NOT NULL,
    cv_sha256 VARCHAR(64) NOT NULL,
    analysis_mode VARCHAR(30) NOT NULL,
    policy_version VARCHAR(80) NOT NULL,
    model_version VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_event_id UUID,
    request_payload JSONB,
    request_payload_sha256 VARCHAR(64),
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    next_publish_at TIMESTAMP,
    published_at TIMESTAMP,
    completed_at TIMESTAMP,
    failure_code VARCHAR(100),
    summary TEXT,
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    personalized_questions JSONB NOT NULL DEFAULT '[]'::jsonb,
    outcome_event_id UUID,
    outcome_payload_sha256 VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_cv_analysis_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT uq_recruitment_cv_analysis_application_identity UNIQUE
        (tenant_id,application_id,cv_sha256,analysis_mode,policy_version,model_version),
    CONSTRAINT uq_recruitment_cv_analysis_application_ref UNIQUE (tenant_id,id,application_id),
    CONSTRAINT uq_recruitment_cv_analysis_outcome_event UNIQUE (outcome_event_id),
    CONSTRAINT fk_recruitment_cv_analysis_application FOREIGN KEY (tenant_id,application_id)
        REFERENCES recruitment_applications(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_cv_analysis_cv FOREIGN KEY (tenant_id,cv_id)
        REFERENCES recruitment_application_cvs(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_cv_analysis_hashes CHECK (
        cv_sha256 ~ '^[0-9a-f]{64}$' AND
        (request_payload_sha256 IS NULL OR request_payload_sha256 ~ '^[0-9a-f]{64}$') AND
        (outcome_payload_sha256 IS NULL OR outcome_payload_sha256 ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_recruitment_cv_analysis_mode CHECK
        (analysis_mode IN ('SUMMARY_ONLY','PERSONALIZED_QUESTIONS')),
    CONSTRAINT ck_recruitment_cv_analysis_status CHECK
        (status IN ('QUEUED','PUBLISHED','COMPLETED','FAILED','SKIPPED_QUOTA','CANCELLED')),
    CONSTRAINT ck_recruitment_cv_analysis_attempts CHECK (publish_attempts BETWEEN 0 AND 10),
    CONSTRAINT ck_recruitment_cv_analysis_json CHECK (
        (request_payload IS NULL OR jsonb_typeof(request_payload)='object') AND
        jsonb_typeof(evidence)='array' AND jsonb_array_length(evidence) <= 250 AND
        jsonb_typeof(skills)='array' AND jsonb_array_length(skills) <= 100 AND
        jsonb_typeof(personalized_questions)='array' AND jsonb_array_length(personalized_questions) <= 2
    ),
    CONSTRAINT ck_recruitment_cv_analysis_terminal CHECK (
        (status IN ('QUEUED','PUBLISHED') AND completed_at IS NULL) OR
        (status IN ('COMPLETED','FAILED','SKIPPED_QUOTA','CANCELLED') AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_recruitment_cv_analysis_request CHECK (
        (status='SKIPPED_QUOTA' AND request_event_id IS NULL AND request_payload IS NULL) OR
        (status<>'SKIPPED_QUOTA' AND request_event_id IS NOT NULL AND request_payload IS NOT NULL
            AND request_payload_sha256 IS NOT NULL)
    ),
    CONSTRAINT ck_recruitment_cv_analysis_output CHECK (
        (status='COMPLETED' AND summary IS NOT NULL AND failure_code IS NULL AND outcome_event_id IS NOT NULL) OR
        (status='FAILED' AND failure_code IS NOT NULL AND summary IS NULL) OR
        (status IN ('QUEUED','PUBLISHED','SKIPPED_QUOTA','CANCELLED') AND summary IS NULL)
    )
);

ALTER TABLE recruitment_applications ADD CONSTRAINT fk_recruitment_application_active_cv_analysis
    FOREIGN KEY (tenant_id,active_cv_analysis_id,id)
    REFERENCES recruitment_cv_analyses(tenant_id,id,application_id)
    ON DELETE SET NULL (active_cv_analysis_id);

CREATE INDEX idx_recruitment_cv_analysis_due
    ON recruitment_cv_analyses(next_publish_at,id)
    WHERE status='QUEUED' AND publish_attempts < 10;
CREATE INDEX idx_recruitment_cv_analysis_application
    ON recruitment_cv_analyses(tenant_id,application_id,created_at DESC);
CREATE INDEX idx_recruitment_cv_analysis_cleanup
    ON recruitment_cv_analyses(completed_at,id)
    WHERE status IN ('COMPLETED','FAILED','SKIPPED_QUOTA','CANCELLED');

CREATE TABLE recruitment_cv_analysis_inbox (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    analysis_id UUID NOT NULL,
    application_id UUID NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    processing_result VARCHAR(30) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_cv_analysis_inbox_semantic UNIQUE (tenant_id,analysis_id),
    CONSTRAINT ck_recruitment_cv_analysis_inbox_hash CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_cv_analysis_inbox_result CHECK
        (processing_result IN ('APPLIED_COMPLETED','APPLIED_FAILED','IGNORED_CANCELLED'))
);

CREATE OR REPLACE FUNCTION recruitment_cv_ai_snapshots_immutable() RETURNS trigger AS $$
BEGIN
    IF NEW.cv_ai_mode_snapshot IS DISTINCT FROM OLD.cv_ai_mode_snapshot
       OR NEW.cv_ai_consent_at IS DISTINCT FROM OLD.cv_ai_consent_at
       OR NEW.cv_ai_policy_version IS DISTINCT FROM OLD.cv_ai_policy_version
       OR NEW.cv_ai_model_version IS DISTINCT FROM OLD.cv_ai_model_version THEN
        RAISE EXCEPTION 'CV AI application snapshots are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_recruitment_cv_ai_snapshots_immutable
    BEFORE UPDATE ON recruitment_applications
    FOR EACH ROW EXECUTE FUNCTION recruitment_cv_ai_snapshots_immutable();
