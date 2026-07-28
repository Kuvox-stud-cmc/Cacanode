from __future__ import annotations

import asyncio
import base64
import json
from collections.abc import AsyncIterator
from dataclasses import replace
from decimal import Decimal
from typing import Any, cast
from unittest.mock import AsyncMock

import pytest

from app.bootstrap.settings import Settings
from app.modules.interview.api import (
    InteractionLimits,
    InterviewQuestionSnapshot,
    InterviewQuestionSource,
    InterviewRuntimeValidationError,
    InterviewSectionKind,
    InterviewSectionSnapshot,
    PrepareInterviewCommand,
)
from app.modules.interview.internal.durable_events import canonical_event, provider_usage
from app.modules.interview.internal.engine import DeterministicInterviewEngine
from app.modules.interview.internal.media import (
    _amplify_mulaw,
    _MediaSession,
    _mulaw_energy,
    _utterance_complete,
)
from app.modules.interview.internal.redis_state import (
    CheckpointRecovery,
    RuntimeCheckpoint,
    RuntimePreparation,
    RuntimeTokenClaim,
)
from app.modules.interview.internal.runtime import (
    ConfiguredInterviewRuntime,
    canonical_interview_payload,
    interview_payload_sha256,
)
from app.modules.interview.transport.rabbitmq import ConfirmedInterviewPublisher
from app.modules.model.api import AudioEncoding, AudioFrame
from app.modules.model.internal.cartesia_speech import (
    CARTESIA_API_VERSION,
    CARTESIA_STT_MODEL,
    CARTESIA_TTS_MODEL,
    CartesiaStreamingSpeechToTextSession,
    CartesiaStreamingTextToSpeech,
)


class RuntimeState:
    def __init__(self) -> None:
        self.expires_at = 0
        self.writes: list[dict[str, Any]] = []

    async def prepare_runtime_session(self, **values: Any) -> RuntimePreparation:
        self.writes.append(values)
        if not self.expires_at:
            self.expires_at = int(values["expires_at_epoch_seconds"])
        return RuntimePreparation(len(self.writes) == 1, values["payload_hash"], self.expires_at)


class CheckpointState:
    def __init__(self) -> None:
        self.checkpoint: RuntimeCheckpoint | None = None
        self.confirmed: set[str] = set()

    async def stage_checkpoint_event(self, session_id: str, **values: Any) -> int:
        del session_id
        revision = int(values["expected_revision"]) + 1
        self.checkpoint = RuntimeCheckpoint(
            revision=revision,
            phase=str(values["phase"]),
            runtime_state=dict(values["current_runtime_state"]),
            pending_event={
                "event_id": values["event_id"],
                "routing_key": values["routing_key"],
                "payload": values["payload"].decode(),
                "runtime_state": dict(values["next_runtime_state"]),
                "commit_phase": values["commit_phase"],
            },
            call_sid=str(values["call_sid"]),
            recovery_deadline_epoch_seconds=None,
        )
        return revision

    async def publication_confirmed(self, event_id: str) -> bool:
        return event_id in self.confirmed

    async def commit_checkpoint_event(
        self, session_id: str, *, expected_revision: int, event_id: str
    ) -> int:
        del session_id
        assert self.checkpoint is not None
        assert self.checkpoint.revision == expected_revision
        assert event_id in self.confirmed
        pending = self.checkpoint.pending_event
        assert pending is not None
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

    async def load_checkpoint(self, session_id: str) -> RuntimeCheckpoint | None:
        del session_id
        return self.checkpoint


class FailureInjectedPublisher(ConfirmedInterviewPublisher):
    def __init__(self, state: CheckpointState) -> None:
        super().__init__(cast(Any, object()), cast(Any, state))
        self.state = state
        self.fail = True
        self.publications = 0

    async def publish(self, *, event_id: str, routing_key: str, payload: bytes) -> None:
        del routing_key, payload
        self.publications += 1
        if self.fail:
            raise RuntimeError("publisher confirm failed")
        self.state.confirmed.add(event_id)


