from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True, slots=True)
class ChatSession:
    id: str
    tenant_id: str
    user_id: str | None
    chatbot_id: str
    knowledge_base_id: str
    locale: str
    channel: str = "EMPLOYEE_PLAYGROUND"
    external_user_id: str | None = None
    customer_name: str | None = None
    customer_email: str | None = None
    integration_token_id: str | None = None


@dataclass(frozen=True, slots=True)
class RetrievedChunk:
    document_id: str
    source_name: str
    page_number: int | None
    chunk_index: int
    text: str
    score: float
    unit_id: str | None = None
    modality: str | None = None
    section_path: tuple[str, ...] = ()
    block_type: str | None = None
    sheet_name: str | None = None
    cell_range: str | None = None
    table_id: str | None = None


@dataclass(frozen=True, slots=True)
class Citation:
    id: str
    document_id: str
    source_name: str
    page_number: int | None
    chunk_index: int
    score: float
    snippet: str
    unit_id: str | None = None
    modality: str | None = None
    section_path: tuple[str, ...] = ()
    block_type: str | None = None
    sheet_name: str | None = None
    cell_range: str | None = None
    table_id: str | None = None


@dataclass(frozen=True, slots=True)
class AssistantMessage:
    role: str
    content: str
    citations: list[Citation] = field(default_factory=list)
    action: dict[str, Any] | None = None


@dataclass(frozen=True, slots=True)
class ChatMessage:
    role: str
    content: str
    citations: list[Citation] = field(default_factory=list)
    sequence_number: int | None = None
    action: dict[str, Any] | None = None
