ALTER TABLE usage_metrics
    ADD COLUMN verified_application_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN cv_analysis_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN interview_seconds BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_usage_verified_applications_nonnegative CHECK (verified_application_count >= 0),
    ADD CONSTRAINT ck_usage_cv_analyses_nonnegative CHECK (cv_analysis_count >= 0),
    ADD CONSTRAINT ck_usage_interview_seconds_nonnegative CHECK (interview_seconds >= 0);

CREATE TABLE hiring_quota_consumptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    quota_kind VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    consumed_amount BIGINT NOT NULL,
    remaining_amount BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hiring_consumption_semantic UNIQUE (tenant_id, quota_kind, aggregate_id),
    CONSTRAINT ck_hiring_consumption_kind CHECK (quota_kind IN ('VERIFIED_APPLICATION', 'CV_ANALYSIS')),
    CONSTRAINT ck_hiring_consumption_amounts CHECK (consumed_amount > 0 AND remaining_amount >= 0),
    CONSTRAINT ck_hiring_consumption_period CHECK (period_end > period_start)
);
CREATE INDEX idx_hiring_consumption_tenant_kind_period
    ON hiring_quota_consumptions(tenant_id, quota_kind, period_start);

CREATE TABLE hiring_quota_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    quota_kind VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    state VARCHAR(20) NOT NULL,
    reserved_amount BIGINT NOT NULL,
    settled_amount BIGINT,
    settlement_period_start TIMESTAMP,
    settlement_period_end TIMESTAMP,
    remaining_amount BIGINT,
    expires_at TIMESTAMP,
    terminal_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hiring_reservation_semantic UNIQUE (tenant_id, quota_kind, aggregate_id),
    CONSTRAINT ck_hiring_reservation_kind CHECK (quota_kind IN ('ACTIVE_JOB', 'RECRUITMENT_STORAGE', 'INTERVIEW_SECONDS')),
    CONSTRAINT ck_hiring_reservation_state CHECK (state IN ('RESERVED', 'COMMITTED', 'SETTLED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_hiring_reservation_amounts CHECK (
        reserved_amount > 0 AND (settled_amount IS NULL OR settled_amount >= 0)
        AND (remaining_amount IS NULL OR remaining_amount >= 0)
    ),
    CONSTRAINT ck_hiring_active_job_amount CHECK (quota_kind <> 'ACTIVE_JOB' OR reserved_amount = 1),
    CONSTRAINT ck_hiring_kind_state CHECK (
        (quota_kind = 'ACTIVE_JOB' AND state IN ('RESERVED', 'RELEASED')) OR
        (quota_kind = 'RECRUITMENT_STORAGE' AND state IN ('RESERVED', 'COMMITTED', 'RELEASED', 'EXPIRED')) OR
        (quota_kind = 'INTERVIEW_SECONDS' AND state IN ('RESERVED', 'SETTLED', 'RELEASED', 'EXPIRED'))
    ),
    CONSTRAINT ck_hiring_expiry CHECK (
        (quota_kind = 'ACTIVE_JOB' AND expires_at IS NULL) OR
        (quota_kind IN ('RECRUITMENT_STORAGE', 'INTERVIEW_SECONDS') AND expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_hiring_settlement_window CHECK (
        (settlement_period_start IS NULL AND settlement_period_end IS NULL) OR
        (settlement_period_start IS NOT NULL AND settlement_period_end > settlement_period_start)
    )
);
CREATE INDEX idx_hiring_reservation_tenant_kind_state
    ON hiring_quota_reservations(tenant_id, quota_kind, state);
CREATE INDEX idx_hiring_reservation_expiry
    ON hiring_quota_reservations(expires_at)
    WHERE state = 'RESERVED' AND expires_at IS NOT NULL;

UPDATE billing_subscriptions
SET entitlement_snapshot = entitlement_snapshot || CASE plan_code
    WHEN 'STARTER' THEN '{"maxActiveJobs":1,"maxVerifiedApplications":25,"maxInterviewSeconds":0,"maxCvAnalyses":0,"maxRecruitmentStorageBytes":52428800}'::jsonb
    WHEN 'TRIAL' THEN '{"maxActiveJobs":1,"maxVerifiedApplications":25,"maxInterviewSeconds":1200,"maxCvAnalyses":5,"maxRecruitmentStorageBytes":104857600}'::jsonb
    WHEN 'PRO' THEN '{"maxActiveJobs":3,"maxVerifiedApplications":150,"maxInterviewSeconds":3600,"maxCvAnalyses":100,"maxRecruitmentStorageBytes":1073741824}'::jsonb
    WHEN 'BUSINESS' THEN '{"maxActiveJobs":10,"maxVerifiedApplications":1000,"maxInterviewSeconds":18000,"maxCvAnalyses":500,"maxRecruitmentStorageBytes":10737418240}'::jsonb
    ELSE '{"maxActiveJobs":0,"maxVerifiedApplications":0,"maxInterviewSeconds":0,"maxCvAnalyses":0,"maxRecruitmentStorageBytes":0}'::jsonb
END;

UPDATE billing_payment_orders
SET entitlement_snapshot = entitlement_snapshot || CASE requested_plan
    WHEN 'STARTER' THEN '{"maxActiveJobs":1,"maxVerifiedApplications":25,"maxInterviewSeconds":0,"maxCvAnalyses":0,"maxRecruitmentStorageBytes":52428800}'::jsonb
    WHEN 'TRIAL' THEN '{"maxActiveJobs":1,"maxVerifiedApplications":25,"maxInterviewSeconds":1200,"maxCvAnalyses":5,"maxRecruitmentStorageBytes":104857600}'::jsonb
    WHEN 'PRO' THEN '{"maxActiveJobs":3,"maxVerifiedApplications":150,"maxInterviewSeconds":3600,"maxCvAnalyses":100,"maxRecruitmentStorageBytes":1073741824}'::jsonb
    WHEN 'BUSINESS' THEN '{"maxActiveJobs":10,"maxVerifiedApplications":1000,"maxInterviewSeconds":18000,"maxCvAnalyses":500,"maxRecruitmentStorageBytes":10737418240}'::jsonb
    ELSE '{"maxActiveJobs":0,"maxVerifiedApplications":0,"maxInterviewSeconds":0,"maxCvAnalyses":0,"maxRecruitmentStorageBytes":0}'::jsonb
END;

UPDATE billing_subscriptions
SET catalog_version = '2026-07-23'
WHERE status IN ('TRIAL', 'STARTER', 'ACTIVE', 'GRACE', 'ENTERPRISE');

ALTER TABLE billing_subscriptions ADD CONSTRAINT ck_billing_subscription_hiring_entitlements CHECK (
    entitlement_snapshot ? 'maxActiveJobs' AND entitlement_snapshot ? 'maxVerifiedApplications' AND
    entitlement_snapshot ? 'maxInterviewSeconds' AND entitlement_snapshot ? 'maxCvAnalyses' AND
    entitlement_snapshot ? 'maxRecruitmentStorageBytes' AND
    jsonb_typeof(entitlement_snapshot->'maxActiveJobs') = 'number' AND (entitlement_snapshot->>'maxActiveJobs')::numeric >= 0 AND
    jsonb_typeof(entitlement_snapshot->'maxVerifiedApplications') = 'number' AND (entitlement_snapshot->>'maxVerifiedApplications')::numeric >= 0 AND
    jsonb_typeof(entitlement_snapshot->'maxInterviewSeconds') = 'number' AND (entitlement_snapshot->>'maxInterviewSeconds')::numeric >= 0 AND
    jsonb_typeof(entitlement_snapshot->'maxCvAnalyses') = 'number' AND (entitlement_snapshot->>'maxCvAnalyses')::numeric >= 0 AND
    jsonb_typeof(entitlement_snapshot->'maxRecruitmentStorageBytes') = 'number' AND (entitlement_snapshot->>'maxRecruitmentStorageBytes')::numeric >= 0
);

ALTER TABLE billing_payment_orders ADD CONSTRAINT ck_billing_payment_hiring_entitlements CHECK (
    entitlement_snapshot ? 'maxActiveJobs' AND entitlement_snapshot ? 'maxVerifiedApplications' AND
    entitlement_snapshot ? 'maxInterviewSeconds' AND entitlement_snapshot ? 'maxCvAnalyses' AND
    entitlement_snapshot ? 'maxRecruitmentStorageBytes' AND
    jsonb_typeof(entitlement_snapshot->'maxActiveJobs') = 'number' AND (entitlement_snapshot->>'maxActiveJobs')::numeric >= 0 AND
    jsonb_typeof(entitlement_snapshot->'maxVerifiedApplications') = 'number' AND (entitlement_snapshot->>'maxVerifiedApplications')::numeric >= 0 AND
    jsonb_typeof(entitlement_snapshot->'maxInterviewSeconds') = 'number' AND (entitlement_snapshot->>'maxInterviewSeconds')::numeric >= 0 AND
    jsonb_typeof(entitlement_snapshot->'maxCvAnalyses') = 'number' AND (entitlement_snapshot->>'maxCvAnalyses')::numeric >= 0 AND
    jsonb_typeof(entitlement_snapshot->'maxRecruitmentStorageBytes') = 'number' AND (entitlement_snapshot->>'maxRecruitmentStorageBytes')::numeric >= 0
);