def command() -> PrepareInterviewCommand:
    value = PrepareInterviewCommand(
        session_id="22222222-2222-4222-8222-222222222222",
        call_attempt_id="55555555-5555-4555-8555-555555555555",
        tenant_id="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        template_revision_id="44444444-4444-4444-8444-444444444444",
        snapshot_version="interview-session-v1",
        snapshot_sha256="",
        company_display_name="Công ty Ánh Dương",
        candidate_display_name="Nguyễn Văn A",
        introduction_text="Xin chào.",
        disclosure_text="Cuộc gọi không được ghi âm.",
        closing_text="Cảm ơn.",
        duration_limit_seconds=300,
        interaction_limits=InteractionLimits(1, 1, 10, 1),
        recording_enabled=False,
        cv_personalization_enabled=False,
        sections=(
            InterviewSectionSnapshot(
                section_id="66666666-6666-4666-8666-666666666666",
                position=1,
                kind=InterviewSectionKind.CORE,
                language_tag="vi-VN",
                duration_limit_seconds=300,
                transition_text="",
                questions=(
                    InterviewQuestionSnapshot(
                        question_id="77777777-7777-4777-8777-777777777777",
                        position=1,
                        prompt="Hãy mô tả một hệ thống bạn đã xây dựng.",
                        competency="Kỹ thuật",
                        rubric="Đánh giá tính rõ ràng.",
                        follow_up_limit=1,
                        source=InterviewQuestionSource.TEMPLATE,
                    ),
                ),
            ),
        ),
    )
    return replace(
        value, snapshot_sha256=interview_payload_sha256(canonical_interview_payload(value))
    )


@pytest.mark.asyncio
async def test_preparation_uses_deterministic_token_and_detached_canonical_hash() -> None:
    state = RuntimeState()
    runtime = ConfiguredInterviewRuntime(
        enabled=True,
        state=cast(Any, state),
        token_secret="runtime-secret",
        now=lambda: 1_700_000_000,
    )
    first = await runtime.prepare(command())
    replay = await runtime.prepare(command())
    assert first.runtime_token == replay.runtime_token
    assert first.expires_at_epoch_seconds == replay.expires_at_epoch_seconds == 1_700_000_900
    assert first.accepted_snapshot_sha256 == command().snapshot_sha256
    assert state.writes[0]["payload"]["companyDisplayName"] == "Công ty Ánh Dương"
    assert "runtime_token" not in state.writes[0]


@pytest.mark.asyncio
async def test_confirm_failure_leaves_staged_event_for_watchdog_republication() -> None:
    state = CheckpointState()
    publisher = FailureInjectedPublisher(state)
    payload = b'{"schema_version":"1.2"}'

    with pytest.raises(RuntimeError, match="confirm failed"):
        await publisher.publish_checkpointed(
            session_id="session",
            expected_revision=0,
            phase="TERMINAL_PUBLICATION",
            commit_phase="TERMINAL_COMPLETE",
            current_runtime_state={"engine": {"next_turn_sequence": 1}},
            next_runtime_state={"engine": {"next_turn_sequence": 1}},
            event_id="event-id",
            routing_key="interview.session.failed",
            payload=payload,
            call_sid="CA" + "1" * 32,
        )

    assert state.checkpoint is not None
    assert state.checkpoint.pending_event is not None
    publisher.fail = False

    recovered = await publisher.recover_checkpoint("session")

    assert recovered == CheckpointRecovery(
        event_id="event-id", revision=2, phase="TERMINAL_COMPLETE"
    )
    assert publisher.publications == 2
    assert state.checkpoint is not None and state.checkpoint.pending_event is None

    committed = await publisher.recover_checkpoint(
        "session", requested_event_id="event-id"
    )

    assert committed == CheckpointRecovery(
        event_id="event-id", revision=2, phase="TERMINAL_COMPLETE"
    )


