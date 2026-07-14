from datetime import UTC, datetime, timedelta
from uuid import UUID

import jwt
from fastapi.testclient import TestClient

from app.api.v1.chat import get_chat_service
from app.core.config import settings
from app.main import app
from app.rag.errors import (
    ChatModelProviderError,
    ChatModelTimeoutError,
    ChatSessionStoreUnavailableError,
)
from app.rag.models import AssistantMessage, ChatMessage, ChatSession, Citation


def auth_headers(tenant_id: str = "tenant-1", user_id: str = "user-1") -> dict[str, str]:
    token = jwt.encode(
        {
            "sub": "admin@cacanode.local",
            "tenantId": tenant_id,
            "userId": user_id,
            "exp": datetime.now(UTC) + timedelta(minutes=15),
        },
        settings.TOKEN_KEY,
        algorithm="HS256",
    )
    return {"Authorization": f"Bearer {token}"}


class FakeChatService:
    def list_external_conversations(
        self,
        *,
        tenant_id: str,
        status: str | None,
        channel: str | None,
        limit: int,
        offset: int,
    ):
        self.listed_conversations = {
            "tenant_id": tenant_id,
            "status": status,
            "channel": channel,
            "limit": limit,
            "offset": offset,
        }
        now = datetime.now(UTC)
        return [
            {
                "id": "conversation-1",
                "channel": channel or "WIDGET",
                "external_user_id": "visitor-1",
                "customer_name": "Ada",
                "customer_email": "ada@example.com",
                "status": status or "OPEN",
                "message_count": 2,
                "created_at": now,
                "updated_at": now,
                "closed_at": None,
            }
        ]

    def get_external_conversation(self, *, tenant_id: str, session_id: str):
        self.loaded_conversation = {"tenant_id": tenant_id, "session_id": session_id}
        now = datetime.now(UTC)
        return (
            {
                "id": session_id,
                "channel": "CUSTOM_API",
                "external_user_id": "external-1",
                "customer_name": None,
                "customer_email": "customer@example.com",
                "customer_metadata": {"plan": "pro", "nested": {"ignored": True}},
                "status": "OPEN",
                "created_at": now,
                "updated_at": now,
                "closed_at": None,
            },
            [
                ChatMessage(role="system", content="System note", sequence_number=1),
                ChatMessage(
                    role="assistant",
                    content="Draft prepared [S1].",
                    citations=[
                        Citation(
                            id="S1",
                            document_id="doc-1",
                            source_name="policy.pdf",
                            page_number=2,
                            chunk_index=3,
                            score=0.9,
                            snippet="Relevant policy",
                        )
                    ],
                    sequence_number=2,
                    action={
                        "type": "ticket_draft",
                        "title": "Follow up",
                        "description": "Contact the customer.",
                    },
                ),
            ],
        )

    def close_session(
        self,
        *,
        tenant_id: str,
        session_id: str,
        integration_token_id: str | None = None,
        user_id: str | None = None,
    ) -> None:
        self.closed = {
            "tenant_id": tenant_id,
            "session_id": session_id,
            "integration_token_id": integration_token_id,
            "user_id": user_id,
        }

    def list_playground_sessions(self, *, tenant_id: str, user_id: str, limit: int, offset: int):
        self.listed = {"tenant_id": tenant_id, "user_id": user_id, "limit": limit, "offset": offset}
        now = datetime.now(UTC)
        return [
            {
                "id": UUID("be98a53d-ab96-4738-8e59-dd7da3975147"),
                "title": "First question",
                "message_count": 2,
                "status": "OPEN",
                "created_at": now,
                "last_activity_at": now,
            }
        ]

    def hide_playground_session(self, *, tenant_id: str, user_id: str, session_id: str) -> None:
        self.hidden = {"tenant_id": tenant_id, "user_id": user_id, "session_id": session_id}

    def create_session(
        self,
        *,
        tenant_id: str,
        user_id: str,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
    ) -> ChatSession:
        self.created = {
            "tenant_id": tenant_id,
            "user_id": user_id,
            "chatbot_id": chatbot_id,
            "knowledge_base_id": knowledge_base_id,
            "locale": locale,
        }
        return ChatSession(
            id="session-1",
            tenant_id=tenant_id,
            user_id=user_id,
            chatbot_id=chatbot_id,
            knowledge_base_id=knowledge_base_id,
            locale=locale,
        )

    async def submit_message(
        self,
        *,
        tenant_id: str,
        session_id: str,
        content: str,
        user_id: str | None = None,
    ) -> AssistantMessage:
        self.submitted = {
            "tenant_id": tenant_id,
            "session_id": session_id,
            "content": content,
            "user_id": user_id,
        }
        return AssistantMessage(
            role="assistant",
            content="Cau tra loi [S1].",
            citations=[
                Citation(
                    id="S1",
                    document_id="doc-1",
                    source_name="policy.txt",
                    page_number=1,
                    chunk_index=0,
                    score=0.82,
                    snippet="Cau tra loi.",
                )
            ],
        )


