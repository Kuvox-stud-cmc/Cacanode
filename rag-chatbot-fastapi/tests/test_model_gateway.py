from __future__ import annotations

import asyncio
from typing import Any

import pytest
from prometheus_client import REGISTRY

from app.core.config import Settings
from app.infrastructure.model_gateway import (
    OllamaChatModel,
    OpenAIChatModel,
    create_chat_model,
)
from app.rag.errors import ChatModelProviderError, ChatModelTimeoutError


def metric_value(name: str, labels: dict[str, str]) -> float:
    return REGISTRY.get_sample_value(name, labels) or 0.0


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

    async def __aenter__(self) -> FakeOllamaClient:
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def post(self, url: str, json: dict[str, object]) -> FakeOllamaResponse:
        FakeOllamaClient.last_url = url
        FakeOllamaClient.last_json = dict(json)
        return FakeOllamaResponse()


class SlowOllamaClient(FakeOllamaClient):
    async def post(self, url: str, json: dict[str, object]) -> FakeOllamaResponse:
        del url, json
        await asyncio.sleep(1)
        return FakeOllamaResponse()


class FakeOpenAIResponse:
    content = "OpenAI answer."
    response_metadata: dict[str, str] = {}


class EmptyOpenAIResponse:
    content = ""
    response_metadata = {"finish_reason": "length"}


class FakeChatOpenAI:
    last_kwargs: dict[str, Any] = {}
    last_messages: list[Any] = []

    def __init__(self, **kwargs: Any):
        FakeChatOpenAI.last_kwargs = kwargs

    async def ainvoke(self, messages: list[Any]) -> FakeOpenAIResponse:
        FakeChatOpenAI.last_messages = messages
        return FakeOpenAIResponse()


class EmptyChatOpenAI(FakeChatOpenAI):
    async def ainvoke(self, messages: list[Any]) -> EmptyOpenAIResponse:
        FakeChatOpenAI.last_messages = messages
        return EmptyOpenAIResponse()


class FailingChatOpenAI(FakeChatOpenAI):
    async def ainvoke(self, messages: list[Any]) -> FakeOpenAIResponse:
        del messages
        raise RuntimeError("provider rejected request")


def settings(**overrides: object) -> Settings:
    values: dict[str, object] = {
        "_env_file": (),
        "LLM_BASE_URL": "http://localhost:11434/v1",
        "LLM_MODEL_ID": "gemma4:12b",
        "LLM_MAX_OUTPUT_TOKENS": 64,
        "LLM_TEMPERATURE": 0,
        "LLM_TIMEOUT_SECONDS": 1,
        "LLM_DISABLE_THINKING": True,
    }
    values.update(overrides)
    return Settings(**values)


def test_create_chat_model_defaults_to_ollama() -> None:
    model = create_chat_model(settings(LLM_PROVIDER="ollama"))

    assert isinstance(model, OllamaChatModel)


