from datetime import UTC, datetime, timedelta

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
from app.rag.models import AssistantMessage, ChatSession, Citation


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
    ) -> AssistantMessage:
        self.submitted = {
            "tenant_id": tenant_id,
            "session_id": session_id,
            "content": content,
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
    ) -> AssistantMessage:
        del tenant_id, session_id, content
        raise ChatModelTimeoutError("Model generation timed out")


class ProviderErrorChatService(FakeChatService):
    async def submit_message(
        self,
        *,
        tenant_id: str,
        session_id: str,
        content: str,
    ) -> AssistantMessage:
        del tenant_id, session_id, content
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
