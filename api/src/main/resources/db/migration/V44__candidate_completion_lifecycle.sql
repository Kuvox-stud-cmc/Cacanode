ALTER TABLE recruitment_applications
    DROP CONSTRAINT ck_recruitment_application_status,
    DROP CONSTRAINT ck_recruitment_application_verification;

ALTER TABLE recruitment_applications
    ALTER COLUMN submitted_at DROP NOT NULL,
    ALTER COLUMN submitted_at DROP DEFAULT,
    ALTER COLUMN privacy_consent_at DROP NOT NULL;

ALTER TABLE recruitment_applications
    ADD CONSTRAINT ck_recruitment_application_status CHECK (status IN (
        'AWAITING_CANDIDATE','SUBMITTED_UNVERIFIED','SUBMITTED','INTERVIEW_INVITED',
        'INTERVIEW_SCHEDULED','INTERVIEW_COMPLETED','UNDER_REVIEW','SHORTLISTED',
        'REJECTED','WITHDRAWN')),
    ADD CONSTRAINT ck_recruitment_application_submission_consent CHECK (
        (status = 'AWAITING_CANDIDATE' AND submitted_at IS NULL AND privacy_consent_at IS NULL)
        OR
        (status <> 'AWAITING_CANDIDATE' AND submitted_at IS NOT NULL AND privacy_consent_at IS NOT NULL)),
    ADD CONSTRAINT ck_recruitment_application_verification CHECK (
        (status IN ('AWAITING_CANDIDATE','SUBMITTED_UNVERIFIED') AND verified_at IS NULL)
        OR status = 'WITHDRAWN'
        OR (status NOT IN ('AWAITING_CANDIDATE','SUBMITTED_UNVERIFIED','WITHDRAWN') AND verified_at IS NOT NULL));

ALTER TABLE recruitment_application_email_tokens
    DROP CONSTRAINT ck_recruitment_email_token_purpose,
    DROP CONSTRAINT ck_recruitment_email_token_expiry;

ALTER TABLE recruitment_application_email_tokens
    ADD CONSTRAINT ck_recruitment_email_token_purpose
        CHECK (purpose IN ('VERIFICATION','MANAGEMENT','COMPLETION','DELETION_CONFIRMATION')),
    ADD CONSTRAINT ck_recruitment_email_token_expiry CHECK (
        expires_at > created_at AND
        ((purpose='VERIFICATION' AND expires_at <= created_at + INTERVAL '24 hours') OR
         (purpose='MANAGEMENT' AND expires_at <= created_at + INTERVAL '30 days') OR
         (purpose='COMPLETION' AND expires_at <= created_at + INTERVAL '7 days') OR
         (purpose='DELETION_CONFIRMATION' AND expires_at <= created_at + INTERVAL '1 hour')));
