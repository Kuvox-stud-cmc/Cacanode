from __future__ import annotations

from dataclasses import dataclass, field
from uuid import uuid4

from app.rag.models import AssistantMessage, ChatSession


@dataclass(slots=True)
class StoredMessage:
    role: str
    content: str


@dataclass(slots=True)
class StoredSession:
    session: ChatSession
    messages: list[StoredMessage] = field(default_factory=list)


class InMemoryChatSessionStore:
    def __init__(self) -> None:
        self._sessions: dict[str, StoredSession] = {}

    def create(
        self,
        *,
        tenant_id: str,
        user_id: str,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
    ) -> ChatSession:
        session = ChatSession(
            id=str(uuid4()),
            tenant_id=tenant_id,
            user_id=user_id,
            chatbot_id=chatbot_id,
            knowledge_base_id=knowledge_base_id,
            locale=locale,
        )
        self._sessions[session.id] = StoredSession(session=session)
        return session

    def get_for_tenant(self, session_id: str, tenant_id: str) -> ChatSession | None:
        stored = self._sessions.get(session_id)
        if stored is None or stored.session.tenant_id != tenant_id:
            return None
        return stored.session

    def add_user_message(self, session_id: str, content: str) -> None:
        self._sessions[session_id].messages.append(StoredMessage(role="user", content=content))

    def add_assistant_message(self, session_id: str, message: AssistantMessage) -> None:
        self._sessions[session_id].messages.append(
            StoredMessage(role=message.role, content=message.content)
        )

