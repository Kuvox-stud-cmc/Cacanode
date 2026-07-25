package com.cacanode.api.recruitment.config;

public final class RecruitmentRabbitTopology {
    public static final String INTERVIEW_EXCHANGE = "cacanode.interview.v1";
    public static final String DEAD_LETTER_EXCHANGE = "cacanode.interview.dlx.v1";
    public static final String RECORDING_OPERATION_EXCHANGE = "cacanode.recruitment.recording-operations.v1";
    public static final String RECORDING_OPERATION_DEAD_LETTER_EXCHANGE =
            "cacanode.recruitment.recording-operations.dlx.v1";
    public static final String RESUME_ANALYSIS_QUEUE = "cacanode.interview.resume-analysis.v1";
    public static final String INTERVIEW_EVENTS_QUEUE = "cacanode.recruitment.interview-events.v1";
    public static final String RECORDING_OPERATION_QUEUE = "cacanode.recruitment.recording-operations.v1";
    public static final String RESUME_ANALYSIS_DLQ = "cacanode.interview.resume-analysis.dlq.v1";
    public static final String INTERVIEW_EVENTS_DLQ = "cacanode.recruitment.interview-events.dlq.v1";
    public static final String RECORDING_OPERATION_DLQ = "cacanode.recruitment.recording-operations.dlq.v1";

    public static final String RESUME_ANALYSIS_REQUESTED = "interview.resume-analysis.requested";
    public static final String RESUME_ANALYSIS_OUTCOME = "interview.resume-analysis.outcome";
    public static final String TURN_FINALIZED = "interview.turn.finalized";
    public static final String SESSION_COMPLETED = "interview.session.completed";
    public static final String SESSION_FAILED = "interview.session.failed";
    public static final String PROVIDER_USAGE = "interview.provider.usage";
    public static final String RECORDING_OPERATION_REQUESTED = "recruitment.recording.operation.requested";

    public static final int MAX_TRANSIENT_RETRIES = 3;
    public static final long CONFIRM_TIMEOUT_MILLIS = 5_000;

    private RecruitmentRabbitTopology() {
    }
}
