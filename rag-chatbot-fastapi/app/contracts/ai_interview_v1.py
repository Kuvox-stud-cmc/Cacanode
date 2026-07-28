from __future__ import annotations

from datetime import datetime
from decimal import ROUND_HALF_UP, Decimal
from typing import Annotated, Literal
from uuid import UUID, uuid5

from pydantic import BaseModel, ConfigDict, Field, model_validator

INTERVIEW_EVENT_NAMESPACE = UUID("95f2198b-9bb1-5895-87ce-324f54c90d63")


def interview_event_id(event_type: str, aggregate_id: UUID, semantic_key: str) -> UUID:
    return uuid5(INTERVIEW_EVENT_NAMESPACE, f"{event_type}|{aggregate_id}|{semantic_key}")


def interview_runtime_event_id(
    event_type: str, session_id: UUID, call_attempt_id: UUID, semantic_key: str
) -> UUID:
    return uuid5(
        INTERVIEW_EVENT_NAMESPACE,
        f"{event_type}|{session_id}|{call_attempt_id}|{semantic_key}",
    )


def interview_turn_id(session_id: UUID, sequence: int) -> UUID:
    if sequence < 1:
        raise ValueError("turn sequence must be 1-based")
    return uuid5(INTERVIEW_EVENT_NAMESPACE, f"interview.turn|{session_id}|{sequence}|v1.1")


def interview_runtime_turn_id(session_id: UUID, call_attempt_id: UUID, sequence: int) -> UUID:
    if sequence < 1:
        raise ValueError("turn sequence must be 1-based")
    return uuid5(
        INTERVIEW_EVENT_NAMESPACE,
        f"interview.turn|{session_id}|{call_attempt_id}|{sequence}|v1.2",
    )


def resume_analysis_id(
    tenant_id: UUID,
    application_id: UUID,
    cv_sha256: str,
    analysis_mode: str,
    policy_version: str,
    model_version: str,
) -> UUID:
    return uuid5(
        INTERVIEW_EVENT_NAMESPACE,
        "|".join(
            (
                "cv-analysis",
                str(tenant_id),
                str(application_id),
                cv_sha256,
                analysis_mode,
                policy_version,
                model_version,
            )
        ),
    )


def current_resume_analysis_id(
    tenant_id: UUID,
    application_id: UUID,
    document_id: UUID,
    cv_sha256: str,
    analysis_mode: str,
    policy_version: str,
    model_version: str,
) -> UUID:
    return uuid5(
        INTERVIEW_EVENT_NAMESPACE,
        "|".join(
            (
                "cv-analysis-v2",
                str(tenant_id),
                str(application_id),
                str(document_id),
                cv_sha256,
                analysis_mode,
                policy_version,
                model_version,
            )
        ),
    )


def resume_analysis_id_v12(
    tenant_id: UUID,
    application_id: UUID,
    document_id: UUID,
    cv_sha256: str,
    analysis_mode: str,
    policy_version: str,
    model_version: str,
    analysis_revision: int,
) -> UUID:
    return uuid5(
        INTERVIEW_EVENT_NAMESPACE,
        "|".join(
            (
                "cv-analysis-v1.2",
                str(tenant_id),
                str(application_id),
                str(document_id),
                cv_sha256,
                analysis_mode,
                policy_version,
                model_version,
                str(analysis_revision),
            )
        ),
    )


class InterviewEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Literal["1.0"]
    event_id: UUID
    event_type: str
    occurred_at: datetime
    tenant_id: UUID
    aggregate_id: UUID


class ResumeAnalysisRequestV10(InterviewEvent):
    event_type: Literal["interview.resume-analysis.requested"]
    analysis_id: UUID
    application_id: UUID
    document_id: UUID
    storage_key: str = Field(min_length=1, max_length=512)
    file_name: str = Field(min_length=1, max_length=255)
    content_type: Literal[
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ]
    file_size_bytes: int = Field(ge=1, le=20 * 1024 * 1024)
    requested_language_tag: str = Field(min_length=2, max_length=35)


class ResumeEvidenceV10(BaseModel):
    model_config = ConfigDict(extra="forbid")
    excerpt: str = Field(min_length=1, max_length=500)
    source_location: str = Field(min_length=1, max_length=120)


class ResumeAnalysisOutcomeV10(InterviewEvent):
    event_type: Literal["interview.resume-analysis.outcome"]
    analysis_id: UUID
    application_id: UUID
    status: Literal["COMPLETED", "FAILED"]
    summary: str = Field(max_length=4000)
    skills: list[str] = Field(max_length=50)
    evidence: list[ResumeEvidenceV10] = Field(max_length=20)
    error_code: str | None = Field(default=None, max_length=100)


