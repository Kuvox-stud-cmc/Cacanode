from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
import unicodedata
from collections.abc import Callable
from typing import Any, NoReturn
from uuid import UUID

from app.modules.interview.api import (
    CancelInterviewCommand,
    CancelledInterview,
    InterviewDisabledError,
    InterviewQuestionSource,
    InterviewRuntimeConflictError,
    InterviewRuntimeNotReadyError,
    InterviewRuntimeValidationError,
    InterviewSectionKind,
    PreparedInterview,
    PrepareInterviewCommand,
)
from app.modules.interview.internal.redis_state import InterviewRedisState

SNAPSHOT_VERSION = "interview-session-v1"


def canonical_interview_payload(command: PrepareInterviewCommand) -> dict[str, Any]:
    return _nfc(
        {
            "snapshotVersion": command.snapshot_version,
            "sessionId": command.session_id,
            "callAttemptId": command.call_attempt_id,
            "tenantId": command.tenant_id,
            "templateRevisionId": command.template_revision_id,
            "companyDisplayName": command.company_display_name,
            "candidateDisplayName": command.candidate_display_name,
            "introductionText": command.introduction_text,
            "disclosureText": command.disclosure_text,
            "closingText": command.closing_text,
            "durationLimitSeconds": command.duration_limit_seconds,
            "interactionLimits": {
                "repetitionLimit": command.interaction_limits.repetition_limit,
                "clarificationLimit": command.interaction_limits.clarification_limit,
                "silenceTimeoutSeconds": command.interaction_limits.silence_timeout_seconds,
                "silencePromptLimit": command.interaction_limits.silence_prompt_limit,
            },
            "recordingEnabled": command.recording_enabled,
            "cvPersonalizationEnabled": command.cv_personalization_enabled,
            "sections": [
                {
                    "sectionId": section.section_id,
                    "position": section.position,
                    "kind": section.kind.value,
                    "languageTag": section.language_tag,
                    "durationLimitSeconds": section.duration_limit_seconds,
                    "transitionText": section.transition_text,
                    "questions": [
                        {
                            **{
                                "questionId": question.question_id,
                                "position": question.position,
                                "prompt": question.prompt,
                                "competency": question.competency,
                                "rubric": question.rubric,
                                "followUpLimit": question.follow_up_limit,
                                "source": question.source.value,
                            },
                            **({"evidence": question.evidence} if question.evidence else {}),
                        }
                        for question in sorted(section.questions, key=lambda value: value.position)
                    ],
                }
                for section in sorted(command.sections, key=lambda value: value.position)
            ],
        }
    )


