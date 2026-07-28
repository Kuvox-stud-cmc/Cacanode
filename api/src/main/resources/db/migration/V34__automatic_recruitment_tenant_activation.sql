ALTER TABLE recruitment_tenant_activation
    DROP CONSTRAINT ck_recruitment_activation_stage;

ALTER TABLE recruitment_tenant_activation
    ADD CONSTRAINT ck_recruitment_activation_stage
    CHECK (rollout_stage IN ('OFF','AUTO','INTERNAL','PILOT','GA'));