class TemplateQuestion(BaseModel):
    model_config = ConfigDict(extra="forbid")
    question_id: UUID
    section_id: UUID
    prompt: str = Field(min_length=1, max_length=1000)
    competency: str = Field(min_length=1, max_length=200)


class ResumeAnalysisRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    schema_version: Literal["1.1", "1.2"]
    event_id: UUID
    event_type: Literal["interview.resume-analysis.requested"]
    occurred_at: datetime
    tenant_id: UUID
    aggregate_id: UUID
    analysis_id: UUID
    application_id: UUID
    document_id: UUID
    storage_key: str = Field(min_length=1, max_length=512)
    file_name: str = Field(min_length=1, max_length=255)
    content_type: Literal[
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ]
    file_size_bytes: int = Field(ge=1, le=5 * 1024 * 1024)
    requested_language_tag: Literal["vi-VN", "en-US"]
    cv_sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    analysis_mode: Literal["SUMMARY_ONLY", "PERSONALIZED_QUESTIONS"]
    policy_version: str = Field(min_length=1, max_length=80)
    model_version: str = Field(min_length=1, max_length=120)
    job_title: str = Field(min_length=1, max_length=200)
    job_description: str = Field(max_length=4000)
    allowed_core_section_ids: list[UUID] = Field(min_length=1, max_length=10)
    template_questions: list[TemplateQuestion] = Field(max_length=100)
    personalized_question_limit: Literal[0, 2]

    @model_validator(mode="after")
    def validate_identity_and_mode(self) -> ResumeAnalysisRequest:
        if self.aggregate_id != self.analysis_id:
            raise ValueError("aggregate_id must equal analysis_id")
        expected_limit = 0 if self.analysis_mode == "SUMMARY_ONLY" else 2
        if self.personalized_question_limit != expected_limit:
            raise ValueError("personalized question limit does not match analysis mode")
        if len(set(self.allowed_core_section_ids)) != len(self.allowed_core_section_ids):
            raise ValueError("allowed CORE section IDs must be unique")
        allowed = set(self.allowed_core_section_ids)
        if any(question.section_id not in allowed for question in self.template_questions):
            raise ValueError("template question targets an unknown CORE section")
        return self


class JobContextAnchor(BaseModel):
    model_config = ConfigDict(extra="forbid")
    anchor_id: str = Field(min_length=1, max_length=160)
    field: str = Field(min_length=1, max_length=80)
    excerpt: str = Field(min_length=1, max_length=12_000)


class ResumeAnalysisRequestV12(ResumeAnalysisRequest):
    schema_version: Literal["1.2"]
    analysis_revision: int = Field(ge=1)
    job_context_anchors: list[JobContextAnchor] = Field(min_length=1, max_length=250)
    job_context_truncated: bool

    @model_validator(mode="after")
    def validate_v12_identity(self) -> ResumeAnalysisRequestV12:
        if len({item.anchor_id for item in self.job_context_anchors}) != len(
            self.job_context_anchors
        ):
            raise ValueError("job-context anchor IDs must be unique")
        return self


class ResumeEvidence(BaseModel):
    model_config = ConfigDict(extra="forbid")
    anchor_id: str = Field(min_length=1, max_length=80)
    excerpt: str = Field(min_length=1, max_length=500)
    source_location: str = Field(min_length=1, max_length=120)


class ResumeSkill(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=120)
    evidence_anchor_ids: list[str] = Field(min_length=1, max_length=20)


class PersonalizedQuestion(BaseModel):
    model_config = ConfigDict(extra="forbid")
    question_id: UUID
    target_section_id: UUID
    prompt: str = Field(min_length=1, max_length=1000)
    competency: str = Field(min_length=1, max_length=200)
    rubric: str = Field(min_length=1, max_length=2000)
    evidence_anchor_ids: list[str] = Field(min_length=1, max_length=20)


