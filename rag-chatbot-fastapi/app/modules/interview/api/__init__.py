from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from typing import Protocol


class InterviewRuntimeError(Exception):
    """Base error owned by the interview capability."""


class InterviewDisabledError(InterviewRuntimeError):
    pass


class InterviewRuntimeNotReadyError(InterviewRuntimeError):
    pass


class InterviewRuntimeConflictError(InterviewRuntimeError):
    pass


class InterviewRuntimeValidationError(InterviewRuntimeError):
    pass


class InterviewSectionKind(StrEnum):
    CORE = "CORE"
    ENGLISH_SCREEN = "ENGLISH_SCREEN"


class InterviewQuestionSource(StrEnum):
    TEMPLATE = "TEMPLATE"
    CV_PERSONALIZED = "CV_PERSONALIZED"


@dataclass(frozen=True, slots=True)
class InterviewQuestionSnapshot:
    question_id: str
    position: int
    prompt: str
    competency: str
    rubric: str
    follow_up_limit: int
    source: InterviewQuestionSource
    evidence: str | None = None


@dataclass(frozen=True, slots=True)
class InterviewSectionSnapshot:
    section_id: str
    position: int
    kind: InterviewSectionKind
    language_tag: str
    duration_limit_seconds: int
    transition_text: str
    questions: tuple[InterviewQuestionSnapshot, ...]


@dataclass(frozen=True, slots=True)
class InteractionLimits:
    repetition_limit: int
    clarification_limit: int
    silence_timeout_seconds: int
    silence_prompt_limit: int


@dataclass(frozen=True, slots=True)
class PrepareInterviewCommand:
    session_id: str
    call_attempt_id: str
    tenant_id: str
    template_revision_id: str
    snapshot_version: str
    snapshot_sha256: str
    company_display_name: str
    candidate_display_name: str
    introduction_text: str
    disclosure_text: str
    closing_text: str
    duration_limit_seconds: int
    interaction_limits: InteractionLimits
    recording_enabled: bool
    cv_personalization_enabled: bool
    sections: tuple[InterviewSectionSnapshot, ...]


@dataclass(frozen=True, slots=True)
class PreparedInterview:
    session_id: str
    call_attempt_id: str
    runtime_token: str
    expires_at_epoch_seconds: int
    accepted_snapshot_sha256: str


@dataclass(frozen=True, slots=True)
class CancelInterviewCommand:
    session_id: str
    call_attempt_id: str
    reason: str


@dataclass(frozen=True, slots=True)
class CancelledInterview:
    session_id: str
    call_attempt_id: str
    cancelled: bool
    already_terminal: bool


class InterviewRuntimeApi(Protocol):
    async def prepare(self, command: PrepareInterviewCommand) -> PreparedInterview: ...

    async def cancel(self, command: CancelInterviewCommand) -> CancelledInterview: ...


__all__ = [
    "CancelInterviewCommand",
    "CancelledInterview",
    "InteractionLimits",
    "InterviewDisabledError",
    "InterviewQuestionSnapshot",
    "InterviewQuestionSource",
    "InterviewRuntimeApi",
    "InterviewRuntimeError",
    "InterviewRuntimeNotReadyError",
    "InterviewRuntimeConflictError",
    "InterviewRuntimeValidationError",
    "InterviewSectionKind",
    "InterviewSectionSnapshot",
    "PrepareInterviewCommand",
    "PreparedInterview",
]