class EarlierPendingPublisher:
    def __init__(self) -> None:
        self.publish_attempts: list[dict[str, Any]] = []
        self.recoveries = 0

    async def publish_checkpointed(self, **values: Any) -> int:
        self.publish_attempts.append(values)
        if len(self.publish_attempts) == 1:
            raise ValueError("Interview checkpoint revision conflict")
        return int(values["expected_revision"]) + 2

    async def recover_checkpoint(
        self, session_id: str, *, requested_event_id: str | None = None
    ) -> CheckpointRecovery | None:
        del session_id
        assert requested_event_id is not None
        self.recoveries += 1
        return CheckpointRecovery(
            event_id="earlier-turn-event", revision=4, phase="TERMINAL_PUBLICATION"
        )


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("event_id", "routing_key"),
    [
        ("requested-usage-event", "interview.provider.usage"),
        ("requested-terminal-event", "interview.session.failed"),
    ],
)
async def test_recovering_an_earlier_turn_does_not_skip_requested_publication(
    event_id: str, routing_key: str
) -> None:
    publisher = EarlierPendingPublisher()
    session = _MediaSession(
        websocket=cast(Any, object()),
        settings=Settings(_env_file=(), INTERVIEW_PUBLISH_CONFIRM_MAX_ATTEMPTS=3),
        state=cast(Any, object()),
        tts=cast(Any, object()),
        stt_factory=cast(Any, object()),
        evaluator=None,
        publisher=cast(Any, publisher),
        monotonic=lambda: 0,
    )
    session.claim = RuntimeTokenClaim(
        session_id="session",
        call_attempt_id="attempt",
        tenant_id="tenant",
        same_call_replay=False,
    )
    session.call_sid = "CA" + "1" * 32
    session.checkpoint_revision = 2

    revision = await session._publish_checkpointed_event(
        phase="TERMINAL_PUBLICATION",
        commit_phase="TERMINAL_COMPLETE",
        event_id=event_id,
        routing_key=routing_key,
        payload=b"{}",
        current_state={"engine": {}},
        next_state={"engine": {}},
    )

    assert publisher.recoveries == 1
    assert [attempt["event_id"] for attempt in publisher.publish_attempts] == [
        event_id,
        event_id,
    ]
    assert publisher.publish_attempts[1]["expected_revision"] == 4
    assert revision == 6


class AlreadyCommittedPublisher:
    def __init__(self) -> None:
        self.publish_attempts = 0
        self.recoveries = 0

    async def publish_checkpointed(self, **values: Any) -> int:
        del values
        self.publish_attempts += 1
        raise ValueError("Interview pending-event commit conflict")

    async def recover_checkpoint(
        self, session_id: str, *, requested_event_id: str | None = None
    ) -> CheckpointRecovery | None:
        del session_id
        assert requested_event_id == "candidate-event"
        self.recoveries += 1
        return CheckpointRecovery(
            event_id=requested_event_id,
            revision=8,
            phase="CANDIDATE_EVALUATION",
        )


@pytest.mark.asyncio
async def test_event_committed_by_recovery_is_success_without_duplicate_publication() -> None:
    publisher = AlreadyCommittedPublisher()
    session = _MediaSession(
        websocket=cast(Any, object()),
        settings=Settings(_env_file=(), INTERVIEW_PUBLISH_CONFIRM_MAX_ATTEMPTS=3),
        state=cast(Any, object()),
        tts=cast(Any, object()),
        stt_factory=cast(Any, object()),
        evaluator=None,
        publisher=cast(Any, publisher),
        monotonic=lambda: 0,
    )
    session.claim = RuntimeTokenClaim(
        session_id="session",
        call_attempt_id="attempt",
        tenant_id="tenant",
        same_call_replay=False,
    )

    revision = await session._publish_checkpointed_event(
        phase="CANDIDATE_PUBLICATION",
        commit_phase="CANDIDATE_EVALUATION",
        event_id="candidate-event",
        routing_key="interview.turn.finalized",
        payload=b"{}",
        current_state={"engine": {}},
        next_state={"engine": {}},
    )

    assert revision == 8
    assert publisher.publish_attempts == 1
    assert publisher.recoveries == 1


