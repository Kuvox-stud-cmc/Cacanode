from __future__ import annotations

import json
from typing import Any, cast

import pytest

from app.modules.interview.internal.engine import DeterministicInterviewEngine
from app.modules.interview.internal.recovery import InterviewRecoveryWorker
from app.modules.interview.internal.redis_state import RuntimeCheckpoint
from app.modules.interview.transport.rabbitmq import ConfirmedInterviewPublisher

SESSION_ID = "22222222-2222-4222-8222-222222222222"
ATTEMPT_ID = "55555555-5555-4555-8555-555555555555"
TENANT_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
CALL_SID = "CA" + "1" * 32


class RecoverySettings:
    INTERVIEW_RECOVERY_POLL_SECONDS = 1
    INTERVIEW_RECOVERY_BATCH_SIZE = 10
    INTERVIEW_RECOVERY_MAX_ATTEMPTS = 3
    LLM_PROVIDER = "ollama"


def interview_payload() -> dict[str, Any]:
    return {
        "introductionText": "Hello.",
        "closingText": "Goodbye.",
        "durationLimitSeconds": 300,
        "interactionLimits": {
            "repetitionLimit": 1,
            "clarificationLimit": 1,
            "silenceTimeoutSeconds": 10,
            "silencePromptLimit": 1,
        },
        "sections": [
            {
                "sectionId": "66666666-6666-4666-8666-666666666666",
                "position": 1,
                "kind": "CORE",
                "languageTag": "en-US",
                "durationLimitSeconds": 300,
                "transitionText": "",
                "questions": [
                    {
                        "questionId": "77777777-7777-4777-8777-777777777777",
                        "position": 1,
                        "prompt": "Describe a system you built.",
                        "competency": "Engineering",
                        "rubric": "Clarity",
                        "followUpLimit": 1,
                        "source": "TEMPLATE",
                    }
                ],
            }
        ],
    }


def prepared_session() -> dict[str, Any]:
    return {
        "session_id": SESSION_ID,
        "call_attempt_id": ATTEMPT_ID,
        "tenant_id": TENANT_ID,
        "status": "claimed",
        "claimed_call_sid": CALL_SID,
        "payload": interview_payload(),
    }


def active_runtime_state(*, tts_characters: int = 0) -> dict[str, Any]:
    engine = DeterministicInterviewEngine(interview_payload())
    engine.begin(0.0)
    value = {
        "engine": engine.snapshot(12.0),
        "pending_audio": None,
        "usage": {
            "stt_audio_ms": 0,
            "tts_characters": tts_characters,
            "twilio_media_ms": 0,
            "llm_tokens": 0,
        },
    }
    engine.discard()
    return value


class RecoveryState:
    def __init__(self, checkpoint: RuntimeCheckpoint | None = None) -> None:
        self.checkpoint = checkpoint
        self.prepared = prepared_session()
        self.confirmed: set[str] = set()
        self.watchdog_registered = True
        self.terminalizations = 0
        self.released_leases = 0

    async def acquire_lease(self, session_id: str) -> str | None:
        assert session_id == SESSION_ID
        return "lease"

    async def release_lease(self, session_id: str, lease_token: str) -> None:
        assert session_id == SESSION_ID and lease_token == "lease"
        self.released_leases += 1

    async def prepared_session(self, session_id: str) -> dict[str, Any] | None:
        assert session_id == SESSION_ID
        return self.prepared

    async def load_checkpoint(self, session_id: str) -> RuntimeCheckpoint | None:
        assert session_id == SESSION_ID
        return self.checkpoint

    async def stage_checkpoint_event(self, session_id: str, **values: Any) -> int:
        assert session_id == SESSION_ID
        current_revision = self.checkpoint.revision if self.checkpoint is not None else 0
        if int(values["expected_revision"]) != current_revision:
            raise ValueError("Interview checkpoint revision conflict")
        revision = current_revision + 1
        self.checkpoint = RuntimeCheckpoint(
            revision=revision,
            phase=str(values["phase"]),
            runtime_state=dict(values["current_runtime_state"]),
            pending_event={
                "event_id": values["event_id"],
                "routing_key": values["routing_key"],
                "payload": values["payload"].decode("utf-8"),
                "runtime_state": dict(values["next_runtime_state"]),
                "commit_phase": values["commit_phase"],
            },
            call_sid=str(values["call_sid"]),
            recovery_deadline_epoch_seconds=None,
        )
        return revision

    async def publication_confirmed(self, event_id: str) -> bool:
        return event_id in self.confirmed

    async def mark_confirmed_publication(self, event_id: str) -> None:
        self.confirmed.add(event_id)

    async def commit_checkpoint_event(
        self, session_id: str, *, expected_revision: int, event_id: str
    ) -> int:
        assert session_id == SESSION_ID and self.checkpoint is not None
        assert self.checkpoint.revision == expected_revision
        assert event_id in self.confirmed
        pending = self.checkpoint.pending_event
        assert pending is not None and pending["event_id"] == event_id
        revision = expected_revision + 1
        self.checkpoint = RuntimeCheckpoint(
            revision=revision,
            phase=str(pending["commit_phase"]),
            runtime_state=cast(dict[str, Any], pending["runtime_state"]),
            pending_event=None,
            call_sid=self.checkpoint.call_sid,
            recovery_deadline_epoch_seconds=None,
        )
        return revision

    async def clear_recoverable(self, session_id: str) -> None:
        assert session_id == SESSION_ID
        self.watchdog_registered = False

    async def terminalize_runtime_session(
        self, session_id: str, call_attempt_id: str, call_sid: str
    ) -> bool:
        assert session_id == SESSION_ID
        assert call_attempt_id == ATTEMPT_ID and call_sid == CALL_SID
        self.terminalizations += 1
        self.prepared["status"] = "terminal"
        self.checkpoint = None
        self.watchdog_registered = False
        return True


