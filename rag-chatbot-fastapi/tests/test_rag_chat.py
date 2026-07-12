from __future__ import annotations

from collections.abc import Sequence
from types import SimpleNamespace
from typing import Any

import pytest

from app.core.config import Settings
from app.ingestion.embedding import OllamaEmbeddingClient
from app.ingestion.errors import TransientIngestionError
from app.rag.chat_service import NO_INFORMATION_RESPONSE, RagChatService
from app.rag.errors import ChatSessionNotFoundError
from app.rag.models import RetrievedChunk
from app.rag.retrieval import QdrantVectorRetriever
from app.rag.sessions import InMemoryChatSessionStore


class FakeOllamaResponse:
    def __init__(self, payload: dict[str, object]):
        self._payload = payload

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, object]:
        return self._payload


class FakeOllamaClient:
    payload: dict[str, object]

    def __init__(self, timeout: int):
        del timeout

    async def __aenter__(self) -> FakeOllamaClient:
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def post(self, url: str, json: dict[str, object]) -> FakeOllamaResponse:
        self.last_url = url
        self.last_json = json
        return FakeOllamaResponse(self.payload)


@pytest.mark.asyncio
async def test_embedding_adapter_embeds_queries(monkeypatch: pytest.MonkeyPatch) -> None:
    FakeOllamaClient.payload = {"embeddings": [[1, 2, 3]]}
    monkeypatch.setattr("app.ingestion.embedding.httpx.AsyncClient", FakeOllamaClient)
    embedder = OllamaEmbeddingClient(Settings(TEXT_EMBEDDING_DIMENSION=3))

    assert await embedder.embed_query("hello") == [1.0, 2.0, 3.0]