class ResumeAnalysisOutcome(BaseModel):
    model_config = ConfigDict(extra="forbid")
    schema_version: Literal["1.1", "1.2"]
    event_id: UUID
    event_type: Literal["interview.resume-analysis.outcome"]
    occurred_at: datetime
    tenant_id: UUID
    aggregate_id: UUID
    analysis_id: UUID
    application_id: UUID
    cv_sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    analysis_mode: Literal["SUMMARY_ONLY", "PERSONALIZED_QUESTIONS"]
    policy_version: str = Field(min_length=1, max_length=80)
    model_version: str = Field(min_length=1, max_length=120)
    status: Literal["COMPLETED", "FAILED"]
    summary: str | None = Field(default=None, max_length=4000)
    evidence: list[ResumeEvidence] = Field(max_length=250)
    skills: list[ResumeSkill] = Field(max_length=100)
    personalized_questions: list[PersonalizedQuestion] = Field(max_length=2)
    error_code: str | None = Field(default=None, max_length=100)

    @model_validator(mode="after")
    def validate_outcome(self) -> ResumeAnalysisOutcome:
        if self.aggregate_id != self.analysis_id:
            raise ValueError("aggregate_id must equal analysis_id")
        if self.status == "FAILED":
            if (
                not self.error_code
                or self.summary is not None
                or any((self.evidence, self.skills, self.personalized_questions))
            ):
                raise ValueError("failed outcome contains completed content")
        elif not self.summary or self.error_code is not None:
            raise ValueError("completed outcome is missing its summary")
        if self.analysis_mode == "SUMMARY_ONLY" and self.personalized_questions:
            raise ValueError("summary-only outcome contains questions")
        return self


class FitFinding(BaseModel):
    model_config = ConfigDict(extra="forbid")
    weight_percent: int = Field(ge=1, le=100)
    match_percent: int = Field(ge=0, le=100)
    evidence_status: Literal["EVIDENCED", "NOT_EVIDENCED"]
    explanation: str = Field(min_length=1, max_length=1000)
    job_excerpt: str = Field(min_length=1, max_length=2000)
    job_anchor_id: str = Field(min_length=1, max_length=160)
    cv_evidence_anchor_ids: list[str] = Field(max_length=20)


class ResumeAnalysisOutcomeV12(ResumeAnalysisOutcome):
    schema_version: Literal["1.2"]
    analysis_revision: int = Field(ge=1)
    fit_score_percent: int | None = Field(default=None, ge=0, le=100)
    fit_confidence: Literal["LOW", "MEDIUM", "HIGH"] | None = None
    fit_explanation: str | None = Field(default=None, max_length=1000)
    strengths: list[FitFinding] = Field(max_length=50)
    gaps: list[FitFinding] = Field(max_length=50)

    @model_validator(mode="after")
    def validate_fit_outcome(self) -> ResumeAnalysisOutcomeV12:
        if self.status == "FAILED":
            if any(
                (
                    self.fit_score_percent is not None,
                    self.fit_confidence is not None,
                    self.fit_explanation is not None,
                    bool(self.strengths),
                    bool(self.gaps),
                )
            ):
                raise ValueError("failed outcome contains fit content")
            return self
        if (
            self.fit_score_percent is None
            or self.fit_confidence is None
            or not self.fit_explanation
        ):
            raise ValueError("completed outcome is missing fit content")
        findings = self.strengths + self.gaps
        if sum(item.weight_percent for item in findings) != 100:
            raise ValueError("fit weights must total 100")
        weighted = sum(item.weight_percent * item.match_percent for item in findings)
        if self.fit_score_percent != (weighted + 50) // 100:
            raise ValueError("fit score does not match weighted findings")
        for item in self.strengths:
            if (
                item.evidence_status != "EVIDENCED"
                or not item.cv_evidence_anchor_ids
                or not 70 <= item.match_percent <= 100
            ):
                raise ValueError("invalid strength")
        for item in self.gaps:
            if item.evidence_status == "NOT_EVIDENCED":
                if item.match_percent != 0 or item.cv_evidence_anchor_ids:
                    raise ValueError("invalid not-evidenced gap")
            elif not item.cv_evidence_anchor_ids or not 1 <= item.match_percent <= 69:
                raise ValueError("invalid partial gap")
        return self


class FinalizedTurnV10(InterviewEvent):
    event_type: Literal["interview.turn.finalized"]
    session_id: UUID
    call_attempt_id: UUID
    turn_id: UUID
    sequence: int = Field(ge=0)
    speaker: Literal["CANDIDATE", "INTERVIEWER", "SYSTEM"]
    language_tag: str = Field(min_length=2, max_length=35)
    started_at_epoch_ms: int = Field(ge=0)
    ended_at_epoch_ms: int = Field(ge=0)
    transcript: str = Field(min_length=1, max_length=8000)
    interrupted: bool


