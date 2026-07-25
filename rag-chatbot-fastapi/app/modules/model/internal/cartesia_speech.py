from __future__ import annotations

import time
from collections.abc import AsyncIterator, Callable
from typing import Any, Protocol, cast

from app.modules.model.api import (
    AudioEncoding,
    AudioFrame,
    ModelUnavailableError,
    SpeechActivityEvent,
    SpeechActivityKind,
    StreamingSpeechToTextSessionApi,
    StreamingTextToSpeechApi,
    TranscriptEvent,
)

CARTESIA_STT_MODEL = "ink-whisper-2025-06-04"
CARTESIA_TTS_MODEL = "sonic-3.5-2026-05-04"
CARTESIA_API_VERSION = "2026-03-01"
CARTESIA_ENCODING = "pcm_mulaw"
CARTESIA_SAMPLE_RATE = 8000


class CartesiaSttConnection(Protocol):
    async def send(self, data: bytes) -> None: ...

    async def receive(self) -> dict[str, Any] | None: ...

    async def finish(self) -> None: ...

    async def close(self) -> None: ...


class CartesiaSpeechSocketFactory(Protocol):
    async def open_stt(
        self, *, model: str, language: str, api_version: str
    ) -> CartesiaSttConnection: ...

    def synthesize(
        self,
        *,
        text: str,
        model: str,
        language: str,
        voice_id: str,
        api_version: str,
    ) -> AsyncIterator[bytes]: ...


class SdkCartesiaSpeechSocketFactory:
    def __init__(self, api_key: str) -> None:
        try:
            from cartesia import AsyncCartesia
        except ImportError as exception:  # pragma: no cover - deployment dependency
            raise ModelUnavailableError("Cartesia SDK is unavailable") from exception
        self._client: Any = AsyncCartesia(api_key=api_key)

    async def open_stt(
        self, *, model: str, language: str, api_version: str
    ) -> CartesiaSttConnection:
        try:
            socket = await self._client.stt.websocket(
                model=model,
                language=language,
                encoding=CARTESIA_ENCODING,
                sample_rate=CARTESIA_SAMPLE_RATE,
                api_version=api_version,
            )
            return cast(CartesiaSttConnection, _SdkSttConnection(socket))
        except Exception as exception:
            raise ModelUnavailableError("Cartesia STT connection failed") from exception

    async def _tts_chunks(
        self,
        *,
        text: str,
        model: str,
        language: str,
        voice_id: str,
        api_version: str,
    ) -> AsyncIterator[bytes]:
        try:
            socket = await self._client.tts.websocket(api_version=api_version)
            stream = socket.send(
                model_id=model,
                transcript=text,
                language=language,
                voice={"mode": "id", "id": voice_id},
                output_format={
                    "container": "raw",
                    "encoding": CARTESIA_ENCODING,
                    "sample_rate": CARTESIA_SAMPLE_RATE,
                },
            )
            async for item in stream:
                audio = item if isinstance(item, bytes) else getattr(item, "audio", None)
                if audio:
                    yield bytes(audio)
            await socket.close()
        except Exception as exception:
            raise ModelUnavailableError("Cartesia TTS stream failed") from exception

    def synthesize(
        self,
        *,
        text: str,
        model: str,
        language: str,
        voice_id: str,
        api_version: str,
    ) -> AsyncIterator[bytes]:
        return self._tts_chunks(
            text=text,
            model=model,
            language=language,
            voice_id=voice_id,
            api_version=api_version,
        )


class _SdkSttConnection:
    def __init__(self, socket: Any) -> None:
        self._socket = socket

    async def send(self, data: bytes) -> None:
        await self._socket.send(data)

    async def receive(self) -> dict[str, Any] | None:
        value = await self._socket.receive()
        if value is None or isinstance(value, dict):
            return value
        if hasattr(value, "model_dump"):
            return cast(dict[str, Any], value.model_dump())
        return cast(dict[str, Any], value)

    async def finish(self) -> None:
        await self._socket.finalize()

    async def close(self) -> None:
        await self._socket.close()