@pytest.mark.asyncio
async def test_embedding_adapter_reports_query_model_errors(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    FakeOllamaClient.payload = {"error": "model not found"}
    monkeypatch.setattr("app.ingestion.embedding.httpx.AsyncClient", FakeOllamaClient)
    embedder = OllamaEmbeddingClient(Settings(TEXT_EMBEDDING_DIMENSION=3))

    with pytest.raises(TransientIngestionError, match="model not found"):
        await embedder.embed_query("hello")


class FakeQdrantClient:
    def __init__(self) -> None:
        self.kwargs: dict[str, Any] = {}

    async def query_points(self, **kwargs: Any) -> object:
        self.kwargs = kwargs
        return SimpleNamespace(
            points=[
                SimpleNamespace(
                    score=0.88,
                    payload={
                        "document_id": "doc-1",
                        "source_name": "policy.txt",
                        "page_number": 1,
                        "chunk_index": 2,
                        "text": "Policy source text.",
                    },
                )
            ]
        )


@pytest.mark.asyncio
async def test_qdrant_retriever_filters_by_tenant_and_knowledge_base() -> None:
    client = FakeQdrantClient()
    retriever = QdrantVectorRetriever(
        Settings(
            QDRANT_COLLECTION="chunks",
            QDRANT_TENANT_FIELD="tenant_id",
            QDRANT_KNOWLEDGE_BASE_FIELD="knowledge_base_id",
        ),
        client=client,  # type: ignore[arg-type]
    )

    chunks = await retriever.retrieve(
        tenant_id="tenant-1",
        knowledge_base_id="kb-1",
        query_vector=[0.1, 0.2, 0.3],
        limit=5,
        score_threshold=0.35,
    )

    assert len(chunks) == 1
    assert chunks[0].document_id == "doc-1"
    query_filter = client.kwargs["query_filter"]
    conditions = {condition.key: condition.match.value for condition in query_filter.must}
    assert conditions == {"tenant_id": "tenant-1", "knowledge_base_id": "kb-1"}
    assert client.kwargs["collection_name"] == "chunks"
    assert client.kwargs["score_threshold"] == 0.35


class FakeEmbedder:
    async def embed_query(self, text: str) -> list[float]:
        self.text = text
        return [0.1, 0.2, 0.3]


class FakeRetriever:
    def __init__(self, chunks: list[RetrievedChunk]):
        self.chunks = chunks
        self.calls: list[dict[str, object]] = []

    async def retrieve(
        self,
        *,
        tenant_id: str,
        knowledge_base_id: str,
        query_vector: Sequence[float],
        limit: int,
        score_threshold: float,
    ) -> list[RetrievedChunk]:
        self.calls.append(
            {
                "tenant_id": tenant_id,
                "knowledge_base_id": knowledge_base_id,
                "query_vector": list(query_vector),
                "limit": limit,
                "score_threshold": score_threshold,
            }
        )
        return self.chunks


class FakeChatModel:
    def __init__(self) -> None:
        self.calls: list[Sequence[dict[str, object]]] = []

    async def complete(self, messages: Sequence[dict[str, object]]) -> str:
        self.calls.append(messages)
        return "Sản phẩm được đổi trong 7 ngày [S1]."


def make_service(
    *,
    chunks: list[RetrievedChunk],
    settings: Settings | None = None,
) -> tuple[RagChatService, InMemoryChatSessionStore, FakeRetriever, FakeChatModel]:
    retriever = FakeRetriever(chunks)
    model = FakeChatModel()
    service = RagChatService(
        settings=settings
        or Settings(TEXT_TOP_K=8, FINAL_CONTEXT_TOP_K=5, MIN_RETRIEVAL_CONFIDENCE=0.35),
        sessions=InMemoryChatSessionStore(),
        embedder=FakeEmbedder(),
        retriever=retriever,
        chat_model=model,
    )
    return service, service._sessions, retriever, model


@pytest.mark.asyncio
async def test_chat_service_returns_no_information_without_evidence() -> None:
    service, store, retriever, model = make_service(chunks=[])
    session = service.create_session(
        tenant_id="tenant-1",
        user_id="user-1",
        chatbot_id="bot-1",
        knowledge_base_id="kb-1",
        locale="vi-VN",
    )

    message = await service.submit_message(
        tenant_id="tenant-1",
        session_id=session.id,
        content="Khong co trong tai lieu?",
    )

    assert message.content == NO_INFORMATION_RESPONSE
    assert message.citations == []
    assert model.calls == []
    assert retriever.calls[0]["knowledge_base_id"] == "kb-1"
    assert store.get_for_tenant(session.id, "tenant-1") is not None


@pytest.mark.asyncio
async def test_chat_service_generates_grounded_answer_with_citations() -> None:
    service, _, retriever, model = make_service(
        chunks=[
            RetrievedChunk(
                document_id="doc-1",
                source_name="policy.txt",
                page_number=1,
                chunk_index=0,
                text="Sản phẩm được đổi trong 7 ngày.",
                score=0.91,
            )
        ]
    )
    session = service.create_session(
        tenant_id="tenant-1",
        user_id="user-1",
        chatbot_id="bot-1",
        knowledge_base_id="kb-1",
        locale="vi-VN",
    )

    message = await service.submit_message(
        tenant_id="tenant-1",
        session_id=session.id,
        content="Chinh sach doi tra?",
    )

    assert message.content == "Sản phẩm được đổi trong 7 ngày [S1]."
    assert message.citations[0].id == "S1"
    assert message.citations[0].document_id == "doc-1"
    assert message.citations[0].snippet == "Sản phẩm được đổi trong 7 ngày."
    assert retriever.calls[0]["tenant_id"] == "tenant-1"
    assert "Sản phẩm được đổi trong 7 ngày." in str(model.calls[0][1]["content"])


@pytest.mark.asyncio
async def test_chat_service_enforces_session_tenant_isolation() -> None:
    service, _, _, _ = make_service(chunks=[])
    session = service.create_session(
        tenant_id="tenant-1",
        user_id="user-1",
        chatbot_id="bot-1",
        knowledge_base_id="kb-1",
        locale="vi-VN",
    )

    with pytest.raises(ChatSessionNotFoundError):
        await service.submit_message(
            tenant_id="tenant-2",
            session_id=session.id,
            content="Can I read this?",
        )
