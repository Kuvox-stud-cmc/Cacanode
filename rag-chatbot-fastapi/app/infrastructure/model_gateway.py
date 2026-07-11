from collections.abc import AsyncIterator, Sequence
from typing import Any

from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from pydantic import SecretStr

from app.core.config import Settings


class OpenAICompatibleChatModel:
    """Adapter for Cacanode's private OpenAI-compatible model endpoint."""

    def __init__(self, settings: Settings):
        if not settings.LLM_MODEL_ID:
            raise RuntimeError("LLM_MODEL_ID is not configured")
        self._client = ChatOpenAI(
            base_url=settings.LLM_BASE_URL,
            api_key=SecretStr(settings.LLM_INTERNAL_API_KEY),
            model=settings.LLM_MODEL_ID,
            temperature=settings.LLM_TEMPERATURE,
            max_completion_tokens=settings.LLM_MAX_OUTPUT_TOKENS,
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
        response = await self._client.ainvoke(self._messages(messages))
        return str(response.content)

    async def stream(self, messages: Sequence[dict[str, Any]]) -> AsyncIterator[str]:
        async for chunk in self._client.astream(self._messages(messages)):
            if chunk.content:
                yield str(chunk.content)
