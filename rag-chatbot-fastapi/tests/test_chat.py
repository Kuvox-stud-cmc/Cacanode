from fastapi.testclient import TestClient

from app.main import app


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


def test_versioned_chat_contract_is_explicitly_unimplemented() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/chat/sessions",
            json={"chatbot_id": "bot_test", "locale": "vi-VN"},
        )

    assert response.status_code == 501
    body = response.json()["error"]
    assert body["code"] == "NOT_IMPLEMENTED"
    assert body["request_id"] == response.headers["X-Request-ID"]


def test_legacy_chat_alias_remains_available() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/chat/sessions",
            json={"chatbot_id": "bot_test"},
        )

    assert response.status_code == 501