def canonical_interview_json(payload: dict[str, Any]) -> bytes:
    return json.dumps(
        _nfc(payload), ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")


def interview_payload_sha256(payload: dict[str, Any]) -> str:
    return hashlib.sha256(canonical_interview_json(payload)).hexdigest()


class ConfiguredInterviewRuntime:
    def __init__(
        self,
        *,
        enabled: bool,
        state: InterviewRedisState | None = None,
        token_secret: str = "",
        token_ttl_seconds: int = 900,
        now: Callable[[], float] = time.time,
    ) -> None:
        self._enabled = enabled
        self._state = state
        self._token_secret = token_secret.encode("utf-8")
        self._token_ttl_seconds = token_ttl_seconds
        self._now = now

    async def prepare(self, command: PrepareInterviewCommand) -> PreparedInterview:
        self._require_available()
        self._validate(command)
        payload = canonical_interview_payload(command)
        digest = interview_payload_sha256(payload)
        if not hmac.compare_digest(digest, command.snapshot_sha256):
            raise InterviewRuntimeValidationError("INTERVIEW_SNAPSHOT_HASH_MISMATCH")
        token = self._token(command.call_attempt_id, digest)
        token_hash = hashlib.sha256(token.encode("utf-8")).hexdigest()
        expires_at = int(self._now()) + self._token_ttl_seconds
        assert self._state is not None
        try:
            result = await self._state.prepare_runtime_session(
                session_id=command.session_id,
                call_attempt_id=command.call_attempt_id,
                tenant_id=command.tenant_id,
                payload=payload,
                payload_hash=digest,
                token_sha256=token_hash,
                expires_at_epoch_seconds=expires_at,
            )
        except ValueError as exception:
            raise InterviewRuntimeConflictError("INTERVIEW_PREPARATION_CONFLICT") from exception
        return PreparedInterview(
            session_id=command.session_id,
            call_attempt_id=command.call_attempt_id,
            runtime_token=token,
            expires_at_epoch_seconds=result.expires_at_epoch_seconds,
            accepted_snapshot_sha256=digest,
        )

    async def cancel(self, command: CancelInterviewCommand) -> CancelledInterview:
        self._require_available()
        _uuid(command.session_id, "session_id")
        _uuid(command.call_attempt_id, "call_attempt_id")
        assert self._state is not None
        try:
            cancelled = await self._state.cancel_runtime_session(
                command.session_id, command.call_attempt_id
            )
        except ValueError as exception:
            raise InterviewRuntimeConflictError("INTERVIEW_CANCELLATION_CONFLICT") from exception
        return CancelledInterview(
            session_id=command.session_id,
            call_attempt_id=command.call_attempt_id,
            cancelled=cancelled,
            already_terminal=not cancelled,
        )

    def _token(self, call_attempt_id: str, digest: str) -> str:
        value = hmac.new(
            self._token_secret,
            f"interview-runtime-v1:{call_attempt_id}:{digest}".encode(),
            hashlib.sha256,
        ).digest()
        return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")

    def _validate(self, command: PrepareInterviewCommand) -> None:
        for field, value in (
            ("session_id", command.session_id),
            ("call_attempt_id", command.call_attempt_id),
            ("tenant_id", command.tenant_id),
            ("template_revision_id", command.template_revision_id),
        ):
            _uuid(value, field)
        if command.snapshot_version != SNAPSHOT_VERSION:
            raise InterviewRuntimeValidationError("INTERVIEW_SNAPSHOT_VERSION_UNSUPPORTED")
        if command.duration_limit_seconds <= 0 or not command.sections:
            raise InterviewRuntimeValidationError("INTERVIEW_SNAPSHOT_INVALID")
        positions = [section.position for section in command.sections]
        if sorted(positions) != list(range(1, len(positions) + 1)):
            raise InterviewRuntimeValidationError("INTERVIEW_SECTION_ORDER_INVALID")
        for section in command.sections:
            _uuid(section.section_id, "section_id")
            if (
                section.language_tag not in {"en-US", "vi-VN"}
                or section.duration_limit_seconds <= 0
            ):
                raise InterviewRuntimeValidationError("INTERVIEW_SECTION_INVALID")
            if section.kind not in {InterviewSectionKind.CORE, InterviewSectionKind.ENGLISH_SCREEN}:
                raise InterviewRuntimeValidationError("INTERVIEW_SECTION_INVALID")
            question_positions = [question.position for question in section.questions]
            if sorted(question_positions) != list(range(1, len(question_positions) + 1)):
                raise InterviewRuntimeValidationError("INTERVIEW_QUESTION_ORDER_INVALID")
            for question in section.questions:
                _uuid(question.question_id, "question_id")
                if not all(
                    (question.prompt.strip(), question.competency.strip(), question.rubric.strip())
                ):
                    raise InterviewRuntimeValidationError("INTERVIEW_QUESTION_INVALID")
                if (
                    question.source is InterviewQuestionSource.CV_PERSONALIZED
                    and not question.evidence
                ):
                    raise InterviewRuntimeValidationError("INTERVIEW_EVIDENCE_REQUIRED")

    def _require_available(self) -> None:
        if not self._enabled:
            raise InterviewDisabledError("INTERVIEW_DISABLED")
        if self._state is None or not self._token_secret:
            raise InterviewRuntimeNotReadyError("INTERVIEW_RUNTIME_NOT_READY")

    def _raise_unavailable(self) -> NoReturn:
        self._require_available()
        raise InterviewRuntimeNotReadyError("INTERVIEW_RUNTIME_NOT_READY")


def _uuid(value: str, field: str) -> None:
    try:
        UUID(value)
    except (ValueError, AttributeError) as exception:
        raise InterviewRuntimeValidationError(f"INVALID_{field.upper()}") from exception


def _nfc(value: Any) -> Any:
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, dict):
        return {key: _nfc(child) for key, child in value.items()}
    if isinstance(value, list):
        return [_nfc(child) for child in value]
    return value