class TerminalCheckpointPublisher(AlreadyCommittedPublisher):
    async def recover_checkpoint(
        self, session_id: str, *, requested_event_id: str | None = None
    ) -> CheckpointRecovery | None:
        del session_id, requested_event_id
        self.recoveries += 1
        return CheckpointRecovery(event_id=None, revision=12, phase="TERMINAL_COMPLETE")


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("phase", "commit_phase", "routing_key"),
    [
        (
            "CANDIDATE_PUBLICATION",
            "CANDIDATE_EVALUATION",
            "interview.turn.finalized",
        ),
        (
            "TERMINAL_PUBLICATION",
            "TERMINAL_PUBLICATION",
            "interview.provider.usage",
        ),
        (
            "TERMINAL_PUBLICATION",
            "TERMINAL_COMPLETE",
            "interview.session.failed",
        ),
    ],
)
async def test_publication_cannot_overwrite_terminal_recovery(
    phase: str, commit_phase: str, routing_key: str
) -> None:
    publisher = TerminalCheckpointPublisher()
    session = _MediaSession(
        websocket=cast(Any, object()),
        settings=Settings(_env_file=(), INTERVIEW_PUBLISH_CONFIRM_MAX_ATTEMPTS=3),
        state=cast(Any, object()),
        tts=cast(Any, object()),
        stt_factory=cast(Any, object()),
        evaluator=None,
        publisher=cast(Any, publisher),
        monotonic=lambda: 0,
    )
    session.claim = RuntimeTokenClaim(
        session_id="session",
        call_attempt_id="attempt",
        tenant_id="tenant",
        same_call_replay=False,
    )

    with pytest.raises(RuntimeError, match="CHECKPOINT_TERMINALIZED"):
        await session._publish_checkpointed_event(
            phase=phase,
            commit_phase=commit_phase,
            event_id="candidate-event",
            routing_key=routing_key,
            payload=b"{}",
            current_state={"engine": {}},
            next_state={"engine": {}},
        )

    assert publisher.publish_attempts == 1
    assert publisher.recoveries == 1


class ExactCommittedTerminalPublisher(AlreadyCommittedPublisher):
    async def recover_checkpoint(
        self, session_id: str, *, requested_event_id: str | None = None
    ) -> CheckpointRecovery | None:
        del session_id
        self.recoveries += 1
        return CheckpointRecovery(
            event_id=requested_event_id, revision=12, phase="TERMINAL_COMPLETE"
        )


@pytest.mark.asyncio
async def test_exact_committed_terminal_publication_is_success() -> None:
    publisher = ExactCommittedTerminalPublisher()
    session = _MediaSession(
        websocket=cast(Any, object()),
        settings=Settings(_env_file=(), INTERVIEW_PUBLISH_CONFIRM_MAX_ATTEMPTS=3),
        state=cast(Any, object()),
        tts=cast(Any, object()),
        stt_factory=cast(Any, object()),
        evaluator=None,
        publisher=cast(Any, publisher),
        monotonic=lambda: 0,
    )
    session.claim = RuntimeTokenClaim(
        session_id="session",
        call_attempt_id="attempt",
        tenant_id="tenant",
        same_call_replay=False,
    )

    revision = await session._publish_checkpointed_event(
        phase="TERMINAL_PUBLICATION",
        commit_phase="TERMINAL_COMPLETE",
        event_id="terminal-event",
        routing_key="interview.session.failed",
        payload=b"{}",
        current_state={"engine": {}},
        next_state={"engine": {}},
    )

    assert revision == 12
    assert publisher.publish_attempts == 1
    assert publisher.recoveries == 1


@pytest.mark.asyncio
async def test_preparation_rejects_mismatched_hash_and_recording() -> None:
    state = RuntimeState()
    runtime = ConfiguredInterviewRuntime(
        enabled=True,
        state=cast(Any, state),
        token_secret="runtime-secret",
    )
    invalid = command()
    invalid = replace(invalid, snapshot_sha256="0" * 64)
    with pytest.raises(InterviewRuntimeValidationError, match="HASH_MISMATCH"):
        await runtime.prepare(invalid)


class SttConnection:
    def __init__(self) -> None:
        self.audio: list[bytes] = []
        self.responses = [
            {"type": "speech_started", "timestamp_ms": 1},
            {"text": "xin chào", "is_final": True, "done": True, "end_ms": 20},
        ]

    async def send(self, data: bytes) -> None:
        self.audio.append(data)

    async def receive(self) -> dict[str, Any] | None:
        return self.responses.pop(0) if self.responses else None

    async def finish(self) -> None:
        pass

    async def close(self) -> None:
        pass


class SpeechFactory:
    def __init__(self) -> None:
        self.connection = SttConnection()
        self.stt_settings: dict[str, str] = {}
        self.tts_settings: dict[str, str] = {}

    async def open_stt(self, **values: str) -> SttConnection:
        self.stt_settings = values
        return self.connection

    async def _chunks(self) -> AsyncIterator[bytes]:
        yield b"\xff" * 160

    def synthesize(self, **values: str) -> AsyncIterator[bytes]:
        self.tts_settings = values
        return self._chunks()