class TimeoutChatService(FakeChatService):
    async def submit_message(
        self,
        *,
        tenant_id: str,
        session_id: str,
        content: str,
        user_id: str | None = None,
    ) -> AssistantMessage:
        del tenant_id, session_id, content, user_id
        raise ChatModelTimeoutError("Model generation timed out")


class ProviderErrorChatService(FakeChatService):
    async def submit_message(
        self,
        *,
        tenant_id: str,
        session_id: str,
        content: str,
        user_id: str | None = None,
    ) -> AssistantMessage:
        del tenant_id, session_id, content, user_id
        raise ChatModelProviderError("Model provider request failed")


class UnavailableChatService(FakeChatService):
    def create_session(
        self,
        *,
        tenant_id: str,
        user_id: str,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
    ) -> ChatSession:
        del tenant_id, user_id, chatbot_id, knowledge_base_id, locale
        raise ChatSessionStoreUnavailableError("Postgres is unavailable")


def with_fake_service() -> FakeChatService:
    service = FakeChatService()
    app.dependency_overrides[get_chat_service] = lambda: service
    return service


def test_liveness_and_request_id() -> None:
    with TestClient(app) as client:
        response = client.get("/health/live")

    assert response.status_code == 200
    assert response.json()["status"] == "live"
    assert response.headers["X-Request-ID"].startswith("req_")


def test_readiness_reports_worker_scaffolds() -> None:
    with TestClient(app) as client:
        response = client.get("/health/ready")

    assert response.status_code == 200
    assert set(response.json()["components"]["workers"]) == {
        "document",
        "ocr",
        "asr",
        "vision",
        "audio",
        "video",
    }


def test_versioned_chat_requires_authentication() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/chat/sessions",
            json={
                "chatbot_id": "bot_test",
                "knowledge_base_id": "kb_test",
                "locale": "vi-VN",
            },
        )

    assert response.status_code in {401, 403}