class SectionResult(BaseModel):
    model_config = ConfigDict(extra="forbid")
    section_id: UUID
    kind: Literal["CORE", "ENGLISH_SCREEN"]
    status: Literal["COMPLETED", "PARTIAL", "SKIPPED"]


class InterviewCompletedV10(InterviewEvent):
    event_type: Literal["interview.session.completed"]
    session_id: UUID
    call_attempt_id: UUID
    completion_reason: Literal["FINISHED", "CANDIDATE_STOPPED", "TIME_LIMIT", "PARTIAL"]
    connected_seconds: int = Field(ge=0, le=14400)
    turn_count: int = Field(ge=0, le=500)
    section_results: list[SectionResult] = Field(max_length=10)


class InterviewFailedV10(InterviewEvent):
    event_type: Literal["interview.session.failed"]
    session_id: UUID
    call_attempt_id: UUID
    failure_code: str = Field(min_length=1, max_length=100)
    retryable: bool
    connected_seconds: int = Field(ge=0, le=14400)
    last_turn_sequence: int | None = Field(default=None, ge=0)
    detail: str = Field(max_length=1000)


class ProviderUsageV10(InterviewEvent):
    event_type: Literal["interview.provider.usage"]
    usage_id: UUID
    session_id: UUID
    call_attempt_id: UUID
    provider: Literal["TWILIO", "CARTESIA", "OPENAI"]
    capability: Literal["VOICE_CALL", "MEDIA_STREAM", "STT", "TTS", "LLM"]
    quantity: Decimal = Field(gt=0, le=1_000_000_000)
    unit: Literal["CONNECTED_SECOND", "AUDIO_SECOND", "CHARACTER", "TOKEN"]
    provider_request_id: str | None = Field(default=None, max_length=255)