@pytest.mark.asyncio
async def test_cartesia_adapters_select_models_languages_and_pass_mulaw_directly() -> None:
    factory = SpeechFactory()
    stt = CartesiaStreamingSpeechToTextSession(factory)
    await stt.start(language_tag="vi-VN")
    frame = AudioFrame(b"\x7f" * 160, 0, 8000, 1, AudioEncoding.MULAW)
    await stt.send(frame)
    events = await stt.finish()
    assert factory.connection.audio == [frame.data]
    assert factory.stt_settings == {
        "model": CARTESIA_STT_MODEL,
        "language": "vi",
        "api_version": CARTESIA_API_VERSION,
    }
    assert any(getattr(event, "text", "") == "xin chào" for event in events)

    tts = CartesiaStreamingTextToSpeech(
        factory,
        english_voice_id="voice-en",
        vietnamese_voice_id="voice-vi",
    )
    output = [item async for item in tts.synthesize("Hello", language_tag="en-US")]
    assert output[0].data == b"\xff" * 160
    assert output[0].encoding is AudioEncoding.MULAW
    assert factory.tts_settings["model"] == CARTESIA_TTS_MODEL
    assert factory.tts_settings["language"] == "en"
    assert factory.tts_settings["voice_id"] == "voice-en"


def test_mulaw_output_gain_increases_non_silent_audio_without_changing_length() -> None:
    source = bytes([0x90, 0x10, 0xA0, 0x20]) * 40
    amplified = _amplify_mulaw(source, 6.0)
    assert len(amplified) == len(source)
    assert _mulaw_energy(amplified) > _mulaw_energy(source)
    settings = Settings(_env_file=())
    assert settings.INTERVIEW_TTS_OUTPUT_GAIN_DB == 0
    assert _amplify_mulaw(source, settings.INTERVIEW_TTS_OUTPUT_GAIN_DB) == source


def test_utterance_finalizes_before_the_stt_provider_session_boundary() -> None:
    assert not _utterance_complete(
        speech_ms=88_999,
        silence_ms=0,
        silence_limit_ms=3000,
        utterance_limit_ms=90_000,
    )
    assert _utterance_complete(
        speech_ms=89_000,
        silence_ms=0,
        silence_limit_ms=3000,
        utterance_limit_ms=90_000,
    )
    assert _utterance_complete(
        speech_ms=10_000,
        silence_ms=3000,
        silence_limit_ms=3000,
        utterance_limit_ms=90_000,
    )


def test_durable_event_decimal_fields_are_json_numbers() -> None:
    event = provider_usage(
        tenant_id="11111111-1111-4111-8111-111111111111",
        session_id="22222222-2222-4222-8222-222222222222",
        call_attempt_id="33333333-3333-4333-8333-333333333333",
        provider="CARTESIA",
        capability="TTS",
        quantity=Decimal("12.5"),
        unit="AUDIO_SECOND",
    )

    payload = json.loads(canonical_event(event))

    assert payload["quantity"] == 12.5
    assert isinstance(payload["quantity"], float)


class MediaWebSocket:
    def __init__(self) -> None:
        self.sent: list[dict[str, Any]] = []

    async def send_json(self, value: dict[str, Any]) -> None:
        self.sent.append(value)

    async def close(self, code: int, reason: str) -> None:
        self.sent.append({"close": code, "reason": reason})


class StopMediaWebSocket(MediaWebSocket):
    async def receive_text(self) -> str:
        return (
            '{"event":"stop","sequenceNumber":"1",'
            '"streamSid":"MZ11111111111111111111111111111111"}'
        )


class FailingMediaWebSocket(MediaWebSocket):
    async def send_json(self, payload: dict[str, Any]) -> None:
        del payload
        raise ConnectionError("closed")


class MediaTts:
    async def synthesize(self, text: str, *, language_tag: str) -> AsyncIterator[AudioFrame]:
        del text, language_tag
        yield AudioFrame(b"\xff" * 160, 0, 8000, 1, AudioEncoding.MULAW)


class MediaStt:
    def __init__(self) -> None:
        self.language = ""
        self.frames: list[AudioFrame] = []
        self.closed = False

    async def start(self, *, language_tag: str) -> None:
        self.language = language_tag

    async def send(self, frame: AudioFrame) -> tuple[object, ...]:
        self.frames.append(frame)
        return ()

    async def finish(self) -> tuple[object, ...]:
        return ()

    async def close(self) -> None:
        self.closed = True


