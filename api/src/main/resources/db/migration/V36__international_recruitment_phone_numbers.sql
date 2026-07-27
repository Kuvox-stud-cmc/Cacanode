ALTER TABLE recruitment_candidates DROP CONSTRAINT ck_recruitment_candidate_phone;
ALTER TABLE recruitment_candidates ADD CONSTRAINT ck_recruitment_candidate_phone
    CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{7,14}$');

ALTER TABLE recruitment_interview_call_attempts DROP CONSTRAINT ck_recruitment_call_attempt_destination;
ALTER TABLE recruitment_interview_call_attempts ADD CONSTRAINT ck_recruitment_call_attempt_destination
    CHECK (destination_e164 IS NULL OR destination_e164 ~ '^\+[1-9][0-9]{7,14}$');
