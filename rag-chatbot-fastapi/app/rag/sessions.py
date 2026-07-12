from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime
from typing import Any, Protocol
from uuid import uuid4

from app.rag.errors import ChatSessionStoreUnavailableError, ChatWorkspaceNotFoundError
from app.rag.models import AssistantMessage, ChatMessage, ChatSession, Citation


class ChatSessionStore(Protocol):
    def create(
        self,
        *,
        tenant_id: str,
        user_id: str,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
    ) -> ChatSession: ...

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

    def close_for_tenant(self, session_id: str, tenant_id: str) -> bool: ...


@dataclass(slots=True)
class StoredMessage:
    role: str
    content: str
    citations: list[Citation] = field(default_factory=list)


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
            StoredMessage(role=message.role, content=message.content, citations=message.citations)
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

        start = after or 0
        return [
            ChatMessage(
                role=item.role,
                content=item.content,
                citations=item.citations,
                sequence_number=index,
            )
            for index, item in enumerate(stored.messages, start=1)
            if index > start
        ][:limit]

    def close_for_tenant(self, session_id: str, tenant_id: str) -> bool:
        stored = self._sessions.get(session_id)
        if stored is None or stored.session.tenant_id != tenant_id:
            return False
        del self._sessions[session_id]
        return True


class PostgresChatSessionStore:
    def __init__(self, postgres_url: str) -> None:
        try:
            import psycopg
            from psycopg.rows import dict_row
            from psycopg.types.json import Jsonb
        except ImportError as exc:  # pragma: no cover - depends on production extras.
            raise RuntimeError(
                "psycopg is required for Postgres chat sessions. Install project dependencies."
            ) from exc

        self._postgres_url = postgres_url
        self._psycopg = psycopg
        self._dict_row = dict_row
        self._jsonb = Jsonb

    def create(
        self,
        *,
        tenant_id: str,
        user_id: str,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
    ) -> ChatSession:
        session_id = str(uuid4())
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO chat_sessions (
                        id, tenant_id, user_id, chatbot_id, knowledge_base_id, locale, status
                    )
                    SELECT %s, %s, %s, c.id, kb.id, %s, 'OPEN'
                    FROM chatbots c
                    JOIN knowledge_bases kb
                      ON kb.id = %s
                     AND kb.tenant_id = %s
                     AND kb.status = 'ACTIVE'
                    WHERE c.id = %s
                      AND c.tenant_id = %s
                      AND c.knowledge_base_id = kb.id
                      AND c.status = 'ACTIVE'
                    """,
                    (
                        session_id,
                        tenant_id,
                        user_id,
                        locale,
                        knowledge_base_id,
                        tenant_id,
                        chatbot_id,
                        tenant_id,
                    ),
                )
                inserted = cur.rowcount
            conn.commit()
        if inserted == 0:
            raise ChatWorkspaceNotFoundError("Chat workspace was not found")
        return ChatSession(
            id=session_id,
            tenant_id=tenant_id,
            user_id=user_id,
            chatbot_id=chatbot_id,
            knowledge_base_id=knowledge_base_id,
            locale=locale,
        )

    def get_for_tenant(self, session_id: str, tenant_id: str) -> ChatSession | None:
        with self._connect() as conn:
            with conn.cursor(row_factory=self._dict_row) as cur:
                cur.execute(
                    """
                    SELECT id, tenant_id, user_id, chatbot_id, knowledge_base_id, locale
                    FROM chat_sessions
                    WHERE id = %s
                      AND tenant_id = %s
                      AND status = 'OPEN'
                    """,
                    (session_id, tenant_id),
                )
                row = cur.fetchone()
        if row is None:
            return None
        return self._session_from_row(row)

    def add_user_message(self, session_id: str, content: str) -> None:
        self._insert_message(session_id=session_id, role="user", content=content, citations=[])

    def add_assistant_message(self, session_id: str, message: AssistantMessage) -> None:
        self._insert_message(
            session_id=session_id,
            role=message.role,
            content=message.content,
            citations=[asdict(citation) for citation in message.citations],
        )

    def list_messages(
        self,
        *,
        session_id: str,
        tenant_id: str,
        limit: int = 50,
        after: int | None = None,
    ) -> list[ChatMessage]:
        with self._connect() as conn:
            with conn.cursor(row_factory=self._dict_row) as cur:
                cur.execute(
                    """
                    SELECT 1
                    FROM chat_sessions
                    WHERE id = %s
                      AND tenant_id = %s
                      AND status = 'OPEN'
                    """,
                    (session_id, tenant_id),
                )
                if cur.fetchone() is None:
                    return []

                cur.execute(
                    """
                    SELECT role, content, citations, sequence_number
                    FROM chat_messages
                    WHERE session_id = %s
                      AND sequence_number > %s
                    ORDER BY sequence_number ASC
                    LIMIT %s
                    """,
                    (session_id, after or 0, min(max(limit, 1), 200)),
                )
                rows = cur.fetchall()
        return [self._message_from_row(row) for row in rows]

    def close_for_tenant(self, session_id: str, tenant_id: str) -> bool:
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    UPDATE chat_sessions
                    SET status = 'CLOSED',
                        closed_at = %s,
                        updated_at = %s
                    WHERE id = %s
                      AND tenant_id = %s
                      AND status = 'OPEN'
                    """,
                    (
                        datetime.now(UTC).replace(tzinfo=None),
                        datetime.now(UTC).replace(tzinfo=None),
                        session_id,
                        tenant_id,
                    ),
                )
                updated = cur.rowcount
            conn.commit()
        return updated > 0

    def _insert_message(
        self,
        *,
        session_id: str,
        role: str,
        content: str,
        citations: list[dict[str, Any]],
    ) -> None:
        with self._connect() as conn:
            with conn.cursor(row_factory=self._dict_row) as cur:
                cur.execute(
                    """
                    SELECT tenant_id, user_id
                    FROM chat_sessions
                    WHERE id = %s
                      AND status = 'OPEN'
                    FOR UPDATE
                    """,
                    (session_id,),
                )
                session = cur.fetchone()
                if session is None:
                    raise KeyError(session_id)

                cur.execute(
                    """
                    SELECT COALESCE(MAX(sequence_number), 0) + 1 AS next_sequence
                    FROM chat_messages
                    WHERE session_id = %s
                    """,
                    (session_id,),
                )
                next_sequence = cur.fetchone()["next_sequence"]
                cur.execute(
                    """
                    INSERT INTO chat_messages (
                        session_id, tenant_id, user_id, role, content, citations, sequence_number
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        session_id,
                        session["tenant_id"],
                        session["user_id"],
                        role,
                        content,
                        self._jsonb(citations),
                        next_sequence,
                    ),
                )
                cur.execute(
                    "UPDATE chat_sessions SET updated_at = %s WHERE id = %s",
                    (datetime.now(UTC).replace(tzinfo=None), session_id),
                )
            conn.commit()

    def _connect(self) -> Any:
        try:
            return self._psycopg.connect(self._postgres_url)
        except self._psycopg.OperationalError as exc:
            raise ChatSessionStoreUnavailableError("Chat session store is unavailable") from exc

    def _session_from_row(self, row: dict[str, Any]) -> ChatSession:
        return ChatSession(
            id=str(row["id"]),
            tenant_id=str(row["tenant_id"]),
            user_id=str(row["user_id"]),
            chatbot_id=str(row["chatbot_id"]),
            knowledge_base_id=str(row["knowledge_base_id"]),
            locale=str(row["locale"]),
        )

    def _message_from_row(self, row: dict[str, Any]) -> ChatMessage:
        return ChatMessage(
            role=str(row["role"]),
            content=str(row["content"]),
            citations=[Citation(**citation) for citation in row["citations"]],
            sequence_number=int(row["sequence_number"]),
        )
