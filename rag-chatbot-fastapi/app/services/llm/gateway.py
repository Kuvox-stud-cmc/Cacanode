"""LLM Gateway with multi-provider support.

Provides factory functions and gateway class for interacting with various
LLM providers (Groq, OpenAI, Anthropic) and embedding providers (VoyageAI, OpenAI).
"""

from typing import AsyncGenerator

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.embeddings import Embeddings

from app.core.config import settings


def get_llm(tenant_config: dict) -> BaseChatModel:
    """Factory function to create LLM instance based on tenant configuration.

    Supports multiple LLM providers with optional Bring Your Own Key (BYOK).

    Args:
        tenant_config: Dictionary containing:
            - llm_provider: Provider name ("groq", "openai", "anthropic")
            - llm_model: Model identifier
            - api_key: Optional BYOK API key (falls back to settings)

    Returns:
        LangChain chat model instance.

    Raises:
        ValueError: If provider is not supported or API key is missing.
    """
    provider = tenant_config.get("llm_provider", "groq").lower()
    model = tenant_config.get("llm_model")
    api_key = tenant_config.get("api_key")

    if provider == "groq":
        from langchain_groq import ChatGroq

        key = api_key or settings.GROQ_API_KEY
        if not key:
            raise ValueError("Groq API key not configured")
        return ChatGroq(
            model=model or settings.LLM_MODEL,
            api_key=key,
            temperature=0.7,
        )

    elif provider == "openai":
        from langchain_openai import ChatOpenAI

        key = api_key or settings.GROQ_API_KEY  # Fallback pattern - user should set OPENAI_API_KEY
        if not key:
            raise ValueError("OpenAI API key not configured")
        return ChatOpenAI(
            model=model or "gpt-4o",
            api_key=key,
            temperature=0.7,
        )

    elif provider == "anthropic":
        from langchain_anthropic import ChatAnthropic

        key = api_key
        if not key:
            raise ValueError("Anthropic API key required (BYOK only)")
        return ChatAnthropic(
            model=model or "claude-3-5-sonnet-20241022",
            api_key=key,
            temperature=0.7,
        )

    else:
        raise ValueError(f"Unsupported LLM provider: {provider}")


def get_embeddings(tenant_config: dict) -> Embeddings:
    """Factory function to create embeddings instance based on tenant configuration.

    Supports multiple embedding providers with optional Bring Your Own Key (BYOK).

    Args:
        tenant_config: Dictionary containing:
            - embed_provider: Provider name ("voyageai", "openai")
            - embed_model: Model identifier
            - api_key: Optional BYOK API key (falls back to settings)

    Returns:
        LangChain embeddings instance.

    Raises:
        ValueError: If provider is not supported or API key is missing.
    """
    provider = tenant_config.get("embed_provider", "voyageai").lower()
    model = tenant_config.get("embed_model")
    api_key = tenant_config.get("api_key")

    if provider == "voyageai":
        from langchain_voyageai import VoyageAIEmbeddings

        key = api_key or settings.VOYAGE_API_KEY
        if not key:
            raise ValueError("VoyageAI API key not configured")
        return VoyageAIEmbeddings(
            model=model or settings.EMBED_MODEL,
            api_key=key,
        )

    elif provider == "openai":
        from langchain_openai import OpenAIEmbeddings

        key = api_key
        if not key:
            raise ValueError("OpenAI API key required for embeddings (BYOK only)")
        return OpenAIEmbeddings(
            model=model or "text-embedding-3-small",
            api_key=key,
        )

    else:
        raise ValueError(f"Unsupported embedding provider: {provider}")


class LLMGateway:
    """Gateway for LLM operations with tenant-specific configuration.

    Provides a unified interface for:
    - Text completion
    - Streaming generation
    - Document embeddings
    - Query embeddings

    Each tenant can have their own provider configuration and optional BYOK.
    """

    def __init__(self, tenant_config: dict):
        """Initialize LLM Gateway with tenant configuration.

        Args:
            tenant_config: Dictionary with provider settings and optional API keys.
        """
        self.tenant_config = tenant_config
        self.llm = get_llm(tenant_config)
        self.embeddings = get_embeddings(tenant_config)

    async def complete(self, prompt: str, system: str | None = None) -> str:
        """Generate a complete text response.

        Args:
            prompt: The user prompt/message.
            system: Optional system message for context/instructions.

        Returns:
            Generated text response.
        """
        messages = []
        if system:
            messages.append(SystemMessage(content=system))
        messages.append(HumanMessage(content=prompt))

        response = await self.llm.ainvoke(messages)
        return str(response.content)

    async def stream(
        self, prompt: str, system: str | None = None
    ) -> AsyncGenerator[str, None]:
        """Stream text response in chunks.

        Args:
            prompt: The user prompt/message.
            system: Optional system message for context/instructions.

        Yields:
            Text chunks as they are generated.
        """
        messages = []
        if system:
            messages.append(SystemMessage(content=system))
        messages.append(HumanMessage(content=prompt))

        async for chunk in self.llm.astream(messages):
            content = chunk.content
            if content:
                yield str(content)

    async def embed(self, texts: list[str]) -> list[list[float]]:
        """Generate embeddings for multiple texts.

        Args:
            texts: List of text strings to embed.

        Returns:
            List of embedding vectors (one per input text).
        """
        return await self.embeddings.aembed_documents(texts)

    async def embed_query(self, text: str) -> list[float]:
        """Generate embedding for a single query text.

        Args:
            text: Query text to embed.

        Returns:
            Single embedding vector.
        """
        return await self.embeddings.aembed_query(text)