class FinalMediaStt(MediaStt):
    async def finish(self) -> tuple[object, ...]:
        transcript = type(
            "Transcript", (), {"text": "A concrete candidate answer", "is_final": True}
        )()
        return (transcript,)


class MediaEvaluator:
    measured_tokens = 0

    async def evaluate(self, **values: Any) -> None:
        del values
        await asyncio.sleep(0)
        return None


@pytest.mark.asyncio
async def test_media_marks_gate_fresh_fixed_language_stt_and_mulaw_passthrough() -> None:
    settings = Settings(
        _env_file=(),
        INTERVIEW_ENABLED=True,
        INTERVIEW_MESSAGING_ENABLED=True,
        INTERVIEW_MEDIA_STREAM_ENABLED=True,
        INTERVIEW_TRANSPORT_SMOKE_MODE=True,
        INTERVIEW_RUNTIME_TOKEN_SECRET="runtime-secret",
        TWILIO_ACCOUNT_SID="AC" + "1" * 32,
        TWILIO_AUTH_TOKEN="auth-token",
        TWILIO_MEDIA_STREAM_WSS_URL="wss://example.test/media",
        CARTESIA_API_KEY="cartesia-key",
        CARTESIA_ENGLISH_VOICE_ID="voice-en",
        CARTESIA_VIETNAMESE_VOICE_ID="voice-vi",
    )
    websocket = MediaWebSocket()
    sessions: list[MediaStt] = []

    def stt_factory() -> MediaStt:
        value = MediaStt()
        sessions.append(value)
        return value

    session = _MediaSession(
        websocket=cast(Any, websocket),
        query_token=None,
        settings=settings,
        state=cast(Any, object()),
        tts=MediaTts(),
        stt_factory=stt_factory,
        evaluator=None,
        monotonic=lambda: 0,
    )
    session.stream_sid = "MZ" + "1" * 32
    session.language_tag = "vi-VN"
    await session._send_audio_batch("prompt", "vi-VN", "question")
    mark = session.mark_waiting
    assert session.listening is False
    await session._media(
        {
            "streamSid": session.stream_sid,
            "media": {"payload": "AAAA"},
        }
    )
    assert not sessions
    await session._mark({"streamSid": session.stream_sid, "mark": {"name": mark}})
    assert sessions[0].language == "vi-VN" and session.listening
    raw = b"\x00" * 160
    await session._media(
        {
            "streamSid": session.stream_sid,
            "media": {"payload": base64.b64encode(raw).decode()},
        }
    )
    assert sessions[0].frames[0].data == raw
    assert sessions[0].frames[0].encoding is AudioEncoding.MULAW

    session.listening = False
    await session._send_audio_batch("next", "en-US", "question")
    mark = session.mark_waiting
    session.language_tag = "en-US"
    await session._mark({"streamSid": session.stream_sid, "mark": {"name": mark}})
    assert sessions[0].closed
    assert sessions[1].language == "en-US"
    assert websocket.sent[-2]["event"] == "media"
    assert websocket.sent[-1]["event"] == "mark"


@pytest.mark.asyncio
async def test_ignored_media_frames_yield_to_heartbeat_and_durability_tasks() -> None:
    session = _MediaSession(
        websocket=cast(Any, MediaWebSocket()),
        settings=Settings(_env_file=()),
        state=cast(Any, object()),
        tts=MediaTts(),
        stt_factory=MediaStt,
        evaluator=None,
        monotonic=lambda: 0,
    )
    session.stream_sid = "MZ" + "1" * 32
    session.listening = False
    background_ran = False

    async def background() -> None:
        nonlocal background_ran
        background_ran = True

    task = asyncio.create_task(background())
    await session._media(
        {
            "streamSid": session.stream_sid,
            "media": {"payload": base64.b64encode(b"\xff" * 160).decode()},
        }
    )
    assert background_ran is True
    await task


