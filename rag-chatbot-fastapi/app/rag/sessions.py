from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import UTC, datetime
from typing import Any, Protocol
from uuid import uuid4

from app.rag.models import AssistantMessage, ChatMessage, ChatSession, Citation
from app.rag.prompts import default_customer_answer_prompt


class ChatSessionStore(Protocol):
    def create(self, **kwargs: Any) -> ChatSession: ...

    def get_for_tenant(self, session_id: str, tenant_id: str) -> ChatSession | None: ...

    def add_user_message(self, session_id: str, content: str) -> None: ...

    def add_assistant_message(self, session_id: str, message: AssistantMessage) -> None: ...

    def list_messages(
        self,
        *,
        session_id: str,
        tenant_id: str,
        limit: int = 50,
        after: int | None = None,
    ) -> list[ChatMessage]: ...

    def consume_message_quota(self, tenant_id: str) -> None: ...

    def customer_visible_document_ids(
        self, *, tenant_id: str, knowledge_base_id: str
    ) -> list[str]: ...

    def close_for_tenant(self, session_id: str, tenant_id: str) -> bool: ...

    def list_playground_sessions(self, **kwargs: Any) -> list[dict[str, Any]]: ...

    def hide_playground_session(self, **kwargs: Any) -> bool: ...


@dataclass(slots=True)
class StoredMessage:
    role: str
    content: str
    citations: list[Citation] = field(default_factory=list)
    action: dict[str, Any] | None = None


@dataclass(slots=True)
class StoredSession:
    session: ChatSession
    messages: list[StoredMessage] = field(default_factory=list)


class InMemoryChatSessionStore:
    """Test-only session store; production generation uses GenerationChatSessionStore."""

    def __init__(self) -> None:
        self._sessions: dict[str, StoredSession] = {}
        self._hidden_sessions: set[str] = set()
        self._customer_answer_prompts: dict[str, str] = {}
        self._tenant_names: dict[str, str] = {}
        self.customer_document_ids: list[str] = []

    def set_customer_answer_prompt(self, tenant_id: str, prompt: str) -> None:
        self._customer_answer_prompts[tenant_id] = prompt

    def set_tenant_name(self, tenant_id: str, tenant_name: str) -> None:
        self._tenant_names[tenant_id] = tenant_name

    def create(self, **kwargs: Any) -> ChatSession:
        tenant_id = str(kwargs["tenant_id"])
        tenant_name = self._tenant_names.get(tenant_id, tenant_id)
        session = ChatSession(
            id=str(uuid4()),
            tenant_id=tenant_id,
            user_id=kwargs.get("user_id"),
            chatbot_id=str(kwargs["chatbot_id"]),
            knowledge_base_id=str(kwargs["knowledge_base_id"]),
            locale=str(kwargs["locale"]),
            channel=str(kwargs.get("channel", "EMPLOYEE_PLAYGROUND")),
            external_user_id=kwargs.get("external_user_id"),
            customer_name=kwargs.get("customer_name"),
            customer_email=kwargs.get("customer_email"),
            integration_token_id=kwargs.get("integration_token_id"),
            customer_answer_prompt=self._customer_answer_prompts.get(
                tenant_id, default_customer_answer_prompt(tenant_name)
            ),
            tenant_name=tenant_name,
        )
        self._sessions[session.id] = StoredSession(session)
        return session

    def get_for_tenant(self, session_id: str, tenant_id: str) -> ChatSession | None:
        stored = self._sessions.get(session_id)
        if (
            stored is None
            or stored.session.tenant_id != tenant_id
            or session_id in self._hidden_sessions
        ):
            return None
        return replace(
            stored.session,
            tenant_name=self._tenant_names.get(tenant_id, stored.session.tenant_name),
            customer_answer_prompt=self._customer_answer_prompts.get(
                tenant_id, stored.session.customer_answer_prompt
            ),
        )

    def add_user_message(self, session_id: str, content: str) -> None:
        self._sessions[session_id].messages.append(StoredMessage("user", content))

    def add_assistant_message(self, session_id: str, message: AssistantMessage) -> None:
        self._sessions[session_id].messages.append(
            StoredMessage(message.role, message.content, message.citations, message.action)
        )

    def list_messages(
        self,
        *,
        session_id: str,
        tenant_id: str,
        limit: int = 50,
        after: int | None = None,
    ) -> list[ChatMessage]:
        stored = self._sessions.get(session_id)
        if stored is None or stored.session.tenant_id != tenant_id:
            return []
        return [
            ChatMessage(
                role=item.role,
                content=item.content,
                citations=item.citations,
                sequence_number=index,
                action=item.action,
            )
            for index, item in enumerate(stored.messages, start=1)
            if index > (after or 0)
        ][:limit]

    def consume_message_quota(self, tenant_id: str) -> None:
        del tenant_id

    def customer_visible_document_ids(
        self, *, tenant_id: str, knowledge_base_id: str
    ) -> list[str]:
        del tenant_id, knowledge_base_id
        return list(self.customer_document_ids)

    def close_for_tenant(self, session_id: str, tenant_id: str) -> bool:
        stored = self._sessions.get(session_id)
        if stored is None or stored.session.tenant_id != tenant_id:
            return False
        del self._sessions[session_id]
        return True

    def list_playground_sessions(self, **kwargs: Any) -> list[dict[str, Any]]:
        tenant_id = str(kwargs["tenant_id"])
        user_id = str(kwargs["user_id"])
        rows = []
        for stored in self._sessions.values():
            session = stored.session
            if (
                session.tenant_id == tenant_id
                and session.user_id == user_id
                and session.channel == "EMPLOYEE_PLAYGROUND"
                and session.id not in self._hidden_sessions
            ):
                first = next(
                    (item.content for item in stored.messages if item.role == "user"), ""
                )
                rows.append(
                    {
                        "id": session.id,
                        "title": first[:60] or datetime.now(UTC).date().isoformat(),
                        "message_count": len(stored.messages),
                        "status": "OPEN",
                        "created_at": datetime.now(UTC),
                        "last_activity_at": datetime.now(UTC),
                    }
                )
        offset = int(kwargs.get("offset", 0))
        limit = int(kwargs.get("limit", 50))
        return rows[offset : offset + limit]

    def hide_playground_session(self, **kwargs: Any) -> bool:
        session_id = str(kwargs["session_id"])
        stored = self._sessions.get(session_id)
        if (
            stored is None
            or stored.session.tenant_id != str(kwargs["tenant_id"])
            or stored.session.user_id != str(kwargs["user_id"])
        ):
            return False
        self._hidden_sessions.add(session_id)
        return True


