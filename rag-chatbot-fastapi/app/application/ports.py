from collections.abc import AsyncIterator, Sequence
from typing import Any, Protocol

from app.domain.models import KnowledgeUnit


class ChatModelPort(Protocol):
    async def complete(self, messages: Sequence[dict[str, Any]]) -> str: ...

    def stream(self, messages: Sequence[dict[str, Any]]) -> AsyncIterator[str]: ...


class TextEmbeddingPort(Protocol):
    async def embed_documents(self, texts: Sequence[str]) -> list[list[float]]: ...

    async def embed_query(self, text: str) -> list[float]: ...


class VectorRetrieverPort(Protocol):
    async def retrieve(
        self,
        tenant_id: str,
        knowledge_base_id: str,
        query_text: str,
        limit: int,
    ) -> list[KnowledgeUnit]: ...


class GraphRetrieverPort(Protocol):
    async def retrieve(
        self,
        tenant_id: str,
        knowledge_base_id: str,
        query_text: str,
    ) -> list[KnowledgeUnit]: ...


class SparseRetrieverPort(Protocol):
    async def retrieve(
        self,
        tenant_id: str,
        knowledge_base_id: str,
        query_text: str,
        limit: int,
    ) -> list[KnowledgeUnit]: ...


class RerankerPort(Protocol):
    async def rerank(
        self, query_text: str, candidates: Sequence[KnowledgeUnit]
    ) -> list[KnowledgeUnit]: ...


class ObjectStoragePort(Protocol):
    async def put(self, key: str, content: bytes, content_type: str) -> str: ...

    async def delete_prefix(self, prefix: str) -> None: ...


class JobQueuePort(Protocol):
    async def publish(self, routing_key: str, payload: dict[str, Any]) -> None: ...
