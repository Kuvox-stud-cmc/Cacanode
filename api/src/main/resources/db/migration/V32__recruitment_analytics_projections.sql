CREATE TABLE analytics_recruitment_job_projection (
    job_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP NULL,
    paused_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    archived_at TIMESTAMP NULL
);

CREATE INDEX idx_analytics_recruitment_job_tenant_status_time
    ON analytics_recruitment_job_projection (tenant_id, status, updated_at);
CREATE INDEX idx_analytics_recruitment_job_tenant_published
    ON analytics_recruitment_job_projection (tenant_id, published_at);

CREATE TABLE analytics_recruitment_application_projection (
    application_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    job_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP NULL,
    withdrawn_at TIMESTAMP NULL
);

CREATE INDEX idx_analytics_recruitment_application_tenant_status_time
    ON analytics_recruitment_application_projection (tenant_id, status, updated_at);
CREATE INDEX idx_analytics_recruitment_application_tenant_verified
    ON analytics_recruitment_application_projection (tenant_id, verified_at);

CREATE TABLE analytics_recruitment_interview_projection (
    interview_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    application_id UUID NOT NULL,
    job_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    invited_at TIMESTAMP NULL,
    scheduled_start_at TIMESTAMPTZ NULL,
    scheduled_end_at TIMESTAMPTZ NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    cancelled_at TIMESTAMP NULL,
    expired_at TIMESTAMP NULL
);

CREATE INDEX idx_analytics_recruitment_interview_tenant_status_time
    ON analytics_recruitment_interview_projection (tenant_id, status, updated_at);
CREATE INDEX idx_analytics_recruitment_interview_tenant_completed
    ON analytics_recruitment_interview_projection (tenant_id, completed_at);

INSERT INTO analytics_recruitment_job_projection
    (job_id,tenant_id,status,created_at,updated_at,published_at,paused_at,closed_at,archived_at)
SELECT id,tenant_id,status,created_at,updated_at,published_at,paused_at,closed_at,archived_at
FROM recruitment_jobs;

INSERT INTO analytics_recruitment_application_projection
    (application_id,tenant_id,job_id,status,created_at,updated_at,submitted_at,verified_at,withdrawn_at)
SELECT id,tenant_id,job_id,status,created_at,updated_at,submitted_at,verified_at,withdrawn_at
FROM recruitment_applications;

INSERT INTO analytics_recruitment_interview_projection
    (interview_id,tenant_id,application_id,job_id,status,created_at,updated_at,invited_at,
     scheduled_start_at,scheduled_end_at,started_at,completed_at,cancelled_at,expired_at)
SELECT id,tenant_id,application_id,job_id,status,created_at,updated_at,invited_at,
       scheduled_start_at,scheduled_end_at,started_at,completed_at,cancelled_at,expired_at
FROM recruitment_interviews;