def test_create_chat_model_selects_openai(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.ChatOpenAI", FakeChatOpenAI)

    model = create_chat_model(
        settings(
            LLM_PROVIDER="openai",
            OPENAI_API_KEY="test-key",
            OPENAI_MODEL="gpt-test",
        )
    )

    assert isinstance(model, OpenAIChatModel)


def test_create_chat_model_requires_openai_api_key() -> None:
    with pytest.raises(RuntimeError, match="OPENAI_API_KEY is required"):
        create_chat_model(settings(LLM_PROVIDER="openai", OPENAI_API_KEY=""))


@pytest.mark.asyncio
async def test_ollama_native_chat_disables_thinking(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.httpx.AsyncClient", FakeOllamaClient)
    gateway = OllamaChatModel(settings())

    response = await gateway.complete([{"role": "user", "content": "what is 2+2?"}])

    assert response == "The answer is four."
    assert FakeOllamaClient.last_url == "http://localhost:11434/api/chat"
    assert FakeOllamaClient.last_json["think"] is False
    assert FakeOllamaClient.last_json["stream"] is False
    assert FakeOllamaClient.last_json["options"] == {"temperature": 0.0, "num_predict": 64}


@pytest.mark.asyncio
async def test_openai_chat_model_passes_configured_client_options(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.ChatOpenAI", FakeChatOpenAI)
    gateway = OpenAIChatModel(
        settings(
            LLM_PROVIDER="openai",
            OPENAI_API_KEY="test-key",
            OPENAI_MODEL="gpt-test",
            LLM_TEMPERATURE=0.4,
            LLM_MAX_OUTPUT_TOKENS=99,
            LLM_TIMEOUT_SECONDS=12,
        )
    )

    response = await gateway.complete(
        [
            {"role": "system", "content": "answer tersely"},
            {"role": "user", "content": "hello"},
        ]
    )

    assert response == "OpenAI answer."
    assert FakeChatOpenAI.last_kwargs["model"] == "gpt-test"
    assert FakeChatOpenAI.last_kwargs["temperature"] == 0.4
    assert FakeChatOpenAI.last_kwargs["max_completion_tokens"] == 99
    assert FakeChatOpenAI.last_kwargs["timeout"] == 12.0
    assert FakeChatOpenAI.last_kwargs["api_key"].get_secret_value() == "test-key"
    assert len(FakeChatOpenAI.last_messages) == 2


def test_openai_chat_model_omits_temperature_for_reasoning_models(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.ChatOpenAI", FakeChatOpenAI)

    OpenAIChatModel(
        settings(
            LLM_PROVIDER="openai",
            OPENAI_API_KEY="test-key",
            OPENAI_MODEL="o4-mini",
            LLM_TEMPERATURE=0.2,
        )
    )

    assert "temperature" not in FakeChatOpenAI.last_kwargs
    assert FakeChatOpenAI.last_kwargs["max_completion_tokens"] == 1024


def test_openai_chat_model_preserves_larger_reasoning_model_token_budget(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.ChatOpenAI", FakeChatOpenAI)

    OpenAIChatModel(
        settings(
            LLM_PROVIDER="openai",
            OPENAI_API_KEY="test-key",
            OPENAI_MODEL="o4-mini",
            LLM_MAX_OUTPUT_TOKENS=2048,
        )
    )

    assert FakeChatOpenAI.last_kwargs["max_completion_tokens"] == 2048


def test_openai_chat_model_passes_reasoning_effort_for_reasoning_models(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.ChatOpenAI", FakeChatOpenAI)

    create_chat_model(
        settings(
            LLM_PROVIDER="openai",
            OPENAI_API_KEY="test-key",
            OPENAI_MODEL="o4-mini",
        ),
        reasoning_effort="low",
    )

    assert FakeChatOpenAI.last_kwargs["reasoning_effort"] == "low"


def test_openai_chat_model_ignores_reasoning_effort_for_non_reasoning_models(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.ChatOpenAI", FakeChatOpenAI)

    create_chat_model(
        settings(
            LLM_PROVIDER="openai",
            OPENAI_API_KEY="test-key",
            OPENAI_MODEL="gpt-test",
        ),
        reasoning_effort="low",
    )

    assert "reasoning_effort" not in FakeChatOpenAI.last_kwargs


@pytest.mark.asyncio
async def test_openai_empty_response_is_provider_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.ChatOpenAI", EmptyChatOpenAI)
    gateway = OpenAIChatModel(
        settings(
            LLM_PROVIDER="openai",
            OPENAI_API_KEY="test-key",
            OPENAI_MODEL="o4-mini",
        )
    )

    with pytest.raises(ChatModelProviderError, match="empty response"):
        await gateway.complete([{"role": "user", "content": "hello"}])


@pytest.mark.asyncio
async def test_openai_provider_errors_are_wrapped(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.ChatOpenAI", FailingChatOpenAI)
    gateway = OpenAIChatModel(
        settings(
            LLM_PROVIDER="openai",
            OPENAI_API_KEY="test-key",
            OPENAI_MODEL="gpt-test",
        )
    )

    with pytest.raises(ChatModelProviderError):
        await gateway.complete([{"role": "user", "content": "hello"}])


@pytest.mark.asyncio
async def test_ollama_timeout_records_counter_and_raises_model_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.infrastructure.model_gateway.httpx.AsyncClient", SlowOllamaClient)
    labels = {"provider": "ollama", "model": "timeout-model"}
    before = metric_value("cacanode_ai_chat_model_timeouts_total", labels)
    gateway = OllamaChatModel(
        settings(
            LLM_MODEL_ID="timeout-model",
            LLM_TIMEOUT_SECONDS=0.001,
        )
    )

    with pytest.raises(ChatModelTimeoutError):
        await gateway.complete([{"role": "user", "content": "slow"}])

    after = metric_value("cacanode_ai_chat_model_timeouts_total", labels)
    assert after == before + 1
    assert metric_value(
        "cacanode_ai_chat_model_seconds_count",
        {"provider": "ollama", "model": "timeout-model", "outcome": "timeout"},
    ) >= 1
