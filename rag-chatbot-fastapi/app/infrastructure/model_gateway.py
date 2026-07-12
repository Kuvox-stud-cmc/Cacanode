import asyncio
from collections.abc import AsyncIterator, Sequence
from typing import Any

import httpx
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from pydantic import SecretStr

from app.core.config import Settings
from app.rag.errors import ChatModelTimeoutError


class OpenAICompatibleChatModel:
    """Adapter for Cacanode's private OpenAI-compatible model endpoint."""

    def __init__(self, settings: Settings):
        if not settings.LLM_MODEL_ID:
            raise RuntimeError("LLM_MODEL_ID is not configured")
        self._settings = settings
        self._base_url = settings.LLM_BASE_URL.rstrip("/")
        self._model = settings.LLM_MODEL_ID
        self._timeout_seconds = settings.LLM_TIMEOUT_SECONDS
        self._use_ollama_native = settings.LLM_USE_OLLAMA_NATIVE_CHAT
        self._client: ChatOpenAI | None = None
        if not self._use_ollama_native:
            self._client = ChatOpenAI(
                base_url=settings.LLM_BASE_URL,
                api_key=SecretStr(settings.LLM_INTERNAL_API_KEY),
                model=settings.LLM_MODEL_ID,
                temperature=settings.LLM_TEMPERATURE,
                max_tokens=settings.LLM_MAX_OUTPUT_TOKENS,
            )

    @staticmethod
    def _messages(messages: Sequence[dict[str, Any]]) -> list[BaseMessage]:
        result: list[BaseMessage] = []
        for message in messages:
            content = str(message.get("content", ""))
            if message.get("role") == "system":
                result.append(SystemMessage(content=content))
            else:
                result.append(HumanMessage(content=content))
        return result

    async def complete(self, messages: Sequence[dict[str, Any]]) -> str:
        try:
            if self._use_ollama_native:
                return await asyncio.wait_for(
                    self._complete_ollama_native(messages),
                    timeout=self._timeout_seconds,
                )
            return await asyncio.wait_for(
                self._complete_openai_compatible(messages),
                timeout=self._timeout_seconds,
            )
        except TimeoutError as exc:
            raise ChatModelTimeoutError("Model generation timed out") from exc

    async def _complete_openai_compatible(self, messages: Sequence[dict[str, Any]]) -> str:
        if self._client is None:
            raise RuntimeError("OpenAI-compatible chat client is not configured")
        response = await self._client.ainvoke(self._messages(messages))
        return str(response.content)

    async def _complete_ollama_native(self, messages: Sequence[dict[str, Any]]) -> str:
        payload: dict[str, Any] = {
            "model": self._model,
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
        if self._use_ollama_native:
            yield await self.complete(messages)
            return
        if self._client is None:
            raise RuntimeError("OpenAI-compatible chat client is not configured")
        async for chunk in self._client.astream(self._messages(messages)):
            if chunk.content:
                yield str(chunk.content)