class InterviewEventV11(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Literal["1.1", "1.2"]
    event_id: UUID
    event_type: str
    occurred_at: datetime
    tenant_id: UUID
    aggregate_id: UUID


class FinalizedTurn(InterviewEventV11):
    event_type: Literal["interview.turn.finalized"]
    session_id: UUID
    call_attempt_id: UUID
    turn_id: UUID
    sequence: int = Field(ge=1, le=500)
    speaker: Literal["CANDIDATE", "INTERVIEWER", "SYSTEM"]
    turn_kind: Literal[
        "INTRODUCTION",
        "TRANSITION",
        "QUESTION",
        "ACKNOWLEDGEMENT",
        "FOLLOW_UP",
        "CLARIFICATION",
        "REPETITION",
        "SILENCE_PROMPT",
        "CANDIDATE_UTTERANCE",
        "CLOSING",
    ]
    section_id: UUID | None
    question_id: UUID | None
    language_tag: Literal["vi-VN", "en-US"]
    started_at_epoch_ms: int = Field(ge=0)
    ended_at_epoch_ms: int = Field(ge=0)
    transcript: str = Field(min_length=1, max_length=8000)
    interrupted: bool

    @model_validator(mode="after")
    def validate_turn(self) -> FinalizedTurn:
        if (
            self.aggregate_id != self.session_id
            or self.ended_at_epoch_ms < self.started_at_epoch_ms
        ):
            raise ValueError("invalid finalized-turn binding or timestamps")
        if self.schema_version == "1.2":
            expected_turn = interview_runtime_turn_id(
                self.session_id, self.call_attempt_id, self.sequence
            )
            expected_event = interview_runtime_event_id(
                self.event_type,
                self.session_id,
                self.call_attempt_id,
                f"turn:{self.sequence}:v1.2",
            )
        else:
            expected_turn = interview_turn_id(self.session_id, self.sequence)
            expected_event = interview_event_id(
                self.event_type, self.session_id, f"turn:{self.sequence}:v1.1"
            )
        if self.turn_id != expected_turn or self.event_id != expected_event:
            raise ValueError("invalid finalized-turn identity")
        if self.turn_kind == "CANDIDATE_UTTERANCE" and self.speaker != "CANDIDATE":
            raise ValueError("candidate utterance must use the candidate speaker")
        if self.turn_kind in {"QUESTION", "FOLLOW_UP", "REPETITION", "CLARIFICATION"}:
            if self.section_id is None or self.question_id is None:
                raise ValueError("question-scoped turn is missing context")
        return self


ScoreValue = Annotated[Decimal, Field(ge=1, le=5)]


class EnglishDimensions(BaseModel):
    model_config = ConfigDict(extra="forbid")
    comprehension: ScoreValue
    fluency: ScoreValue
    vocabulary: ScoreValue
    grammar: ScoreValue
    pronunciation: ScoreValue


class ScoreEvaluation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    candidate_turn_id: UUID
    accepted: bool
    rubric_score: ScoreValue | None
    english_dimensions: EnglishDimensions | None

    @model_validator(mode="after")
    def validate_score(self) -> ScoreEvaluation:
        if self.accepted != (self.rubric_score is not None):
            raise ValueError("accepted evaluation must contain a rubric score")
        if not self.accepted and self.english_dimensions is not None:
            raise ValueError("rejected evaluation cannot contain English dimensions")
        return self


class QuestionResult(BaseModel):
    model_config = ConfigDict(extra="forbid")
    section_id: UUID
    question_id: UUID
    section_kind: Literal["CORE", "ENGLISH_SCREEN"]
    status: Literal["COMPLETED", "PARTIAL", "UNANSWERED", "SKIPPED"]
    score: Decimal | None = Field(default=None, ge=1, le=5)
    evaluations: list[ScoreEvaluation] = Field(max_length=20)

    @model_validator(mode="after")
    def validate_result(self) -> QuestionResult:
        accepted = [
            item.rubric_score
            for item in self.evaluations
            if item.accepted and item.rubric_score is not None
        ]
        expected = None if not accepted else sum(accepted, Decimal(0)) / Decimal(len(accepted))
        if expected is None:
            if self.score is not None:
                raise ValueError("unscored question contains a score")
        elif self.score is None or self.score != expected:
            raise ValueError("question score does not equal the accepted-evaluation mean")
        if self.section_kind == "CORE" and any(
            item.english_dimensions is not None for item in self.evaluations
        ):
            raise ValueError("CORE evaluation contains English dimensions")
        return self


class TerminalResultV11(InterviewEventV11):
    session_id: UUID
    call_attempt_id: UUID
    expected_turn_count: int = Field(ge=0, le=500)
    connected_seconds: int = Field(ge=0, le=14400)
    partial: bool
    score_policy_version: Literal["equal-core-questions-v1"]
    overall_score: Decimal | None = Field(default=None, ge=0, le=100, decimal_places=2)
    english_dimensions: EnglishDimensions | None
    english_band: Literal["BASIC", "CONVERSATIONAL", "WORKING_PROFICIENCY", "PROFESSIONAL"] | None
    section_results: list[SectionResult] = Field(max_length=10)
    question_results: list[QuestionResult] = Field(max_length=100)

    @model_validator(mode="after")
    def validate_terminal_result(self) -> TerminalResultV11:
        if self.aggregate_id != self.session_id:
            raise ValueError("aggregate_id must equal session_id")
        semantic_key = (
            "failed" if self.event_type == "interview.session.failed" else "completed"
        ) + (":v1.2" if self.schema_version == "1.2" else ":v1.1")
        expected_event = (
            interview_runtime_event_id(
                self.event_type, self.session_id, self.call_attempt_id, semantic_key
            )
            if self.schema_version == "1.2"
            else interview_event_id(self.event_type, self.session_id, semantic_key)
        )
        if self.event_id != expected_event:
            raise ValueError("invalid terminal-result identity")
        core_scores = [
            item.score
            for item in self.question_results
            if item.section_kind == "CORE" and item.score is not None
        ]
        expected_overall = None
        if core_scores:
            mean = sum(core_scores, Decimal(0)) / Decimal(len(core_scores))
            expected_overall = (mean * Decimal(20)).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            )
        if self.overall_score != expected_overall:
            raise ValueError("overall score does not match equal CORE-question scoring")
        english_evaluations = [
            evaluation.english_dimensions
            for item in self.question_results
            if item.section_kind == "ENGLISH_SCREEN"
            for evaluation in item.evaluations
            if evaluation.accepted and evaluation.english_dimensions is not None
        ]
        if not english_evaluations:
            if self.english_dimensions is not None or self.english_band is not None:
                raise ValueError("English result exists without an evaluated English answer")
        else:
            if self.english_dimensions is None or self.english_band is None:
                raise ValueError("English result is incomplete")
            means = {
                field: sum((getattr(item, field) for item in english_evaluations), Decimal(0))
                / Decimal(len(english_evaluations))
                for field in (
                    "comprehension",
                    "fluency",
                    "vocabulary",
                    "grammar",
                    "pronunciation",
                )
            }
            if any(
                getattr(self.english_dimensions, field) != value for field, value in means.items()
            ):
                raise ValueError("English dimensions do not equal their evaluation means")
            mean = sum(means.values(), Decimal(0)) / Decimal(5)
            expected_band = (
                "BASIC"
                if mean < 2
                else "CONVERSATIONAL"
                if mean < 3
                else "WORKING_PROFICIENCY"
                if mean < 4
                else "PROFESSIONAL"
            )
            if self.english_band != expected_band:
                raise ValueError("English band does not match the unrounded dimension means")
        return self


