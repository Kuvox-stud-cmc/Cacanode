from __future__ import annotations

from typing import Any

import pytest

from app.core.config import Settings
from app.infrastructure.model_gateway import OpenAICompatibleChatModel


class FakeOllamaResponse:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, object]:
        return {"message": {"role": "assistant", "content": "The answer is four."}}


class FakeOllamaClient:
    last_url = ""
    last_json: dict[str, Any] = {}

    def __init__(self, timeout: float):
        self.timeout = timeout

    async def __aenter__(self) -> "FakeOllamaClient":
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def post(self, url: str, json: dict[str, object]) -> FakeOllamaResponse:
        FakeOllamaClient.last_url = url
        FakeOllamaClient.last_json = dict(json)
        return FakeOllamaResponse()


@pytest.mark.asyncio
async def test_ollama_native_chat_disables_thinking(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.httpx.AsyncClient", FakeOllamaClient)
    gateway = OpenAICompatibleChatModel(
        Settings(
            LLM_BASE_URL="http://localhost:11434/v1",
            LLM_MODEL_ID="gemma4:12b",
            LLM_MAX_OUTPUT_TOKENS=64,
            LLM_TEMPERATURE=0,
            LLM_USE_OLLAMA_NATIVE_CHAT=True,
            LLM_DISABLE_THINKING=True,
        )
    )

    response = await gateway.complete([{"role": "user", "content": "what is 2+2?"}])

    assert response == "The answer is four."
    assert FakeOllamaClient.last_url == "http://localhost:11434/api/chat"
    assert FakeOllamaClient.last_json["think"] is False
    assert FakeOllamaClient.last_json["stream"] is False
    assert FakeOllamaClient.last_json["options"] == {"temperature": 0.0, "num_predict": 64}