@pytest.mark.asyncio
async def test_candidate_turn_is_confirmed_before_acknowledgement_audio() -> None:
    settings = Settings(
        _env_file=(),
        INTERVIEW_ENABLED=True,
        INTERVIEW_MESSAGING_ENABLED=True,
        INTERVIEW_MEDIA_STREAM_ENABLED=True,
        INTERVIEW_DURABLE_RESULTS_ENABLED=True,
        INTERVIEW_ENGINE_ENABLED=True,
        INTERVIEW_RUNTIME_TOKEN_SECRET="runtime-secret",
        TWILIO_ACCOUNT_SID="AC" + "1" * 32,
        TWILIO_AUTH_TOKEN="auth-token",
        TWILIO_MEDIA_STREAM_WSS_URL="wss://example.test/media",
        CARTESIA_API_KEY="cartesia-key",
        CARTESIA_ENGLISH_VOICE_ID="voice-en",
        CARTESIA_VIETNAMESE_VOICE_ID="voice-vi",
    )
    session = _MediaSession(
        websocket=cast(Any, MediaWebSocket()),
        settings=settings,
        state=cast(Any, object()),
        tts=MediaTts(),
        stt_factory=MediaStt,
        evaluator=cast(Any, MediaEvaluator()),
        publisher=cast(Any, object()),
        monotonic=lambda: 0,
    )
    session.claim = RuntimeTokenClaim(
        session_id="session", call_attempt_id="attempt", tenant_id="tenant", same_call_replay=False
    )
    session.engine = DeterministicInterviewEngine(canonical_interview_payload(command()))
    session.engine.begin(0)
    session.stt = FinalMediaStt()
    session.listening = True
    session.speech_ms = 1000
    order: list[str] = []

    async def publish_candidate(transcript: str, *, interrupted: bool) -> int:
        assert transcript == "A concrete candidate answer"
        assert interrupted is False
        order.append("candidate")
        return 7

    async def send_acknowledgement(*values: Any, **keywords: Any) -> None:
        del values, keywords
        order.append("audio")

    session._publish_candidate_turn = cast(Any, publish_candidate)
    session._send_audio_segments = cast(Any, send_acknowledgement)

    await session._finish_utterance()

    assert order == ["candidate", "audio"]
    assert session.pending_model_task is not None
    session.pending_model_task.cancel()
    await asyncio.gather(session.pending_model_task, return_exceptions=True)


@pytest.mark.asyncio
async def test_explicit_twilio_stop_publishes_retryable_terminal_failure_immediately() -> None:
    settings = Settings(
        _env_file=(),
        INTERVIEW_ENABLED=True,
        INTERVIEW_MESSAGING_ENABLED=True,
        INTERVIEW_MEDIA_STREAM_ENABLED=True,
        INTERVIEW_ENGINE_ENABLED=True,
        INTERVIEW_DURABLE_RESULTS_ENABLED=True,
        INTERVIEW_RUNTIME_TOKEN_SECRET="runtime-secret",
        TWILIO_ACCOUNT_SID="AC" + "1" * 32,
        TWILIO_AUTH_TOKEN="auth-token",
        TWILIO_MEDIA_STREAM_WSS_URL="wss://example.test/media",
        CARTESIA_API_KEY="cartesia-key",
        CARTESIA_ENGLISH_VOICE_ID="voice-en",
        CARTESIA_VIETNAMESE_VOICE_ID="voice-vi",
    )
    session = _MediaSession(
        websocket=cast(Any, StopMediaWebSocket()),
        settings=settings,
        state=cast(Any, object()),
        tts=MediaTts(),
        stt_factory=MediaStt,
        evaluator=None,
        publisher=cast(Any, object()),
        monotonic=lambda: 0,
    )
    session.stream_sid = "MZ" + "1" * 32
    session.claim = RuntimeTokenClaim(
        session_id="session", call_attempt_id="attempt", tenant_id="tenant", same_call_replay=False
    )
    session.engine = cast(Any, object())
    publish = AsyncMock()
    session._publish_terminal_and_usage = publish

    await session.run()

    publish.assert_awaited_once_with(
        interruption_failure=(
            "CANDIDATE_HANGUP",
            True,
            "The candidate ended the phone call before the interview completed.",
        )
    )
    assert session.clean_finished is True