def test_versioned_chat_session_creation_uses_jwt_tenant() -> None:
    service = with_fake_service()
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/chat/sessions",
                headers=auth_headers(tenant_id="tenant-123", user_id="user-123"),
                json={
                    "chatbot_id": "bot_test",
                    "knowledge_base_id": "kb_test",
                    "locale": "vi-VN",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.json() == {
        "id": "session-1",
        "chatbot_id": "bot_test",
        "knowledge_base_id": "kb_test",
        "tenant_id": "tenant-123",
        "locale": "vi-VN",
    }
    assert service.created["user_id"] == "user-123"


def test_chat_session_store_failure_returns_service_unavailable() -> None:
    service = UnavailableChatService()
    app.dependency_overrides[get_chat_service] = lambda: service
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/chat/sessions",
                headers=auth_headers(tenant_id="tenant-123", user_id="user-123"),
                json={
                    "chatbot_id": "bot_test",
                    "knowledge_base_id": "kb_test",
                    "locale": "vi-VN",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "CHAT_SESSION_STORE_UNAVAILABLE"


def test_playground_history_is_scoped_to_current_user_and_supports_pagination() -> None:
    service = with_fake_service()
    try:
        with TestClient(app) as client:
            response = client.get(
                "/api/v1/chat/playground/sessions?limit=25&offset=5",
                headers=auth_headers(tenant_id="tenant-123", user_id="employee-7"),
            )
        assert response.status_code == 200
        assert response.json()[0]["id"] == "be98a53d-ab96-4738-8e59-dd7da3975147"
        assert response.json()[0]["title"] == "First question"
        assert service.listed == {
            "tenant_id": "tenant-123", "user_id": "employee-7", "limit": 25, "offset": 5
        }
    finally:
        app.dependency_overrides.clear()


def test_hiding_playground_session_uses_current_employee_identity() -> None:
    service = with_fake_service()
    try:
        with TestClient(app) as client:
            response = client.delete(
                "/api/v1/chat/playground/sessions/session-1",
                headers=auth_headers(tenant_id="tenant-123", user_id="employee-7"),
            )
        assert response.status_code == 204
        assert service.hidden == {
            "tenant_id": "tenant-123", "user_id": "employee-7", "session_id": "session-1"
        }
    finally:
        app.dependency_overrides.clear()


def test_conversation_list_passes_validated_filters_and_pagination() -> None:
    service = with_fake_service()
    try:
        with TestClient(app) as client:
            response = client.get(
                "/api/v1/chat/conversations"
                "?conversation_status=CLOSED&channel=CUSTOM_API&limit=25&offset=50",
                headers=auth_headers(tenant_id="tenant-123", user_id="employee-7"),
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.json()[0]["channel"] == "CUSTOM_API"
    assert response.json()[0]["status"] == "CLOSED"
    assert service.listed_conversations == {
        "tenant_id": "tenant-123",
        "status": "CLOSED",
        "channel": "CUSTOM_API",
        "limit": 25,
        "offset": 50,
    }


def test_conversation_list_rejects_invalid_filters_and_pagination_bounds() -> None:
    with TestClient(app) as client:
        for query in (
            "conversation_status=PENDING",
            "channel=EMAIL",
            "limit=0",
            "limit=101",
            "offset=-1",
        ):
            response = client.get(
                f"/api/v1/chat/conversations?{query}",
                headers=auth_headers(),
            )
            assert response.status_code == 422


def test_conversation_detail_preserves_metadata_citations_and_actions() -> None:
    service = with_fake_service()
    try:
        with TestClient(app) as client:
            response = client.get(
                "/api/v1/chat/conversations/conversation-1",
                headers=auth_headers(tenant_id="tenant-123"),
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["customer_metadata"] == {"plan": "pro", "nested": {"ignored": True}}
    assert body["messages"][0]["role"] == "system"
    assert body["messages"][1]["citations"][0]["document_id"] == "doc-1"
    assert body["messages"][1]["action"] == {
        "type": "ticket_draft",
        "title": "Follow up",
        "description": "Contact the customer.",
    }
    assert service.loaded_conversation == {
        "tenant_id": "tenant-123",
        "session_id": "conversation-1",
    }


def test_tenant_member_can_close_external_conversation() -> None:
    service = with_fake_service()
    try:
        with TestClient(app) as client:
            response = client.delete(
                "/api/v1/chat/sessions/conversation-1",
                headers=auth_headers(tenant_id="tenant-123", user_id="employee-7"),
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 204
    assert service.closed == {
        "tenant_id": "tenant-123",
        "session_id": "conversation-1",
        "integration_token_id": None,
        "user_id": "employee-7",
    }

def test_chat_message_returns_structured_citations() -> None:
    service = with_fake_service()
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/chat/sessions/session-1/messages",
                headers=auth_headers(tenant_id="tenant-123"),
                json={"content": "Chinh sach doi tra?"},
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    body = response.json()
    assert body["role"] == "assistant"
    assert body["content"] == "Cau tra loi [S1]."
    assert body["citations"][0]["id"] == "S1"
    assert body["citations"][0]["document_id"] == "doc-1"
    assert service.submitted["tenant_id"] == "tenant-123"


def test_chat_message_timeout_returns_gateway_timeout() -> None:
    service = TimeoutChatService()
    app.dependency_overrides[get_chat_service] = lambda: service
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/chat/sessions/session-1/messages",
                headers=auth_headers(tenant_id="tenant-123"),
                json={"content": "Summarize this document."},
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 504
    body = response.json()
    assert body["error"]["code"] == "MODEL_TIMEOUT"
    assert "too long" in body["error"]["message"]


def test_chat_message_provider_error_returns_bad_gateway() -> None:
    service = ProviderErrorChatService()
    app.dependency_overrides[get_chat_service] = lambda: service
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/chat/sessions/session-1/messages",
                headers=auth_headers(tenant_id="tenant-123"),
                json={"content": "Summarize this document."},
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 502
    assert response.json()["error"]["code"] == "MODEL_PROVIDER_ERROR"


def test_legacy_chat_alias_requires_authentication() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/chat/sessions",
            json={"chatbot_id": "bot_test", "knowledge_base_id": "kb_test"},
        )

    assert response.status_code in {401, 403}