class RecordingPublisher(ConfirmedInterviewPublisher):
    def __init__(self, state: RecoveryState, *, failures: int = 0) -> None:
        super().__init__(cast(Any, object()), cast(Any, state))
        self.state = state
        self.failures = failures
        self.publications: list[dict[str, Any]] = []

    async def publish(self, *, event_id: str, routing_key: str, payload: bytes) -> None:
        self.publications.append(
            {
                "event_id": event_id,
                "routing_key": routing_key,
                "payload": json.loads(payload),
            }
        )
        if self.failures > 0:
            self.failures -= 1
            raise RuntimeError("publisher confirm failed")
        self.state.confirmed.add(event_id)


def worker(state: RecoveryState, publisher: RecordingPublisher) -> InterviewRecoveryWorker:
    return InterviewRecoveryWorker(
        settings=cast(Any, RecoverySettings()),
        state=cast(Any, state),
        publisher=publisher,
    )


@pytest.mark.asyncio
async def test_process_death_before_first_checkpoint_publishes_zero_turn_failure() -> None:
    state = RecoveryState()
    publisher = RecordingPublisher(state)

    await worker(state, publisher)._recover(SESSION_ID)

    assert state.terminalizations == 1
    assert state.watchdog_registered is False
    assert state.released_leases == 1
    assert len(publisher.publications) == 1
    event = publisher.publications[0]["payload"]
    assert event["schema_version"] == "1.2"
    assert event["event_type"] == "interview.session.failed"
    assert event["failure_code"] == "RECOVERY_EXPIRED"
    assert event["expected_turn_count"] == 0
    assert event["connected_seconds"] == 0


@pytest.mark.asyncio
async def test_failed_confirmation_is_republished_by_watchdog_before_cleanup() -> None:
    state = RecoveryState()
    publisher = RecordingPublisher(state, failures=1)
    recovery_worker = worker(state, publisher)

    with pytest.raises(RuntimeError, match="confirm failed"):
        await recovery_worker._recover(SESSION_ID)

    assert state.watchdog_registered is True
    assert state.terminalizations == 0
    assert state.checkpoint is not None and state.checkpoint.pending_event is not None

    await recovery_worker._recover(SESSION_ID)

    assert state.terminalizations == 1
    assert state.watchdog_registered is False
    assert len(publisher.publications) == 2
    assert publisher.publications[0]["event_id"] == publisher.publications[1]["event_id"]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("event_type", "routing_key"),
    [
        ("interview.session.completed", "interview.session.completed"),
        ("interview.session.failed", "interview.session.failed"),
    ],
)
async def test_pending_terminal_event_is_recovered_without_second_result(
    event_type: str, routing_key: str
) -> None:
    checkpoint = RuntimeCheckpoint(
        revision=1,
        phase="TERMINAL_PUBLICATION",
        runtime_state=active_runtime_state(),
        pending_event={
            "event_id": f"pending-{event_type}",
            "routing_key": routing_key,
            "payload": json.dumps({"event_type": event_type}),
            "runtime_state": active_runtime_state(),
            "commit_phase": "TERMINAL_COMPLETE",
        },
        call_sid=CALL_SID,
        recovery_deadline_epoch_seconds=None,
    )
    state = RecoveryState(checkpoint)
    publisher = RecordingPublisher(state)

    await worker(state, publisher)._recover(SESSION_ID)

    assert state.terminalizations == 1
    assert [item["routing_key"] for item in publisher.publications] == [routing_key]


@pytest.mark.asyncio
async def test_committed_terminal_checkpoint_terminalizes_without_new_result() -> None:
    state = RecoveryState(
        RuntimeCheckpoint(
            revision=2,
            phase="TERMINAL_COMPLETE",
            runtime_state=active_runtime_state(),
            pending_event=None,
            call_sid=CALL_SID,
            recovery_deadline_epoch_seconds=None,
        )
    )
    publisher = RecordingPublisher(state)

    await worker(state, publisher)._recover(SESSION_ID)

    assert state.terminalizations == 1
    assert publisher.publications == []


@pytest.mark.asyncio
async def test_nonterminal_checkpoint_publishes_remaining_usage_and_one_failure() -> None:
    state = RecoveryState(
        RuntimeCheckpoint(
            revision=1,
            phase="LISTENING",
            runtime_state=active_runtime_state(tts_characters=12),
            pending_event=None,
            call_sid=CALL_SID,
            recovery_deadline_epoch_seconds=None,
        )
    )
    publisher = RecordingPublisher(state)

    await worker(state, publisher)._recover(SESSION_ID)

    event_types = [item["payload"]["event_type"] for item in publisher.publications]
    assert event_types == ["interview.provider.usage", "interview.session.failed"]
    assert state.terminalizations == 1
