from __future__ import annotations

import unicodedata
from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass
from enum import StrEnum
from typing import Protocol

EMBEDDING_NORMALIZATION_VERSION = 1


def normalize_embedding_text(text: str) -> str:
    return unicodedata.normalize("NFC", text.replace("\r\n", "\n").replace("\r", "\n"))


class ModelRole(StrEnum):
    SYSTEM = "system"
    USER = "user"
    ASSISTANT = "assistant"


@dataclass(frozen=True, slots=True)
class ModelMessage:
    role: ModelRole
    content: str


@dataclass(frozen=True, slots=True)
class ModelCompletion:
    content: str
    input_tokens: int | None = None
    output_tokens: int | None = None


@dataclass(frozen=True, slots=True)
class SparseEmbedding:
    indices: tuple[int, ...]
    values: tuple[float, ...]


@dataclass(frozen=True, slots=True)
class EmbeddingNormalization:
    version: str
    normalized_text: str


class ModelError(Exception):
    """Base error owned by the model capability."""


class ModelTimeoutError(ModelError):
    pass


class ModelRejectedError(ModelError):
    pass


class ModelUnavailableError(ModelError):
    pass


class ChatModelApi(Protocol):
    provider: str
    model: str

    async def complete(self, messages: Sequence[dict[str, object]]) -> str: ...

    async def complete_with_usage(
        self, messages: Sequence[dict[str, object]]
    ) -> ModelCompletion: ...


class TextEmbeddingApi(Protocol):
    async def embed_documents(self, texts: Sequence[str]) -> list[list[float]]: ...

    async def embed_query(self, text: str) -> list[float]: ...


class SparseEmbeddingApi(Protocol):
    async def embed_documents(self, texts: Sequence[str]) -> list[SparseEmbedding]: ...

    async def embed_query(self, text: str) -> SparseEmbedding: ...


class AudioEncoding(StrEnum):
    PCM_S16LE = "pcm_s16le"
    MULAW = "mulaw"


@dataclass(frozen=True, slots=True)
class AudioFrame:
    data: bytes
    timestamp_ms: int
    sample_rate_hz: int
    channels: int
    encoding: AudioEncoding


@dataclass(frozen=True, slots=True)
class TranscriptEvent:
    text: str
    language_tag: str
    start_ms: int
    end_ms: int
    is_final: bool
    confidence: float | None = None


@dataclass(frozen=True, slots=True)
class TurnEvent:
    turn_id: str
    start_ms: int
    end_ms: int
    language_tag: str
    transcript: str
    is_final: bool


class SpeechActivityKind(StrEnum):
    SPEECH_STARTED = "speech_started"
    SPEECH_ENDED = "speech_ended"


@dataclass(frozen=True, slots=True)
class SpeechActivityEvent:
    kind: SpeechActivityKind
    timestamp_ms: int


class StreamingSpeechToTextSessionApi(Protocol):
    async def start(self, *, language_tag: str) -> None: ...

    async def send(
        self, frame: AudioFrame
    ) -> tuple[TranscriptEvent | SpeechActivityEvent, ...]: ...

    async def finish(self) -> tuple[TranscriptEvent | SpeechActivityEvent, ...]: ...

    async def close(self) -> None: ...


class StreamingSpeechToTextApi(Protocol):
    def transcribe(
        self, frames: AsyncIterator[AudioFrame], *, language_tag: str
    ) -> AsyncIterator[TranscriptEvent]: ...


class StreamingTextToSpeechApi(Protocol):
    def synthesize(self, text: str, *, language_tag: str) -> AsyncIterator[AudioFrame]: ...


class TurnDetectionApi(Protocol):
    def detect(
        self,
        frames: AsyncIterator[AudioFrame],
        transcripts: AsyncIterator[TranscriptEvent],
        *,
        language_tag: str,
    ) -> AsyncIterator[TurnEvent]: ...


__all__ = [
    "ChatModelApi",
    "AudioEncoding",
    "AudioFrame",
    "EmbeddingNormalization",
    "EMBEDDING_NORMALIZATION_VERSION",
    "ModelCompletion",
    "ModelError",
    "ModelMessage",
    "ModelRejectedError",
    "ModelRole",
    "ModelTimeoutError",
    "ModelUnavailableError",
    "SparseEmbedding",
    "SparseEmbeddingApi",
    "StreamingSpeechToTextApi",
    "StreamingSpeechToTextSessionApi",
    "StreamingTextToSpeechApi",
    "TextEmbeddingApi",
    "TranscriptEvent",
    "SpeechActivityEvent",
    "SpeechActivityKind",
    "TurnDetectionApi",
    "TurnEvent",
    "normalize_embedding_text",
]
