ALTER TABLE recruitment_interview_call_attempts
    ADD CONSTRAINT uq_recruitment_call_attempt_session_binding
        UNIQUE (tenant_id,id,session_id);

ALTER TABLE recruitment_twilio_callback_inbox DROP CONSTRAINT ck_recruitment_twilio_inbox_kind;
ALTER TABLE recruitment_twilio_callback_inbox ADD CONSTRAINT ck_recruitment_twilio_inbox_kind CHECK
    (callback_kind IN ('VOICE','CONSENT','STATUS','STREAM_STATUS','RECORDING_STATUS','FALLBACK'));

CREATE TABLE recruitment_interview_event_inbox (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL,
    call_attempt_id UUID NOT NULL,
    schema_version VARCHAR(8) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    semantic_key VARCHAR(160) NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    canonical_payload JSONB NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_event_semantic UNIQUE (tenant_id,session_id,event_type,semantic_key),
    CONSTRAINT fk_recruitment_event_session FOREIGN KEY (tenant_id,session_id)
        REFERENCES recruitment_interviews(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_event_attempt FOREIGN KEY (tenant_id,call_attempt_id,session_id)
        REFERENCES recruitment_interview_call_attempts(tenant_id,id,session_id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_event_schema CHECK (schema_version IN ('1.0','1.1')),
    CONSTRAINT ck_recruitment_event_hash CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_event_status CHECK (processing_status IN ('APPLIED','DUPLICATE','PENDING_TURNS'))
);

CREATE TABLE recruitment_interview_transcript_turns (
    turn_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL,
    call_attempt_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    sequence_number INTEGER NOT NULL,
    speaker VARCHAR(20) NOT NULL,
    turn_kind VARCHAR(30) NOT NULL,
    section_id UUID,
    question_id UUID,
    language_tag VARCHAR(5) NOT NULL,
    started_at_epoch_ms BIGINT NOT NULL,
    ended_at_epoch_ms BIGINT NOT NULL,
    transcript TEXT NOT NULL,
    interrupted BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_transcript_tenant_id UNIQUE (tenant_id,turn_id),
    CONSTRAINT uq_recruitment_transcript_sequence UNIQUE (tenant_id,session_id,sequence_number),
    CONSTRAINT fk_recruitment_transcript_session FOREIGN KEY (tenant_id,session_id)
        REFERENCES recruitment_interviews(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_transcript_attempt FOREIGN KEY (tenant_id,call_attempt_id,session_id)
        REFERENCES recruitment_interview_call_attempts(tenant_id,id,session_id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_transcript_event FOREIGN KEY (event_id)
        REFERENCES recruitment_interview_event_inbox(event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_transcript_sequence CHECK (sequence_number BETWEEN 0 AND 500),
    CONSTRAINT ck_recruitment_transcript_speaker CHECK (speaker IN ('CANDIDATE','INTERVIEWER','SYSTEM')),
    CONSTRAINT ck_recruitment_transcript_kind CHECK (turn_kind IN ('INTRODUCTION','TRANSITION','QUESTION','ACKNOWLEDGEMENT','FOLLOW_UP','CLARIFICATION','REPETITION','SILENCE_PROMPT','CANDIDATE_UTTERANCE','CLOSING')),
    CONSTRAINT ck_recruitment_transcript_language CHECK (language_tag IN ('vi-VN','en-US')),
    CONSTRAINT ck_recruitment_transcript_time CHECK (started_at_epoch_ms >= 0 AND ended_at_epoch_ms >= started_at_epoch_ms),
    CONSTRAINT ck_recruitment_transcript_text CHECK (btrim(transcript) <> '' AND length(transcript) <= 8000)
);

CREATE TABLE recruitment_interview_results (
    session_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    call_attempt_id UUID NOT NULL,
    terminal_event_id UUID NOT NULL UNIQUE,
    terminal_kind VARCHAR(12) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    completion_reason VARCHAR(30),
    failure_code VARCHAR(100),
    retryable BOOLEAN,
    failure_detail VARCHAR(1000),
    partial BOOLEAN NOT NULL,
    expected_turn_count INTEGER NOT NULL,
    persisted_turn_count INTEGER NOT NULL DEFAULT 0,
    connected_seconds INTEGER NOT NULL,
    score_policy_version VARCHAR(80) NOT NULL,
    overall_score NUMERIC(5,2),
    english_comprehension NUMERIC(8,4),
    english_fluency NUMERIC(8,4),
    english_vocabulary NUMERIC(8,4),
    english_grammar NUMERIC(8,4),
    english_pronunciation NUMERIC(8,4),
    english_band VARCHAR(30),
    advisory_only BOOLEAN NOT NULL DEFAULT TRUE,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_result_tenant_id UNIQUE (tenant_id,session_id),
    CONSTRAINT fk_recruitment_result_session FOREIGN KEY (tenant_id,session_id)
        REFERENCES recruitment_interviews(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_result_attempt FOREIGN KEY (tenant_id,call_attempt_id,session_id)
        REFERENCES recruitment_interview_call_attempts(tenant_id,id,session_id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_result_event FOREIGN KEY (terminal_event_id)
        REFERENCES recruitment_interview_event_inbox(event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_result_terminal CHECK (terminal_kind IN ('COMPLETED','FAILED')),
    CONSTRAINT ck_recruitment_result_delivery CHECK (delivery_status IN ('PENDING_TURNS','COMPLETE')),
    CONSTRAINT ck_recruitment_result_turns CHECK (expected_turn_count BETWEEN 0 AND 500 AND persisted_turn_count BETWEEN 0 AND 500),
    CONSTRAINT ck_recruitment_result_seconds CHECK (connected_seconds BETWEEN 0 AND 14400),
    CONSTRAINT ck_recruitment_result_score CHECK (overall_score IS NULL OR overall_score BETWEEN 0 AND 100),
    CONSTRAINT ck_recruitment_result_english CHECK (english_band IS NULL OR english_band IN ('BASIC','CONVERSATIONAL','WORKING_PROFICIENCY','PROFESSIONAL')),
    CONSTRAINT ck_recruitment_result_failure CHECK ((terminal_kind='COMPLETED' AND failure_code IS NULL AND retryable IS NULL) OR (terminal_kind='FAILED' AND btrim(failure_code) <> '' AND retryable IS NOT NULL))
);

CREATE TABLE recruitment_interview_section_results (
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL,
    section_id UUID NOT NULL,
    section_kind VARCHAR(20) NOT NULL,
    section_status VARCHAR(20) NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (tenant_id,session_id,section_id),
    CONSTRAINT fk_recruitment_section_result FOREIGN KEY (tenant_id,session_id)
        REFERENCES recruitment_interview_results(tenant_id,session_id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_section_kind CHECK (section_kind IN ('CORE','ENGLISH_SCREEN')),
    CONSTRAINT ck_recruitment_section_status CHECK (section_status IN ('COMPLETED','PARTIAL','SKIPPED')),
    CONSTRAINT ck_recruitment_section_position CHECK (position BETWEEN 1 AND 10)
);

CREATE TABLE recruitment_interview_question_results (
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL,
    section_id UUID NOT NULL,
    question_id UUID NOT NULL,
    section_kind VARCHAR(20) NOT NULL,
    question_status VARCHAR(20) NOT NULL,
    question_score NUMERIC(8,4),
    position INTEGER NOT NULL,
    PRIMARY KEY (tenant_id,session_id,question_id),
    CONSTRAINT uq_recruitment_question_result_ref UNIQUE (tenant_id,session_id,section_id,question_id),
    CONSTRAINT fk_recruitment_question_result FOREIGN KEY (tenant_id,session_id,section_id)
        REFERENCES recruitment_interview_section_results(tenant_id,session_id,section_id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_question_result_kind CHECK (section_kind IN ('CORE','ENGLISH_SCREEN')),
    CONSTRAINT ck_recruitment_question_result_status CHECK (question_status IN ('COMPLETED','PARTIAL','UNANSWERED','SKIPPED')),
    CONSTRAINT ck_recruitment_question_result_score CHECK (question_score IS NULL OR question_score BETWEEN 1 AND 5),
    CONSTRAINT ck_recruitment_question_result_position CHECK (position BETWEEN 1 AND 100)
);

CREATE TABLE recruitment_interview_score_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL,
    section_id UUID NOT NULL,
    question_id UUID NOT NULL,
    candidate_turn_id UUID NOT NULL,
    accepted BOOLEAN NOT NULL,
    rubric_score NUMERIC(8,4),
    english_comprehension NUMERIC(8,4),
    english_fluency NUMERIC(8,4),
    english_vocabulary NUMERIC(8,4),
    english_grammar NUMERIC(8,4),
    english_pronunciation NUMERIC(8,4),
    position INTEGER NOT NULL,
    CONSTRAINT uq_recruitment_score_evaluation UNIQUE (tenant_id,session_id,question_id,candidate_turn_id),
    CONSTRAINT fk_recruitment_evaluation_question FOREIGN KEY (tenant_id,session_id,section_id,question_id)
        REFERENCES recruitment_interview_question_results(tenant_id,session_id,section_id,question_id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_evaluation_turn FOREIGN KEY (tenant_id,candidate_turn_id)
        REFERENCES recruitment_interview_transcript_turns(tenant_id,turn_id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_evaluation_score CHECK ((accepted AND rubric_score BETWEEN 1 AND 5) OR (NOT accepted AND rubric_score IS NULL)),
    CONSTRAINT ck_recruitment_evaluation_position CHECK (position BETWEEN 1 AND 20)
);

CREATE TABLE recruitment_interview_provider_usage (
    usage_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL,
    call_attempt_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    provider VARCHAR(20) NOT NULL,
    capability VARCHAR(30) NOT NULL,
    quantity NUMERIC(20,6) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    provider_request_id VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_recruitment_usage_dimension UNIQUE (tenant_id,session_id,provider,capability),
    CONSTRAINT fk_recruitment_usage_session FOREIGN KEY (tenant_id,session_id)
        REFERENCES recruitment_interviews(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_usage_attempt FOREIGN KEY (tenant_id,call_attempt_id,session_id)
        REFERENCES recruitment_interview_call_attempts(tenant_id,id,session_id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_usage_event FOREIGN KEY (event_id)
        REFERENCES recruitment_interview_event_inbox(event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_usage_provider CHECK (provider IN ('TWILIO','CARTESIA','OPENAI','OLLAMA')),
    CONSTRAINT ck_recruitment_usage_capability CHECK (capability IN ('VOICE_CALL','MEDIA_STREAM','STT','TTS','LLM')),
    CONSTRAINT ck_recruitment_usage_quantity CHECK (quantity > 0 AND quantity <= 1000000000),
    CONSTRAINT ck_recruitment_usage_unit CHECK (unit IN ('CONNECTED_SECOND','AUDIO_SECOND','CHARACTER','TOKEN'))
);

CREATE TABLE recruitment_interview_recordings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL,
    call_attempt_id UUID NOT NULL,
    state VARCHAR(30) NOT NULL,
    provider VARCHAR(20) NOT NULL DEFAULT 'TWILIO',
    provider_account_sid VARCHAR(40),
    provider_recording_sid VARCHAR(40),
    callback_payload_sha256 VARCHAR(64),
    storage_key VARCHAR(512),
    content_type VARCHAR(80),
    size_bytes BIGINT,
    sha256 VARCHAR(64),
    storage_reservation_id UUID,
    recording_completed_at TIMESTAMPTZ,
    retained_until TIMESTAMPTZ NOT NULL,
    ready_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    provider_deleted_at TIMESTAMPTZ,
    failure_code VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_recording_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT uq_recruitment_recording_attempt UNIQUE (tenant_id,call_attempt_id),
    CONSTRAINT uq_recruitment_recording_provider UNIQUE (provider,provider_recording_sid),
    CONSTRAINT fk_recruitment_recording_session FOREIGN KEY (tenant_id,session_id)
        REFERENCES recruitment_interviews(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_recording_attempt FOREIGN KEY (tenant_id,call_attempt_id,session_id)
        REFERENCES recruitment_interview_call_attempts(tenant_id,id,session_id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_recording_state CHECK (state IN ('START_PENDING','RECORDING','COPY_PENDING','DELETE_PROVIDER_PENDING','READY','DELETE_PENDING','DELETED','FAILED')),
    CONSTRAINT ck_recruitment_recording_retention CHECK (retained_until >= created_at),
    CONSTRAINT ck_recruitment_recording_hash CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_recording_callback_hash CHECK (callback_payload_sha256 IS NULL OR callback_payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recruitment_recording_ready CHECK (state <> 'READY' OR (storage_key IS NOT NULL AND content_type='audio/mpeg' AND size_bytes > 0 AND sha256 IS NOT NULL AND ready_at IS NOT NULL))
);

CREATE TABLE recruitment_recording_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    recording_id UUID NOT NULL,
    operation_kind VARCHAR(30) NOT NULL,
    operation_key VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error_code VARCHAR(100),
    notification_attempts INTEGER NOT NULL DEFAULT 0,
    notification_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notification_published_at TIMESTAMPTZ,
    notification_last_error_code VARCHAR(100),
    locked_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recruitment_recording_operation UNIQUE (tenant_id,operation_key),
    CONSTRAINT fk_recruitment_recording_operation FOREIGN KEY (tenant_id,recording_id)
        REFERENCES recruitment_interview_recordings(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT ck_recruitment_recording_operation_kind CHECK (operation_kind IN ('START','COPY','DELETE_PROVIDER','DELETE_STORAGE','VERIFY_DELETION')),
    CONSTRAINT ck_recruitment_recording_operation_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','DEAD')),
    CONSTRAINT ck_recruitment_recording_operation_attempts CHECK (attempts BETWEEN 0 AND 10),
    CONSTRAINT ck_recruitment_recording_notification_attempts CHECK (notification_attempts >= 0)
);

CREATE INDEX idx_recruitment_event_session_type ON recruitment_interview_event_inbox(tenant_id,session_id,event_type);
CREATE INDEX idx_recruitment_transcript_order ON recruitment_interview_transcript_turns(tenant_id,session_id,sequence_number);
CREATE INDEX idx_recruitment_result_delivery ON recruitment_interview_results(delivery_status,updated_at);
CREATE INDEX idx_recruitment_recording_session_state ON recruitment_interview_recordings(tenant_id,session_id,state);
CREATE INDEX idx_recruitment_recording_retention ON recruitment_interview_recordings(retained_until,state);
CREATE INDEX idx_recruitment_recording_operations_due ON recruitment_recording_operations(status,next_attempt_at,id);
CREATE INDEX idx_recruitment_recording_notifications_due
    ON recruitment_recording_operations(notification_next_attempt_at,id)
    WHERE status='PENDING' AND notification_published_at IS NULL;
