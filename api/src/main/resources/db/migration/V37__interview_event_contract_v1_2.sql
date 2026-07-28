ALTER TABLE recruitment_interview_event_inbox
    DROP CONSTRAINT ck_recruitment_event_schema;

ALTER TABLE recruitment_interview_event_inbox
    ADD CONSTRAINT ck_recruitment_event_schema
        CHECK (schema_version IN ('1.0','1.1','1.2'));

ALTER TABLE recruitment_interview_event_inbox
    DROP CONSTRAINT uq_recruitment_event_semantic;

ALTER TABLE recruitment_interview_event_inbox
    ADD CONSTRAINT uq_recruitment_event_semantic
        UNIQUE (tenant_id,session_id,call_attempt_id,event_type,semantic_key);