@pytest.mark.asyncio
async def test_runtime_provider_failure_publishes_terminal_failure_immediately() -> None:
    settings = Settings(
        _env_file=(),
        INTERVIEW_ENABLED=True,
        INTERVIEW_MESSAGING_ENABLED=True,
        INTERVIEW_MEDIA_STREAM_ENABLED=True,
        INTERVIEW_ENGINE_ENABLED=True,
        INTERVIEW_DURABLE_RESULTS_ENABLED=True,
        INTERVIEW_RUNTIME_TOKEN_SECRET="runtime-secret",
        TWILIO_ACCOUNT_SID="AC" + "1" * 32,
        TWILIO_AUTH_TOKEN="auth-token",
        TWILIO_MEDIA_STREAM_WSS_URL="wss://example.test/media",
        CARTESIA_API_KEY="cartesia-key",
        CARTESIA_ENGLISH_VOICE_ID="voice-en",
        CARTESIA_VIETNAMESE_VOICE_ID="voice-vi",
    )
    session = _MediaSession(
        websocket=cast(Any, MediaWebSocket()),
        settings=settings,
        state=cast(Any, object()),
        tts=MediaTts(),
        stt_factory=MediaStt,
        evaluator=None,
        publisher=cast(Any, object()),
        monotonic=lambda: 0,
    )
    session.claim = RuntimeTokenClaim(
        session_id="session", call_attempt_id="attempt", tenant_id="tenant", same_call_replay=False
    )
    session.engine = cast(Any, object())
    publish = AsyncMock()
    session._publish_terminal_and_usage = publish

    await session.publish_runtime_failure("TTS_FAILURE")

    publish.assert_awaited_once_with(
        interruption_failure=(
            "TTS_FAILURE",
            True,
            "The interview media runtime stopped unexpectedly (TTS_FAILURE).",
        )
    )
    assert session.clean_finished is True


@pytest.mark.asyncio
async def test_internal_runtime_failure_is_nonretryable_and_immediate() -> None:
    settings = Settings(
        _env_file=(),
        INTERVIEW_ENABLED=True,
        INTERVIEW_MESSAGING_ENABLED=True,
        INTERVIEW_MEDIA_STREAM_ENABLED=True,
        INTERVIEW_ENGINE_ENABLED=True,
        INTERVIEW_DURABLE_RESULTS_ENABLED=True,
        INTERVIEW_RUNTIME_TOKEN_SECRET="runtime-secret",
        TWILIO_ACCOUNT_SID="AC" + "1" * 32,
        TWILIO_AUTH_TOKEN="auth-token",
        TWILIO_MEDIA_STREAM_WSS_URL="wss://example.test/media",
        CARTESIA_API_KEY="cartesia-key",
        CARTESIA_ENGLISH_VOICE_ID="voice-en",
        CARTESIA_VIETNAMESE_VOICE_ID="voice-vi",
    )
    session = _MediaSession(
        websocket=cast(Any, MediaWebSocket()),
        settings=settings,
        state=cast(Any, object()),
        tts=MediaTts(),
        stt_factory=MediaStt,
        evaluator=None,
        publisher=cast(Any, object()),
        monotonic=lambda: 0,
    )
    session.claim = RuntimeTokenClaim(
        session_id="session", call_attempt_id="attempt", tenant_id="tenant", same_call_replay=False
    )
    session.engine = cast(Any, object())
    publish = AsyncMock()
    session._publish_terminal_and_usage = publish

    await session.publish_runtime_failure("INTERVIEW_RUNTIME_ERROR")

    publish.assert_awaited_once_with(
        interruption_failure=(
            "INTERVIEW_RUNTIME_ERROR",
            False,
            "The interview media runtime stopped unexpectedly (INTERVIEW_RUNTIME_ERROR).",
        )
    )
    assert session.clean_finished is True


@pytest.mark.asyncio
async def test_closed_twilio_websocket_is_not_misclassified_as_tts_failure() -> None:
    settings = Settings(_env_file=(), INTERVIEW_TTS_OUTPUT_GAIN_DB=0)
    session = _MediaSession(
        websocket=cast(Any, FailingMediaWebSocket()),
        settings=settings,
        state=cast(Any, object()),
        tts=MediaTts(),
        stt_factory=MediaStt,
        evaluator=None,
        publisher=None,
        monotonic=lambda: 0,
    )
    session.stream_sid = "MZ" + "1" * 32

    with pytest.raises(RuntimeError, match="MEDIA_SEND_FAILURE"):
        await session._send_audio_batch("Question", "en-US", "question")