@dataclass(slots=True)
class GenerationChatSessionStore:
    """Generation-scoped adapter over context supplied authoritatively by Spring."""

    session: ChatSession
    prior_messages: list[ChatMessage]
    visible_document_ids: list[str]

    def get_for_tenant(self, session_id: str, tenant_id: str) -> ChatSession | None:
        if self.session.id != session_id or self.session.tenant_id != tenant_id:
            return None
        return self.session

    def add_user_message(self, session_id: str, content: str) -> None:
        del session_id, content

    def add_assistant_message(self, session_id: str, message: AssistantMessage) -> None:
        del session_id, message

    def list_messages(
        self,
        *,
        session_id: str,
        tenant_id: str,
        limit: int = 50,
        after: int | None = None,
    ) -> list[ChatMessage]:
        if self.get_for_tenant(session_id, tenant_id) is None:
            return []
        start = max(after or 0, 0)
        return [
            message for index, message in enumerate(self.prior_messages, start=1) if index > start
        ][:limit]

    def consume_message_quota(self, tenant_id: str) -> None:
        del tenant_id

    def customer_visible_document_ids(self, *, tenant_id: str, knowledge_base_id: str) -> list[str]:
        if (
            tenant_id != self.session.tenant_id
            or knowledge_base_id != self.session.knowledge_base_id
        ):
            return []
        return list(self.visible_document_ids)

    def create(self, **kwargs: Any) -> ChatSession:
        del kwargs
        raise RuntimeError("Session creation is owned by Spring")

    def close_for_tenant(self, session_id: str, tenant_id: str) -> bool:
        del session_id, tenant_id
        raise RuntimeError("Session closure is owned by Spring")

    def list_playground_sessions(self, **kwargs: Any) -> list[dict[str, Any]]:
        del kwargs
        raise RuntimeError("Conversation history is owned by Spring")

    def hide_playground_session(self, **kwargs: Any) -> bool:
        del kwargs
        raise RuntimeError("Conversation history is owned by Spring")
