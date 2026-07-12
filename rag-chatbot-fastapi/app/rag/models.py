from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True, slots=True)
class ChatSession:
    id: str
    tenant_id: str
    user_id: str
    chatbot_id: str
    knowledge_base_id: str
    locale: str


@dataclass(frozen=True, slots=True)
class RetrievedChunk:
    document_id: str
    source_name: str
    page_number: int | None
    chunk_index: int
    text: str
    score: float


@dataclass(frozen=True, slots=True)
class Citation:
    id: str
    document_id: str
    source_name: str
    page_number: int | None
    chunk_index: int
    score: float
    snippet: str


@dataclass(frozen=True, slots=True)
class AssistantMessage:
    role: str
    content: str
    citations: list[Citation] = field(default_factory=list)

