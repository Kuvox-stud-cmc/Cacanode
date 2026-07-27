from __future__ import annotations

import json
from datetime import UTC, datetime
from decimal import Decimal
from typing import Any, cast
from uuid import UUID

from app.contracts.ai_interview_v1 import (
    FinalizedTurn,
    InterviewCompleted,
    InterviewFailed,
    ProviderUsage,
    interview_runtime_event_id,
    interview_runtime_turn_id,
)


def canonical_event(event: Any) -> bytes:
    return json.dumps(
        event.model_dump(mode="python"),
        default=_json_default,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def _json_default(value: Any) -> Any:
    if isinstance(value, Decimal):
        integral = value.to_integral_value()
        return int(integral) if value == integral else float(value)
    if isinstance(value, UUID):
        return str(value)
    if isinstance(value, datetime):
        return value.isoformat().replace("+00:00", "Z")
    raise TypeError(f"Unsupported durable-event JSON value: {type(value).__name__}")


def finalized_turn(
    *,
    tenant_id: str,
    session_id: str,
    call_attempt_id: str,
    sequence: int,
    speaker: str,
    turn_kind: str,
    section_id: str | None,
    question_id: str | None,
    language_tag: str,
    started_at_epoch_ms: int,
    ended_at_epoch_ms: int,
    transcript: str,
    interrupted: bool,
) -> FinalizedTurn:
    session = UUID(session_id)
    attempt = UUID(call_attempt_id)
    return FinalizedTurn(
        schema_version="1.2",
        event_id=interview_runtime_event_id(
            "interview.turn.finalized", session, attempt, f"turn:{sequence}:v1.2"
        ),
        event_type="interview.turn.finalized",
        occurred_at=datetime.now(UTC),
        tenant_id=UUID(tenant_id),
        aggregate_id=session,
        session_id=session,
        call_attempt_id=attempt,
        turn_id=interview_runtime_turn_id(session, attempt, sequence),
        sequence=sequence,
        speaker=cast(Any, speaker),
        turn_kind=cast(Any, turn_kind),
        section_id=UUID(section_id) if section_id else None,
        question_id=UUID(question_id) if question_id else None,
        language_tag=cast(Any, language_tag),
        started_at_epoch_ms=started_at_epoch_ms,
        ended_at_epoch_ms=ended_at_epoch_ms,
        transcript=transcript,
        interrupted=interrupted,
    )


def completed_result(
    *,
    tenant_id: str,
    session_id: str,
    call_attempt_id: str,
    completion_reason: str,
    expected_turn_count: int,
    connected_seconds: int,
    result: dict[str, Any],
) -> InterviewCompleted:
    session = UUID(session_id)
    attempt = UUID(call_attempt_id)
    return InterviewCompleted(
        schema_version="1.2",
        event_id=interview_runtime_event_id(
            "interview.session.completed", session, attempt, "completed:v1.2"
        ),
        event_type="interview.session.completed",
        occurred_at=datetime.now(UTC),
        tenant_id=UUID(tenant_id),
        aggregate_id=session,
        session_id=session,
        call_attempt_id=attempt,
        completion_reason=cast(Any, completion_reason),
        expected_turn_count=expected_turn_count,
        connected_seconds=connected_seconds,
        **result,
    )


def failed_result(
    *,
    tenant_id: str,
    session_id: str,
    call_attempt_id: str,
    failure_code: str,
    retryable: bool,
    detail: str,
    expected_turn_count: int,
    connected_seconds: int,
    result: dict[str, Any],
) -> InterviewFailed:
    session = UUID(session_id)
    attempt = UUID(call_attempt_id)
    return InterviewFailed(
        schema_version="1.2",
        event_id=interview_runtime_event_id(
            "interview.session.failed", session, attempt, "failed:v1.2"
        ),
        event_type="interview.session.failed",
        occurred_at=datetime.now(UTC),
        tenant_id=UUID(tenant_id),
        aggregate_id=session,
        session_id=session,
        call_attempt_id=attempt,
        expected_turn_count=expected_turn_count,
        connected_seconds=connected_seconds,
        failure_code=failure_code,
        retryable=retryable,
        detail=detail[:1000],
        **result,
    )


def provider_usage(
    *,
    tenant_id: str,
    session_id: str,
    call_attempt_id: str,
    provider: str,
    capability: str,
    quantity: Decimal,
    unit: str,
) -> ProviderUsage:
    session = UUID(session_id)
    attempt = UUID(call_attempt_id)
    semantic_key = f"{provider.lower()}:{capability.lower()}:v1.2"
    event_id = interview_runtime_event_id(
        "interview.provider.usage", session, attempt, semantic_key
    )
    return ProviderUsage(
        schema_version="1.2",
        event_id=event_id,
        event_type="interview.provider.usage",
        occurred_at=datetime.now(UTC),
        tenant_id=UUID(tenant_id),
        aggregate_id=session,
        usage_id=event_id,
        session_id=session,
        call_attempt_id=attempt,
        provider=cast(Any, provider),
        capability=cast(Any, capability),
        quantity=quantity,
        unit=cast(Any, unit),
        provider_request_id=None,
    )
