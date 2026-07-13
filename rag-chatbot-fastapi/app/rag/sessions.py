from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime
from typing import Any, Protocol
from uuid import uuid4

from app.rag.errors import (
    ChatQuotaExceededError,
    ChatSessionStoreUnavailableError,
    ChatWorkspaceNotFoundError,
)
from app.rag.models import AssistantMessage, ChatMessage, ChatSession, Citation


class ChatSessionStore(Protocol):
    def create(
        self,
        *,
        tenant_id: str,
        user_id: str | None,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
        channel: str = "EMPLOYEE_PLAYGROUND",
        external_user_id: str | None = None,
        customer_name: str | None = None,
        customer_email: str | None = None,
        customer_metadata: dict[str, Any] | None = None,
        integration_token_id: str | None = None,
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

    def consume_message_quota(self, tenant_id: str) -> None: ...

    def list_playground_sessions(
        self, *, tenant_id: str, user_id: str, limit: int, offset: int
    ) -> list[dict[str, Any]]: ...

    def hide_playground_session(self, *, session_id: str, tenant_id: str, user_id: str) -> bool: ...

    def customer_visible_document_ids(
        self, *, tenant_id: str, knowledge_base_id: str
    ) -> list[str]: ...


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
    def __init__(self) -> None:
        self._sessions: dict[str, StoredSession] = {}
        self._hidden_sessions: set[str] = set()
        self.customer_document_ids: list[str] = []

    def create(
        self,
        *,
        tenant_id: str,
        user_id: str | None,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
        channel: str = "EMPLOYEE_PLAYGROUND",
        external_user_id: str | None = None,
        customer_name: str | None = None,
        customer_email: str | None = None,
        customer_metadata: dict[str, Any] | None = None,
        integration_token_id: str | None = None,
    ) -> ChatSession:
        del customer_metadata
        session = ChatSession(
            id=str(uuid4()),
            tenant_id=tenant_id,
            user_id=user_id,
            chatbot_id=chatbot_id,
            knowledge_base_id=knowledge_base_id,
            locale=locale,
            channel=channel,
            external_user_id=external_user_id,
            customer_name=customer_name,
            customer_email=customer_email,
            integration_token_id=integration_token_id,
        )
        self._sessions[session.id] = StoredSession(session=session)
        return session

    def get_for_tenant(self, session_id: str, tenant_id: str) -> ChatSession | None:
        stored = self._sessions.get(session_id)
        if (
            stored is None
            or stored.session.tenant_id != tenant_id
            or session_id in self._hidden_sessions
        ):
            return None
        return stored.session

    def add_user_message(self, session_id: str, content: str) -> None:
        self._sessions[session_id].messages.append(StoredMessage(role="user", content=content))

    def add_assistant_message(self, session_id: str, message: AssistantMessage) -> None:
        self._sessions[session_id].messages.append(
            StoredMessage(
                role=message.role,
                content=message.content,
                citations=message.citations,
                action=message.action,
            )
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
        if (
            stored is None
            or stored.session.tenant_id != tenant_id
            or session_id in self._hidden_sessions
        ):
            return []

        start = after or 0
        return [
            ChatMessage(
                role=item.role,
                content=item.content,
                citations=item.citations,
                sequence_number=index,
                action=item.action,
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

    def consume_message_quota(self, tenant_id: str) -> None:
        del tenant_id

    def list_playground_sessions(
        self, *, tenant_id: str, user_id: str, limit: int, offset: int
    ) -> list[dict[str, Any]]:
        rows = []
        for stored in self._sessions.values():
            session = stored.session
            if session.id in self._hidden_sessions:
                continue
            if (
                session.tenant_id != tenant_id
                or session.user_id != user_id
                or session.channel != "EMPLOYEE_PLAYGROUND"
            ):
                continue
            first = next(
                (
                    item.content.strip()
                    for item in stored.messages
                    if item.role == "user" and item.content.strip()
                ),
                "",
            )
            rows.append({
                "id": session.id,
                "title": first[:60] or datetime.now(UTC).strftime("%b %d, %Y"),
                "message_count": len(stored.messages),
                "status": "OPEN",
                "created_at": datetime.now(UTC),
                "last_activity_at": datetime.now(UTC),
            })
        return rows[offset:offset + min(max(limit, 1), 100)]

    def hide_playground_session(self, *, session_id: str, tenant_id: str, user_id: str) -> bool:
        stored = self._sessions.get(session_id)
        if (
            stored is None
            or stored.session.tenant_id != tenant_id
            or stored.session.user_id != user_id
        ):
            return False
        self._hidden_sessions.add(session_id)
        return True

    def customer_visible_document_ids(self, *, tenant_id: str, knowledge_base_id: str) -> list[str]:
        del tenant_id, knowledge_base_id
        return list(self.customer_document_ids)


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
        user_id: str | None,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
        channel: str = "EMPLOYEE_PLAYGROUND",
        external_user_id: str | None = None,
        customer_name: str | None = None,
        customer_email: str | None = None,
        customer_metadata: dict[str, Any] | None = None,
        integration_token_id: str | None = None,
    ) -> ChatSession:
        session_id = str(uuid4())
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO chat_sessions (
                        id, tenant_id, user_id, chatbot_id, knowledge_base_id, locale, status,
                        channel, external_user_id, customer_name, customer_email,
                        customer_metadata, integration_token_id, last_activity_at
                    )
                    SELECT %s, %s, %s, c.id, kb.id, %s, 'OPEN', %s, %s, %s, %s, %s, %s, NOW()
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
                        channel,
                        external_user_id,
                        customer_name,
                        customer_email,
                        self._jsonb(customer_metadata or {}),
                        integration_token_id,
                        knowledge_base_id,
                        tenant_id,
                        chatbot_id,
                        tenant_id,
                    ),
                )
                inserted = cur.rowcount
                if inserted and channel != "EMPLOYEE_PLAYGROUND":
                    self._insert_outbox_event(
                        cur,
                        tenant_id=tenant_id,
                        event_type="conversation.started",
                        aggregate_id=session_id,
                        payload={
                            "conversationId": session_id,
                            "chatbotId": chatbot_id,
                            "channel": channel,
                            "externalUserId": external_user_id,
                        },
                    )
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
            channel=channel,
            external_user_id=external_user_id,
            customer_name=customer_name,
            customer_email=customer_email,
            integration_token_id=integration_token_id,
        )

    def get_for_tenant(self, session_id: str, tenant_id: str) -> ChatSession | None:
        with self._connect() as conn:
            with conn.cursor(row_factory=self._dict_row) as cur:
                cur.execute(
                    """
                    SELECT id, tenant_id, user_id, chatbot_id, knowledge_base_id, locale,
                           channel, external_user_id, customer_name, customer_email,
                           integration_token_id
                    FROM chat_sessions
                    WHERE id = %s
                      AND tenant_id = %s
                      AND status = 'OPEN'
                      AND hidden_at IS NULL
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
            action=message.action,
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
                      AND hidden_at IS NULL
                    """,
                    (session_id, tenant_id),
                )
                if cur.fetchone() is None:
                    return []

                cur.execute(
                    """
                    SELECT role, content, citations, sequence_number, action
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

    def list_playground_sessions(
        self, *, tenant_id: str, user_id: str, limit: int, offset: int
    ) -> list[dict[str, Any]]:
        with self._connect() as conn:
            with conn.cursor(row_factory=self._dict_row) as cur:
                cur.execute(
                    """
                    SELECT s.id,
                           COALESCE(NULLIF(LEFT(first_message.content, 60), ''),
                                    TO_CHAR(s.created_at, 'Mon DD, YYYY')) AS title,
                           COUNT(m.id) AS message_count,
                           s.status, s.created_at, s.last_activity_at
                    FROM chat_sessions s
                    LEFT JOIN LATERAL (
                        SELECT BTRIM(content) AS content
                        FROM chat_messages
                        WHERE session_id = s.id AND role = 'user'
                        ORDER BY sequence_number ASC
                        LIMIT 1
                    ) first_message ON TRUE
                    LEFT JOIN chat_messages m ON m.session_id = s.id
                    WHERE s.tenant_id = %s
                      AND s.user_id = %s
                      AND s.channel = 'EMPLOYEE_PLAYGROUND'
                      AND s.hidden_at IS NULL
                    GROUP BY s.id, first_message.content
                    ORDER BY s.last_activity_at DESC, s.created_at DESC
                    LIMIT %s OFFSET %s
                    """,
                    (tenant_id, user_id, min(max(limit, 1), 100), max(offset, 0)),
                )
                return [dict(row) for row in cur.fetchall()]

    def hide_playground_session(self, *, session_id: str, tenant_id: str, user_id: str) -> bool:
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    UPDATE chat_sessions
                    SET hidden_at = NOW(), status = 'CLOSED',
                        closed_at = COALESCE(closed_at, NOW()),
                        updated_at = NOW()
                    WHERE id = %s AND tenant_id = %s AND user_id = %s
                      AND channel = 'EMPLOYEE_PLAYGROUND' AND hidden_at IS NULL
                    """,
                    (session_id, tenant_id, user_id),
                )
                updated = cur.rowcount
            conn.commit()
        return updated > 0

    def customer_visible_document_ids(self, *, tenant_id: str, knowledge_base_id: str) -> list[str]:
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id FROM documents
                    WHERE tenant_id = %s AND knowledge_base_id = %s
                      AND status = 'COMPLETED'
                      AND visibility = 'CUSTOMER_AND_EMPLOYEE'
                    """,
                    (tenant_id, knowledge_base_id),
                )
                return [str(row[0]) for row in cur.fetchall()]

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
                    RETURNING channel, chatbot_id, external_user_id
                    """,
                    (
                        datetime.now(UTC).replace(tzinfo=None),
                        datetime.now(UTC).replace(tzinfo=None),
                        session_id,
                        tenant_id,
                    ),
                )
                updated = cur.rowcount
                row = cur.fetchone() if updated else None
                if row is not None and row[0] != "EMPLOYEE_PLAYGROUND":
                    self._insert_outbox_event(
                        cur,
                        tenant_id=tenant_id,
                        event_type="conversation.closed",
                        aggregate_id=session_id,
                        payload={
                            "conversationId": session_id,
                            "chatbotId": str(row[1]),
                            "channel": row[0],
                            "externalUserId": row[2],
                        },
                    )
            conn.commit()
        return updated > 0

    def consume_message_quota(self, tenant_id: str) -> None:
        now = datetime.now(UTC).replace(tzinfo=None)
        with self._connect() as conn:
            with conn.cursor(row_factory=self._dict_row) as cur:
                cur.execute(
                    "SELECT max_messages FROM tenants WHERE id = %s FOR UPDATE",
                    (tenant_id,),
                )
                tenant = cur.fetchone()
                if tenant is None:
                    raise ChatWorkspaceNotFoundError("Tenant was not found")
                cur.execute(
                    """
                    SELECT message_count
                    FROM usage_metrics
                    WHERE tenant_id = %s AND period_year = %s AND period_month = %s
                    """,
                    (tenant_id, now.year, now.month),
                )
                usage = cur.fetchone()
                current = 0 if usage is None else int(usage["message_count"])
                if current >= int(tenant["max_messages"]):
                    raise ChatQuotaExceededError("Tenant message quota exceeded")
                cur.execute(
                    """
                    INSERT INTO usage_metrics (
                        tenant_id, period_year, period_month, message_count,
                        document_count, storage_mb_used, token_count
                    ) VALUES (%s, %s, %s, 1, 0, 0, 0)
                    ON CONFLICT (tenant_id, period_year, period_month)
                    DO UPDATE SET message_count = usage_metrics.message_count + 1,
                                  updated_at = NOW()
                    """,
                    (tenant_id, now.year, now.month),
                )
            conn.commit()

    def list_external_conversations(
        self,
        *,
        tenant_id: str,
        status: str | None,
        limit: int,
        offset: int,
    ) -> list[dict[str, Any]]:
        conditions = ["s.tenant_id = %s", "s.channel IN ('WIDGET', 'CUSTOM_API')"]
        params: list[Any] = [tenant_id]
        if status:
            conditions.append("s.status = %s")
            params.append(status)
        params.extend([min(max(limit, 1), 100), max(offset, 0)])
        with self._connect() as conn:
            with conn.cursor(row_factory=self._dict_row) as cur:
                cur.execute(
                    f"""
                    SELECT s.id, s.channel, s.external_user_id, s.customer_name,
                           s.customer_email, s.status, s.created_at, s.updated_at, s.closed_at,
                           COUNT(m.id) AS message_count
                    FROM chat_sessions s
                    LEFT JOIN chat_messages m ON m.session_id = s.id
                    WHERE {' AND '.join(conditions)}
                    GROUP BY s.id
                    ORDER BY s.created_at DESC
                    LIMIT %s OFFSET %s
                    """,
                    tuple(params),
                )
                return [dict(row) for row in cur.fetchall()]

    def get_external_conversation(
        self, *, tenant_id: str, session_id: str
    ) -> tuple[dict[str, Any], list[ChatMessage]] | None:
        with self._connect() as conn:
            with conn.cursor(row_factory=self._dict_row) as cur:
                cur.execute(
                    """
                    SELECT id, channel, external_user_id, customer_name, customer_email,
                           customer_metadata, status, created_at, updated_at, closed_at
                    FROM chat_sessions
                    WHERE id = %s AND tenant_id = %s
                      AND channel IN ('WIDGET', 'CUSTOM_API')
                    """,
                    (session_id, tenant_id),
                )
                conversation = cur.fetchone()
                if conversation is None:
                    return None
                cur.execute(
                    """
                    SELECT role, content, citations, sequence_number, action
                    FROM chat_messages WHERE session_id = %s ORDER BY sequence_number
                    """,
                    (session_id,),
                )
                messages = [self._message_from_row(row) for row in cur.fetchall()]
                return dict(conversation), messages

    def close_idle_external(self, idle_minutes: int = 30) -> int:
        with self._connect() as conn:
            with conn.cursor(row_factory=self._dict_row) as cur:
                cur.execute(
                    """
                    UPDATE chat_sessions
                    SET status = 'CLOSED', closed_at = NOW(), updated_at = NOW()
                    WHERE status = 'OPEN'
                      AND channel IN ('WIDGET', 'CUSTOM_API')
                      AND last_activity_at < NOW() - (%s * INTERVAL '1 minute')
                    RETURNING id, tenant_id, chatbot_id, channel, external_user_id
                    """,
                    (idle_minutes,),
                )
                rows = cur.fetchall()
                for row in rows:
                    self._insert_outbox_event(
                        cur,
                        tenant_id=str(row["tenant_id"]),
                        event_type="conversation.closed",
                        aggregate_id=str(row["id"]),
                        payload={
                            "conversationId": str(row["id"]),
                            "chatbotId": str(row["chatbot_id"]),
                            "channel": row["channel"],
                            "externalUserId": row["external_user_id"],
                            "reason": "idle_timeout",
                        },
                    )
            conn.commit()
        return len(rows)

    def _insert_message(
        self,
        *,
        session_id: str,
        role: str,
        content: str,
        citations: list[dict[str, Any]],
        action: dict[str, Any] | None = None,
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
                        session_id, tenant_id, user_id, role, content, citations,
                        sequence_number, action
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        session_id,
                        session["tenant_id"],
                        session["user_id"],
                        role,
                        content,
                        self._jsonb(citations),
                        next_sequence,
                        self._jsonb(action or {}),
                    ),
                )
                cur.execute(
                    "UPDATE chat_sessions SET updated_at = %s, last_activity_at = %s WHERE id = %s",
                    (
                        datetime.now(UTC).replace(tzinfo=None),
                        datetime.now(UTC).replace(tzinfo=None),
                        session_id,
                    ),
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
            user_id=str(row["user_id"]) if row["user_id"] is not None else None,
            chatbot_id=str(row["chatbot_id"]),
            knowledge_base_id=str(row["knowledge_base_id"]),
            locale=str(row["locale"]),
            channel=str(row["channel"]),
            external_user_id=row["external_user_id"],
            customer_name=row["customer_name"],
            customer_email=row["customer_email"],
            integration_token_id=(
                str(row["integration_token_id"])
                if row["integration_token_id"] is not None
                else None
            ),
        )

    def _message_from_row(self, row: dict[str, Any]) -> ChatMessage:
        return ChatMessage(
            role=str(row["role"]),
            content=str(row["content"]),
            citations=[Citation(**citation) for citation in row["citations"]],
            sequence_number=int(row["sequence_number"]),
            action=dict(row["action"]) if row["action"] else None,
        )

    def _insert_outbox_event(
        self,
        cursor: Any,
        *,
        tenant_id: str,
        event_type: str,
        aggregate_id: str,
        payload: dict[str, Any],
    ) -> None:
        cursor.execute(
            """
            INSERT INTO webhook_outbox (
                tenant_id, event_type, aggregate_id, payload, status, next_attempt_at
            ) VALUES (%s, %s, %s, %s, 'PENDING', NOW())
            """,
            (tenant_id, event_type, aggregate_id, self._jsonb(payload)),
        )
