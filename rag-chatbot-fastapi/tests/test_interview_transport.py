from __future__ import annotations

import base64
from collections.abc import AsyncIterator
from dataclasses import replace
from typing import Any, cast

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
from app.modules.interview.internal.media import _MediaSession
from app.modules.interview.internal.redis_state import RuntimePreparation
from app.modules.interview.internal.runtime import (
    ConfiguredInterviewRuntime,
    canonical_interview_payload,
    interview_payload_sha256,
)
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


class MediaWebSocket:
    def __init__(self) -> None:
        self.sent: list[dict[str, Any]] = []

    async def send_json(self, value: dict[str, Any]) -> None:
        self.sent.append(value)

    async def close(self, code: int, reason: str) -> None:
        self.sent.append({"close": code, "reason": reason})


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
