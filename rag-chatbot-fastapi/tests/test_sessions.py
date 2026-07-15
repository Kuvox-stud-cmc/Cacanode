from __future__ import annotations

from typing import Any
from datetime import datetime

from app.rag.sessions import PostgresChatSessionStore


def test_billing_anniversary_periods_are_monthly_for_annual_pro() -> None:
    store = PostgresChatSessionStore("postgresql://unused")
    start, end = store._billing_period(  # noqa: SLF001
        {
            "plan_code": "PRO",
            "status": "ACTIVE",
            "quota_anchor_at": datetime(2026, 1, 31, 10, 15),
            "trial_ends_at": None,
            "paid_through_at": datetime(2027, 1, 31, 10, 15),
        },
        datetime(2026, 3, 15, 12, 0),
    )

    assert start == datetime(2026, 2, 28, 10, 15)
    assert end == datetime(2026, 3, 28, 10, 15)


def test_grace_uses_final_paid_window_without_new_quota() -> None:
    store = PostgresChatSessionStore("postgresql://unused")
    start, end = store._billing_period(  # noqa: SLF001
        {
            "plan_code": "PRO",
            "status": "GRACE",
            "quota_anchor_at": datetime(2026, 1, 31, 10, 15),
            "trial_ends_at": None,
            "paid_through_at": datetime(2026, 4, 30, 10, 15),
        },
        datetime(2026, 5, 2, 9, 0),
    )

    assert start == datetime(2026, 4, 28, 10, 15)
    assert end == datetime(2026, 4, 30, 10, 15)


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


class FakeListCursor(FakeCursor):
    def __init__(self, rows: list[dict[str, Any]]) -> None:
        super().__init__({})
        self.rows = rows

    def fetchall(self) -> list[dict[str, Any]]:
        return self.rows


class MissingCursor(FakeCursor):
    def __init__(self) -> None:
        super().__init__({})

    def fetchone(self) -> None:
        return None


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


def test_external_conversation_list_filters_in_postgres_with_stable_paging() -> None:
    rows = [
        {
            "id": "conversation-2",
            "channel": "CUSTOM_API",
            "status": "CLOSED",
            "message_count": 4,
        }
    ]
    cursor = FakeListCursor(rows)
    store = PostgresChatSessionStore("postgresql://unused")
    store._connect = lambda: FakeConnection(cursor)  # type: ignore[method-assign]

    result = store.list_external_conversations(
        tenant_id="tenant-1",
        status="CLOSED",
        channel="CUSTOM_API",
        limit=25,
        offset=50,
    )

    assert result == rows
    assert "s.tenant_id = %s" in cursor.query
    assert "s.channel IN ('WIDGET', 'CUSTOM_API')" in cursor.query
    assert "s.status = %s" in cursor.query
    assert "s.channel = %s" in cursor.query
    assert "ORDER BY s.created_at DESC, s.id DESC" in cursor.query
    assert cursor.params == ("tenant-1", "CLOSED", "CUSTOM_API", 25, 50)


def test_external_conversation_store_clamps_defensive_paging_bounds() -> None:
    cursor = FakeListCursor([])
    store = PostgresChatSessionStore("postgresql://unused")
    store._connect = lambda: FakeConnection(cursor)  # type: ignore[method-assign]

    store.list_external_conversations(
        tenant_id="tenant-1",
        status=None,
        channel=None,
        limit=1000,
        offset=-10,
    )

    assert cursor.params == ("tenant-1", 100, 0)
    assert "s.status = %s" not in cursor.query
    assert "s.channel = %s" not in cursor.query


def test_external_conversation_detail_is_tenant_scoped() -> None:
    cursor = MissingCursor()
    store = PostgresChatSessionStore("postgresql://unused")
    store._connect = lambda: FakeConnection(cursor)  # type: ignore[method-assign]

    result = store.get_external_conversation(
        tenant_id="tenant-1",
        session_id="conversation-from-tenant-2",
    )

    assert result is None
    assert "id = %s AND tenant_id = %s" in cursor.query
    assert "channel IN ('WIDGET', 'CUSTOM_API')" in cursor.query
    assert cursor.params == ("conversation-from-tenant-2", "tenant-1")