class InterviewCompleted(TerminalResultV11):
    event_type: Literal["interview.session.completed"]
    completion_reason: Literal["FINISHED", "CANDIDATE_STOPPED", "TIME_LIMIT", "PARTIAL"]


class InterviewFailed(TerminalResultV11):
    event_type: Literal["interview.session.failed"]
    failure_code: str = Field(min_length=1, max_length=100)
    retryable: bool
    detail: str = Field(max_length=1000)


class ProviderUsage(InterviewEventV11):
    event_type: Literal["interview.provider.usage"]
    usage_id: UUID
    session_id: UUID
    call_attempt_id: UUID
    provider: Literal["TWILIO", "CARTESIA", "OPENAI", "OLLAMA"]
    capability: Literal["VOICE_CALL", "MEDIA_STREAM", "STT", "TTS", "LLM"]
    quantity: Decimal = Field(gt=0, le=1_000_000_000)
    unit: Literal["CONNECTED_SECOND", "AUDIO_SECOND", "CHARACTER", "TOKEN"]
    provider_request_id: str | None = Field(default=None, min_length=1, max_length=255)

    @model_validator(mode="after")
    def validate_usage(self) -> ProviderUsage:
        if self.aggregate_id != self.session_id or self.usage_id != self.event_id:
            raise ValueError("invalid provider-usage binding")
        semantic_key = (
            f"{self.provider.lower()}:{self.capability.lower()}:"
            f"{'v1.2' if self.schema_version == '1.2' else 'v1.1'}"
        )
        expected_event = (
            interview_runtime_event_id(
                self.event_type, self.session_id, self.call_attempt_id, semantic_key
            )
            if self.schema_version == "1.2"
            else interview_event_id(self.event_type, self.session_id, semantic_key)
        )
        if self.event_id != expected_event:
            raise ValueError("invalid provider-usage identity")
        return self


class RecordingReady(InterviewEvent):
    event_type: Literal["recruitment.recording.ready"]
    session_id: UUID
    call_attempt_id: UUID
    storage_key: str = Field(min_length=1, max_length=512)
    content_type: Literal["audio/mpeg", "audio/wav"]
    size_bytes: int = Field(ge=1, le=1024 * 1024 * 1024)
    sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    retained_until: datetime


EVENT_MODELS: dict[str, type[InterviewEvent]] = {
    "recruitment.recording.ready": RecordingReady,
}


def parse_interview_event(payload: bytes) -> BaseModel:
    import json

    raw = json.loads(payload)
    event_type = raw.get("event_type")
    model: type[BaseModel] | None
    if event_type == "interview.resume-analysis.requested":
        if raw.get("schema_version") == "1.2":
            model = ResumeAnalysisRequestV12
        elif raw.get("schema_version") == "1.1":
            model = ResumeAnalysisRequest
        else:
            model = ResumeAnalysisRequestV10
    elif event_type == "interview.resume-analysis.outcome":
        if raw.get("schema_version") == "1.2":
            model = ResumeAnalysisOutcomeV12
        elif raw.get("schema_version") == "1.1":
            model = ResumeAnalysisOutcome
        else:
            model = ResumeAnalysisOutcomeV10
    elif event_type == "interview.turn.finalized":
        model = FinalizedTurn if raw.get("schema_version") in {"1.1", "1.2"} else FinalizedTurnV10
    elif event_type == "interview.session.completed":
        model = (
            InterviewCompleted
            if raw.get("schema_version") in {"1.1", "1.2"}
            else InterviewCompletedV10
        )
    elif event_type == "interview.session.failed":
        model = (
            InterviewFailed if raw.get("schema_version") in {"1.1", "1.2"} else InterviewFailedV10
        )
    elif event_type == "interview.provider.usage":
        model = ProviderUsage if raw.get("schema_version") in {"1.1", "1.2"} else ProviderUsageV10
    else:
        model = EVENT_MODELS.get(event_type)
    if model is None:
        raise ValueError(f"Unsupported interview event type: {event_type}")
    return model.model_validate(raw)
