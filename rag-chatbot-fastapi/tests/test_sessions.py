from __future__ import annotations

from typing import Any

from app.rag.sessions import PostgresChatSessionStore


class FakeCursor:
    def __init__(self, row: dict[str, Any]) -> None:
        self.row = row
        self.query = ""
        self.params: tuple[object, ...] = ()

    def __enter__(self) -> FakeCursor:
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def execute(self, query: str, params: tuple[object, ...]) -> None:
        self.query = query
        self.params = params

    def fetchone(self) -> dict[str, Any]:
        return self.row


class FakeConnection:
    def __init__(self, cursor: FakeCursor) -> None:
        self.fake_cursor = cursor

    def __enter__(self) -> FakeConnection:
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def cursor(self, **kwargs: object) -> FakeCursor:
        del kwargs
        return self.fake_cursor


def test_postgres_session_lookup_loads_current_tenant_prompt() -> None:
    cursor = FakeCursor(
        {
            "id": "session-1",
            "tenant_id": "tenant-1",
            "user_id": None,
            "chatbot_id": "bot-1",
            "knowledge_base_id": "kb-1",
            "locale": "en",
            "channel": "WIDGET",
            "external_user_id": "visitor-1",
            "customer_name": None,
            "customer_email": None,
            "integration_token_id": "token-1",
            "customer_answer_prompt": "Use the current tenant prompt.",
        }
    )
    connection = FakeConnection(cursor)
    store = PostgresChatSessionStore("postgresql://unused")
    store._connect = lambda: connection  # type: ignore[method-assign]

    session = store.get_for_tenant("session-1", "tenant-1")

    assert session is not None
    assert session.customer_answer_prompt == "Use the current tenant prompt."
    assert "JOIN tenants t ON t.id = s.tenant_id" in cursor.query
    assert "t.customer_answer_prompt" in cursor.query
    assert cursor.params == ("session-1", "tenant-1")
