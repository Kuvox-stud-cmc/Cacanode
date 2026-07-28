from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from app.modules.generation.api import Citation
from app.modules.generation.internal.prompts import DEFAULT_CUSTOMER_ANSWER_PROMPT
from app.modules.retrieval.api import RetrievedKnowledgeUnit

RetrievedChunk = RetrievedKnowledgeUnit


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
    customer_answer_prompt: str = DEFAULT_CUSTOMER_ANSWER_PROMPT
    tenant_name: str = ""
    authoritative_revision: int = 0


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
