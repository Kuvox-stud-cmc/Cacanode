import asyncio
import time
from collections.abc import AsyncIterator, Sequence
from typing import Any

import httpx
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from pydantic import SecretStr

from app.core.config import Settings
from app.core.metrics import AI_CHAT_MODEL_SECONDS, AI_CHAT_MODEL_TIMEOUTS_TOTAL
from app.rag.errors import ChatModelProviderError, ChatModelTimeoutError


def _messages(messages: Sequence[dict[str, Any]]) -> list[BaseMessage]:
    result: list[BaseMessage] = []
    for message in messages:
        content = str(message.get("content", ""))
        if message.get("role") == "system":
            result.append(SystemMessage(content=content))
        else:
            result.append(HumanMessage(content=content))
    return result


MIN_REASONING_MODEL_OUTPUT_TOKENS = 1024


def _is_reasoning_model(model: str) -> bool:
    normalized = model.lower()
    return (
        normalized.startswith("o1")
        or normalized.startswith("o3")
        or normalized.startswith("o4")
    )


def _supports_temperature(model: str) -> bool:
    return not _is_reasoning_model(model)


def _completion_token_budget(model: str, configured_tokens: int) -> int:
    if _is_reasoning_model(model):
        return max(configured_tokens, MIN_REASONING_MODEL_OUTPUT_TOKENS)
    return configured_tokens


def _message_content_to_text(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict) and isinstance(item.get("text"), str):
                parts.append(item["text"])
        return "".join(parts)
    return str(content)


def _finish_reason(response: Any) -> str:
    metadata = getattr(response, "response_metadata", None)
    if isinstance(metadata, dict):
        finish_reason = metadata.get("finish_reason")
        if finish_reason is not None:
            return str(finish_reason)
    return "unknown"


class OllamaChatModel:
    """Adapter for Ollama's native /api/chat endpoint."""

    provider = "ollama"

    def __init__(self, settings: Settings):
        if not settings.LLM_MODEL_ID:
            raise RuntimeError("LLM_MODEL_ID is not configured")
        self._settings = settings
        self._base_url = settings.LLM_BASE_URL.rstrip("/")
        self.model = settings.LLM_MODEL_ID
        self._timeout_seconds = settings.LLM_TIMEOUT_SECONDS

    async def complete(self, messages: Sequence[dict[str, Any]]) -> str:
        started_at = time.perf_counter()
        outcome = "success"
        try:
            return await asyncio.wait_for(
                self._complete_ollama_native(messages),
                timeout=self._timeout_seconds,
            )
        except TimeoutError as exc:
            outcome = "timeout"
            AI_CHAT_MODEL_TIMEOUTS_TOTAL.labels(
                provider=self.provider,
                model=self.model,
            ).inc()
            raise ChatModelTimeoutError("Model generation timed out") from exc
        except httpx.TimeoutException as exc:
            outcome = "timeout"
            AI_CHAT_MODEL_TIMEOUTS_TOTAL.labels(
                provider=self.provider,
                model=self.model,
            ).inc()
            raise ChatModelTimeoutError("Model generation timed out") from exc
        except Exception as exc:
            outcome = "error"
            raise ChatModelProviderError("Model provider request failed") from exc
        finally:
            AI_CHAT_MODEL_SECONDS.labels(
                provider=self.provider,
                model=self.model,
                outcome=outcome,
            ).observe(time.perf_counter() - started_at)

    async def _complete_ollama_native(self, messages: Sequence[dict[str, Any]]) -> str:
        payload: dict[str, Any] = {
            "model": self.model,
            "messages": [
                {"role": message.get("role", "user"), "content": str(message.get("content", ""))}
                for message in messages
            ],
            "stream": False,
            "options": {
                "temperature": self._settings.LLM_TEMPERATURE,
                "num_predict": self._settings.LLM_MAX_OUTPUT_TOKENS,
            },
        }
        if self._settings.LLM_DISABLE_THINKING:
            payload["think"] = False

        async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
            response = await client.post(self._ollama_chat_url(), json=payload)
            response.raise_for_status()
            data = response.json()
        message = data.get("message")
        if isinstance(message, dict):
            return str(message.get("content", ""))
        return ""

    def _ollama_chat_url(self) -> str:
        base_url = self._base_url
        if base_url.endswith("/v1"):
            base_url = base_url[: -len("/v1")]
        return f"{base_url}/api/chat"

    async def stream(self, messages: Sequence[dict[str, Any]]) -> AsyncIterator[str]:
        yield await self.complete(messages)


class OpenAIChatModel:
    """Adapter for OpenAI-hosted answer generation."""

    provider = "openai"

    def __init__(self, settings: Settings):
        if not settings.OPENAI_API_KEY:
            raise RuntimeError("OPENAI_API_KEY is required when LLM_PROVIDER=openai")
        if not settings.OPENAI_MODEL:
            raise RuntimeError("OPENAI_MODEL is not configured")
        self.model = settings.OPENAI_MODEL
        self._timeout_seconds = settings.LLM_TIMEOUT_SECONDS
        client_kwargs: dict[str, Any] = {
            "api_key": SecretStr(settings.OPENAI_API_KEY),
            "model": settings.OPENAI_MODEL,
            "max_completion_tokens": _completion_token_budget(
                settings.OPENAI_MODEL,
                settings.LLM_MAX_OUTPUT_TOKENS,
            ),
            "timeout": settings.LLM_TIMEOUT_SECONDS,
        }
        if _supports_temperature(settings.OPENAI_MODEL):
            client_kwargs["temperature"] = settings.LLM_TEMPERATURE
        self._client = ChatOpenAI(**client_kwargs)

    async def complete(self, messages: Sequence[dict[str, Any]]) -> str:
        started_at = time.perf_counter()
        outcome = "success"
        try:
            response = await asyncio.wait_for(
                self._client.ainvoke(_messages(messages)),
                timeout=self._timeout_seconds,
            )
            content = _message_content_to_text(response.content).strip()
            if not content:
                finish_reason = _finish_reason(response)
                raise ChatModelProviderError(
                    "Model provider returned an empty response "
                    f"(finish_reason={finish_reason})"
                )
            return content
        except TimeoutError as exc:
            outcome = "timeout"
            AI_CHAT_MODEL_TIMEOUTS_TOTAL.labels(
                provider=self.provider,
                model=self.model,
            ).inc()
            raise ChatModelTimeoutError("Model generation timed out") from exc
        except ChatModelProviderError:
            outcome = "error"
            raise
        except Exception as exc:
            outcome = "error"
            raise ChatModelProviderError("Model provider request failed") from exc
        finally:
            AI_CHAT_MODEL_SECONDS.labels(
                provider=self.provider,
                model=self.model,
                outcome=outcome,
            ).observe(time.perf_counter() - started_at)

    async def stream(self, messages: Sequence[dict[str, Any]]) -> AsyncIterator[str]:
        async for chunk in self._client.astream(_messages(messages)):
            if chunk.content:
                yield str(chunk.content)


def create_chat_model(settings: Settings) -> OllamaChatModel | OpenAIChatModel:
    if settings.LLM_PROVIDER == "openai":
        return OpenAIChatModel(settings)
    return OllamaChatModel(settings)