class CartesiaStreamingSpeechToTextSession(StreamingSpeechToTextSessionApi):
    def __init__(
        self,
        factory: CartesiaSpeechSocketFactory,
        *,
        now_ms: Callable[[], int] = lambda: int(time.monotonic() * 1000),
    ) -> None:
        self._factory = factory
        self._now_ms = now_ms
        self._connection: CartesiaSttConnection | None = None
        self._language_tag = ""

    async def start(self, *, language_tag: str) -> None:
        self._language_tag = language_tag
        self._connection = await self._factory.open_stt(
            model=CARTESIA_STT_MODEL,
            language=_language(language_tag),
            api_version=CARTESIA_API_VERSION,
        )

    async def send(self, frame: AudioFrame) -> tuple[TranscriptEvent | SpeechActivityEvent, ...]:
        _validate_frame(frame)
        connection = self._require_connection()
        await connection.send(frame.data)
        return await self._receive_available(connection)

    async def finish(self) -> tuple[TranscriptEvent | SpeechActivityEvent, ...]:
        connection = self._require_connection()
        await connection.finish()
        return await self._receive_available(connection, final=True)

    async def close(self) -> None:
        if self._connection is not None:
            await self._connection.close()
            self._connection = None

    async def _receive_available(
        self, connection: CartesiaSttConnection, *, final: bool = False
    ) -> tuple[TranscriptEvent | SpeechActivityEvent, ...]:
        events: list[TranscriptEvent | SpeechActivityEvent] = []
        if not final:
            return ()
        while True:
            value = await connection.receive()
            if value is None:
                break
            event_type = str(value.get("type", ""))
            if event_type in {"speech_started", "speech_ended"}:
                events.append(
                    SpeechActivityEvent(
                        SpeechActivityKind(event_type),
                        int(value.get("timestamp_ms", self._now_ms())),
                    )
                )
            text = str(value.get("text", "")).strip()
            if text:
                events.append(
                    TranscriptEvent(
                        text=text,
                        language_tag=self._language_tag,
                        start_ms=int(value.get("start_ms", 0)),
                        end_ms=int(value.get("end_ms", self._now_ms())),
                        is_final=bool(value.get("is_final", final)),
                        confidence=float(value["confidence"])
                        if value.get("confidence") is not None
                        else None,
                    )
                )
            if value.get("done") is True:
                break
        return tuple(events)

    def _require_connection(self) -> CartesiaSttConnection:
        if self._connection is None:
            raise ModelUnavailableError("Cartesia STT session was not started")
        return self._connection


class CartesiaStreamingTextToSpeech(StreamingTextToSpeechApi):
    def __init__(
        self,
        factory: CartesiaSpeechSocketFactory,
        *,
        english_voice_id: str,
        vietnamese_voice_id: str,
    ) -> None:
        self._factory = factory
        self._voices = {"en-US": english_voice_id, "vi-VN": vietnamese_voice_id}

    async def synthesize(self, text: str, *, language_tag: str) -> AsyncIterator[AudioFrame]:
        voice_id = self._voices.get(language_tag)
        if not voice_id:
            raise ModelUnavailableError("Cartesia voice is not configured")
        timestamp = 0
        async for data in self._factory.synthesize(
            text=text,
            model=CARTESIA_TTS_MODEL,
            language=_language(language_tag),
            voice_id=voice_id,
            api_version=CARTESIA_API_VERSION,
        ):
            if not data:
                continue
            yield AudioFrame(
                data=data,
                timestamp_ms=timestamp,
                sample_rate_hz=CARTESIA_SAMPLE_RATE,
                channels=1,
                encoding=AudioEncoding.MULAW,
            )
            timestamp += len(data) // 8


def _validate_frame(frame: AudioFrame) -> None:
    if (
        frame.encoding is not AudioEncoding.MULAW
        or frame.sample_rate_hz != CARTESIA_SAMPLE_RATE
        or frame.channels != 1
    ):
        raise ValueError("Cartesia requires raw 8-kHz mono mu-law audio")


def _language(language_tag: str) -> str:
    try:
        return {"en-US": "en", "vi-VN": "vi"}[language_tag]
    except KeyError as exception:
        raise ValueError("Unsupported Cartesia interview language") from exception
