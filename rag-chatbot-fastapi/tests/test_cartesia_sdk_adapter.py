from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Any

import pytest

from app.modules.model.api import AudioEncoding, AudioFrame
from app.modules.model.internal.cartesia_speech import (
    CartesiaStreamingSpeechToTextSession,
    SdkCartesiaSpeechSocketFactory,
)


class _TtsItem:
    audio = b"audio"


class _TtsStream:
    def __aiter__(self) -> AsyncIterator[_TtsItem]:
        return self._items()

    async def _items(self) -> AsyncIterator[_TtsItem]:
        yield _TtsItem()


class _TtsSocket:
    def __init__(self) -> None:
        self.send_values: dict[str, Any] = {}
        self.closed = False

    async def send(self, **values: Any) -> _TtsStream:
        self.send_values = values
        return _TtsStream()

    async def close(self) -> None:
        self.closed = True


class _TtsResource:
    def __init__(self) -> None:
        self.socket = _TtsSocket()
        self.websocket_values: dict[str, Any] = {}

    async def websocket(self, **values: Any) -> _TtsSocket:
        self.websocket_values = values
        return self.socket


class _SttEvent:
    def __init__(self, values: dict[str, Any]) -> None:
        self.values = values

    def model_dump(self) -> dict[str, Any]:
        return dict(self.values)


class _SttSocket:
    def __init__(self) -> None:
        self.audio: list[bytes] = []
        self.commands: list[str] = []
        self.events = [
            _SttEvent({"type": "transcript", "text": "hello", "is_final": True}),
            _SttEvent({"type": "flush_done"}),
        ]
        self.closed = False

    async def send_raw(self, data: bytes) -> None:
        self.audio.append(data)

    async def send(self, command: str) -> None:
        self.commands.append(command)

    async def recv(self) -> _SttEvent:
        return self.events.pop(0)

    async def close(self) -> None:
        self.closed = True


class _SttManager:
    def __init__(self, socket: _SttSocket) -> None:
        self.socket = socket

    async def enter(self) -> _SttSocket:
        return self.socket


class _ManualFinalize:
    def __init__(self) -> None:
        self.socket = _SttSocket()
        self.websocket_values: dict[str, Any] = {}

    def websocket(self, **values: Any) -> _SttManager:
        self.websocket_values = values
        return _SttManager(self.socket)


class _Client:
    def __init__(self) -> None:
        self.tts = _TtsResource()
        self.stt = type("SttResource", (), {})()
        self.stt.manual_finalize = _ManualFinalize()


def _factory() -> tuple[SdkCartesiaSpeechSocketFactory, _Client]:
    client = _Client()
    factory = object.__new__(SdkCartesiaSpeechSocketFactory)
    factory._client = client
    return factory, client


@pytest.mark.asyncio
async def test_sdk_35_tts_websocket_is_awaited_without_obsolete_api_version() -> None:
    factory, client = _factory()

    chunks = [
        chunk
        async for chunk in factory.synthesize(
            text="Hello",
            model="sonic",
            language="en",
            voice_id="voice",
            api_version="ignored-by-sdk-3.5",
        )
    ]

    assert chunks == [b"audio"]
    assert client.tts.websocket_values == {}
    assert client.tts.socket.send_values["model_id"] == "sonic"
    assert client.tts.socket.closed is True


@pytest.mark.asyncio
async def test_sdk_35_manual_finalize_stt_uses_raw_audio_and_finalize_command() -> None:
    factory, client = _factory()
    connection = await factory.open_stt(
        model="ink-whisper", language="vi", api_version="ignored-by-sdk-3.5"
    )

    await connection.send(b"audio")
    await connection.finish()
    transcript = await connection.receive()
    finished = await connection.receive()
    await connection.close()

    manual = client.stt.manual_finalize
    assert manual.websocket_values == {
        "model": "ink-whisper",
        "language": "vi",
        "encoding": "pcm_mulaw",
        "sample_rate": 8000,
    }
    assert manual.socket.audio == [b"audio"]
    assert manual.socket.commands == ["finalize", "close"]
    assert transcript == {"type": "transcript", "text": "hello", "is_final": True}
    assert finished == {"type": "flush_done", "done": True}
    assert manual.socket.closed is True


class _RecoverableSttConnection:
    def __init__(self, *, fail_finish: bool = False) -> None:
        self.fail_finish = fail_finish
        self.audio: list[bytes] = []
        self.closed = False
        self.events = [
            {"type": "transcript", "text": "recovered", "is_final": True},
            {"type": "flush_done", "done": True},
        ]

    async def send(self, data: bytes) -> None:
        self.audio.append(data)

    async def receive(self) -> dict[str, Any] | None:
        return self.events.pop(0) if self.events else None

    async def finish(self) -> None:
        if self.fail_finish:
            raise RuntimeError("transient STT disconnect")

    async def close(self) -> None:
        self.closed = True


class _RecoverableSttFactory:
    def __init__(self) -> None:
        self.connections = [
            _RecoverableSttConnection(fail_finish=True),
            _RecoverableSttConnection(),
        ]
        self.opened: list[_RecoverableSttConnection] = []

    async def open_stt(self, **_: str) -> _RecoverableSttConnection:
        connection = self.connections.pop(0)
        self.opened.append(connection)
        return connection


@pytest.mark.asyncio
async def test_streaming_stt_reconnects_replays_audio_and_finishes() -> None:
    factory = _RecoverableSttFactory()
    session = CartesiaStreamingSpeechToTextSession(
        factory, retry_delay_seconds=0
    )
    audio = b"\x7f" * 160

    await session.start(language_tag="en-US")
    await session.send(AudioFrame(audio, 0, 8000, 1, AudioEncoding.MULAW))
    events = await session.finish()

    assert len(factory.opened) == 2
    assert factory.opened[0].closed is True
    assert factory.opened[1].audio == [audio]
    assert [event.text for event in events if hasattr(event, "text")] == ["recovered"]


@pytest.mark.asyncio
async def test_streaming_stt_replays_audio_as_original_frames() -> None:
    factory = _RecoverableSttFactory()
    session = CartesiaStreamingSpeechToTextSession(factory, retry_delay_seconds=0)
    frames = [b"\x7f" * 160, b"\x80" * 160, b"\x81" * 160]

    await session.start(language_tag="en-US")
    for index, audio in enumerate(frames):
        await session.send(AudioFrame(audio, index * 20, 8000, 1, AudioEncoding.MULAW))
    await session.finish()

    assert factory.opened[1].audio == frames
