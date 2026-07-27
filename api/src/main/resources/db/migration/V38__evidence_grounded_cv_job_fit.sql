ALTER TABLE recruitment_cv_analyses
    ADD COLUMN contract_version VARCHAR(10) NOT NULL DEFAULT '1.1',
    ADD COLUMN analysis_revision INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN refresh_request_id UUID,
    ADD COLUMN fit_score_percent INTEGER,
    ADD COLUMN fit_confidence VARCHAR(10),
    ADD COLUMN fit_explanation TEXT,
    ADD COLUMN strengths JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN gaps JSONB NOT NULL DEFAULT '[]'::jsonb;

WITH ranked AS (
    SELECT id,ROW_NUMBER() OVER (PARTITION BY tenant_id,application_id ORDER BY created_at,id) AS revision
    FROM recruitment_cv_analyses
)
UPDATE recruitment_cv_analyses analysis
SET contract_version='1.1',analysis_revision=ranked.revision::integer
FROM ranked WHERE ranked.id=analysis.id;

ALTER TABLE recruitment_cv_analyses
    DROP CONSTRAINT uq_recruitment_cv_analysis_application_identity,
    ADD CONSTRAINT uq_recruitment_cv_analysis_revision
        UNIQUE (tenant_id,application_id,analysis_revision),
    ADD CONSTRAINT uq_recruitment_cv_analysis_refresh_request
        UNIQUE (tenant_id,application_id,refresh_request_id),
    ADD CONSTRAINT ck_recruitment_cv_analysis_contract
        CHECK (contract_version IN ('1.0','1.1','1.2')),
    ADD CONSTRAINT ck_recruitment_cv_analysis_revision
        CHECK (analysis_revision >= 1),
    ADD CONSTRAINT ck_recruitment_cv_analysis_fit_score
        CHECK (fit_score_percent IS NULL OR fit_score_percent BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_recruitment_cv_analysis_fit_confidence
        CHECK (fit_confidence IS NULL OR fit_confidence IN ('LOW','MEDIUM','HIGH')),
    ADD CONSTRAINT ck_recruitment_cv_analysis_fit_json
        CHECK (jsonb_typeof(strengths)='array' AND jsonb_array_length(strengths) <= 50
            AND jsonb_typeof(gaps)='array' AND jsonb_array_length(gaps) <= 50),
    ADD CONSTRAINT ck_recruitment_cv_analysis_fit_output CHECK (
        (contract_version <> '1.2' AND fit_score_percent IS NULL AND fit_confidence IS NULL
            AND fit_explanation IS NULL AND strengths='[]'::jsonb AND gaps='[]'::jsonb)
        OR (contract_version='1.2' AND status='COMPLETED' AND fit_score_percent IS NOT NULL
            AND fit_confidence IS NOT NULL AND btrim(fit_explanation) <> '')
        OR (contract_version='1.2' AND status<>'COMPLETED' AND fit_score_percent IS NULL
            AND fit_confidence IS NULL AND fit_explanation IS NULL
            AND strengths='[]'::jsonb AND gaps='[]'::jsonb)
    );

ALTER TABLE recruitment_applications
    ADD COLUMN pending_cv_analysis_id UUID;

ALTER TABLE recruitment_applications
    ADD CONSTRAINT fk_recruitment_application_pending_cv_analysis
    FOREIGN KEY (tenant_id,pending_cv_analysis_id,id)
    REFERENCES recruitment_cv_analyses(tenant_id,id,application_id)
    ON DELETE SET NULL (pending_cv_analysis_id);

CREATE INDEX idx_recruitment_cv_analysis_refresh
    ON recruitment_cv_analyses(tenant_id,application_id,analysis_revision DESC);
