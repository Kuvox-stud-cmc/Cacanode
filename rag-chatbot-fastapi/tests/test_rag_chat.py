from __future__ import annotations

from collections.abc import Sequence
from types import SimpleNamespace
from typing import Any

import pytest
from prometheus_client import REGISTRY

from app.core.config import Settings
from app.ingestion.embedding import OllamaEmbeddingClient
from app.ingestion.errors import TransientIngestionError
from app.rag.chat_service import NO_INFORMATION_RESPONSE, RagChatService
from app.rag.errors import ChatModelTimeoutError, ChatSessionNotFoundError
from app.rag.models import RetrievedChunk
from app.rag.retrieval import QdrantVectorRetriever
from app.rag.sessions import InMemoryChatSessionStore


def metric_value(name: str, labels: dict[str, str]) -> float:
    return REGISTRY.get_sample_value(name, labels) or 0.0


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
    labels = {"operation": "query", "provider": "ollama", "outcome": "success"}
    before = metric_value("cacanode_ai_embedding_seconds_count", labels)

    assert await embedder.embed_query("hello") == [1.0, 2.0, 3.0]
    assert metric_value("cacanode_ai_embedding_seconds_count", labels) == before + 1


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
    )

    assert len(chunks) == 1
    assert chunks[0].document_id == "doc-1"
    query_filter = client.kwargs["query_filter"]
    conditions = {condition.key: condition.match.value for condition in query_filter.must}
    assert conditions == {"tenant_id": "tenant-1", "knowledge_base_id": "kb-1"}
    assert client.kwargs["collection_name"] == "chunks"
    assert client.kwargs["using"] == "text_embeddinggemma_v1"
    assert "score_threshold" not in client.kwargs


@pytest.mark.asyncio
async def test_qdrant_retriever_can_filter_to_allowed_document_ids() -> None:
    client = FakeQdrantClient()
    retriever = QdrantVectorRetriever(Settings(), client=client)  # type: ignore[arg-type]

    await retriever.retrieve(
        tenant_id="tenant-1", knowledge_base_id="kb-1", query_vector=[0.1],
        limit=5, document_ids=["doc-1", "doc-2"],
    )

    document_condition = next(
        condition for condition in client.kwargs["query_filter"].must
        if condition.key == "document_id"
    )
    assert document_condition.match.any == ["doc-1", "doc-2"]


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
        query_text: str,
        query_vector: Sequence[float],
        document_ids: Sequence[str] | None = None,
    ) -> list[RetrievedChunk]:
        self.calls.append(
            {
                "tenant_id": tenant_id,
                "knowledge_base_id": knowledge_base_id,
                "query_text": query_text,
                "query_vector": list(query_vector),
                "document_ids": list(document_ids) if document_ids is not None else None,
            }
        )
        return self.chunks


class FakeChatModel:
    provider = "test-provider"
    model = "test-model"

    def __init__(self) -> None:
        self.calls: list[Sequence[dict[str, object]]] = []

    async def complete(self, messages: Sequence[dict[str, object]]) -> str:
        self.calls.append(messages)
        return "Sản phẩm được đổi trong 7 ngày [S1]."


class TimeoutChatModel(FakeChatModel):
    provider = "timeout-provider"
    model = "timeout-model"

    async def complete(self, messages: Sequence[dict[str, object]]) -> str:
        self.calls.append(messages)
        raise ChatModelTimeoutError("Model generation timed out")


class TicketDraftChatModel(FakeChatModel):
    async def complete(self, messages: Sequence[dict[str, object]]) -> str:
        self.calls.append(messages)
        return (
            '{"answer":"I prepared a ticket draft for review.",'
            '"ticketDraft":{"title":"Refund request",'
            '"description":"Customer requested help with a refund."}}'
        )


def make_service(
    *,
    chunks: list[RetrievedChunk],
    settings: Settings | None = None,
    model: FakeChatModel | None = None,
) -> tuple[RagChatService, InMemoryChatSessionStore, FakeRetriever, FakeChatModel]:
    retriever = FakeRetriever(chunks)
    model = model or FakeChatModel()
    service = RagChatService(
        settings=settings or Settings(FINAL_CONTEXT_TOP_K=5),
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
    assert retriever.calls[0]["query_text"] == "Khong co trong tai lieu?"
    assert store.get_for_tenant(session.id, "tenant-1") is not None


@pytest.mark.asyncio
async def test_external_chat_can_return_editable_ticket_draft_without_evidence() -> None:
    service, store, _, model = make_service(chunks=[], model=TicketDraftChatModel())
    session = service.create_session(
        tenant_id="tenant-1",
        user_id=None,
        chatbot_id="bot-1",
        knowledge_base_id="kb-1",
        locale="en",
        channel="WIDGET",
        external_user_id="visitor-1",
        integration_token_id="token-1",
    )

    message = await service.submit_message(
        tenant_id="tenant-1",
        session_id=session.id,
        content="Please create a support ticket for my refund.",
        integration_token_id="token-1",
    )

    assert message.content == "I prepared a ticket draft for review."
    assert message.action == {
        "type": "ticket_draft",
        "title": "Refund request",
        "description": "Customer requested help with a refund.",
    }
    assert len(model.calls) == 1
    history = store.list_messages(session_id=session.id, tenant_id="tenant-1")
    assert history[-1].action == message.action


@pytest.mark.asyncio
async def test_external_chat_rejects_another_integration_token() -> None:
    service, _, _, _ = make_service(chunks=[])
    session = service.create_session(
        tenant_id="tenant-1",
        user_id=None,
        chatbot_id="bot-1",
        knowledge_base_id="kb-1",
        locale="en",
        channel="CUSTOM_API",
        integration_token_id="token-1",
    )

    with pytest.raises(ChatSessionNotFoundError):
        await service.submit_message(
            tenant_id="tenant-1",
            session_id=session.id,
            content="Can another token read this?",
            integration_token_id="token-2",
        )


def test_external_conversation_can_be_closed_by_its_integration_token() -> None:
    service, store, _, _ = make_service(chunks=[])
    session = service.create_session(
        tenant_id="tenant-1", user_id=None, chatbot_id="bot-1",
        knowledge_base_id="kb-1", locale="en", channel="WIDGET",
        integration_token_id="token-1",
    )

    service.close_session(
        tenant_id="tenant-1", session_id=session.id, integration_token_id="token-1"
    )

    assert store.get_for_tenant(session.id, "tenant-1") is None


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
async def test_chat_service_records_rag_timing_metrics_on_success() -> None:
    labels = {"provider": "test-provider", "outcome": "success"}
    before = {
        stage: metric_value(
            "cacanode_ai_rag_answer_seconds_count",
            {"stage": stage, **labels},
        )
        for stage in ("embedding", "retrieval", "llm", "total")
    }
    service, _, _, _ = make_service(
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

    await service.submit_message(
        tenant_id="tenant-1",
        session_id=session.id,
        content="Chinh sach doi tra?",
    )

    for stage, count in before.items():
        assert (
            metric_value(
                "cacanode_ai_rag_answer_seconds_count",
                {"stage": stage, **labels},
            )
            == count + 1
        )


@pytest.mark.asyncio
async def test_chat_service_records_rag_timeout_metrics() -> None:
    labels = {"provider": "timeout-provider", "outcome": "timeout"}
    before_total = metric_value(
        "cacanode_ai_rag_answer_seconds_count",
        {"stage": "total", **labels},
    )
    before_llm = metric_value(
        "cacanode_ai_rag_answer_seconds_count",
        {"stage": "llm", **labels},
    )
    service, _, _, _ = make_service(
        chunks=[
            RetrievedChunk(
                document_id="doc-1",
                source_name="policy.txt",
                page_number=1,
                chunk_index=0,
                text="Sản phẩm được đổi trong 7 ngày.",
                score=0.91,
            )
        ],
        model=TimeoutChatModel(),
    )
    session = service.create_session(
        tenant_id="tenant-1",
        user_id="user-1",
        chatbot_id="bot-1",
        knowledge_base_id="kb-1",
        locale="vi-VN",
    )

    with pytest.raises(ChatModelTimeoutError):
        await service.submit_message(
            tenant_id="tenant-1",
            session_id=session.id,
            content="Chinh sach doi tra?",
        )

    assert (
        metric_value("cacanode_ai_rag_answer_seconds_count", {"stage": "total", **labels})
        == before_total + 1
    )
    assert (
        metric_value("cacanode_ai_rag_answer_seconds_count", {"stage": "llm", **labels})
        == before_llm + 1
    )


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


@pytest.mark.asyncio
async def test_employee_cannot_read_or_continue_another_employees_session() -> None:
    service, _, _, _ = make_service(chunks=[])
    session = service.create_session(
        tenant_id="tenant-1", user_id="user-1", chatbot_id="bot-1",
        knowledge_base_id="kb-1", locale="en",
    )

    with pytest.raises(ChatSessionNotFoundError):
        service.list_messages(
            tenant_id="tenant-1", session_id=session.id, user_id="user-2"
        )
    with pytest.raises(ChatSessionNotFoundError):
        await service.submit_message(
            tenant_id="tenant-1", session_id=session.id,
            content="Not my chat", user_id="user-2",
        )


def test_hiding_employee_session_preserves_messages_but_prevents_reopening() -> None:
    service, store, _, _ = make_service(chunks=[])
    session = service.create_session(
        tenant_id="tenant-1", user_id="user-1", chatbot_id="bot-1",
        knowledge_base_id="kb-1", locale="en",
    )
    store.add_user_message(session.id, "Keep this for analytics")

    service.hide_playground_session(
        tenant_id="tenant-1", user_id="user-1", session_id=session.id
    )

    assert len(store._sessions[session.id].messages) == 1
    assert store.get_for_tenant(session.id, "tenant-1") is None
    assert service.list_playground_sessions(
        tenant_id="tenant-1", user_id="user-1", limit=50, offset=0
    ) == []


@pytest.mark.asyncio
async def test_external_retrieval_filters_to_customer_visible_documents() -> None:
    service, store, retriever, _ = make_service(
        chunks=[RetrievedChunk(
            document_id="shared-doc", source_name="shared.txt", page_number=None,
            chunk_index=0, text="Shared answer", score=0.9,
        )]
    )
    store.customer_document_ids = ["shared-doc"]
    session = service.create_session(
        tenant_id="tenant-1", user_id=None, chatbot_id="bot-1",
        knowledge_base_id="kb-1", locale="en", channel="WIDGET",
        integration_token_id="token-1",
    )

    await service.submit_message(
        tenant_id="tenant-1", session_id=session.id, content="Question",
        integration_token_id="token-1",
    )

    assert retriever.calls[0]["document_ids"] == ["shared-doc"]


@pytest.mark.asyncio
async def test_external_retrieval_skips_qdrant_when_no_documents_are_shared() -> None:
    service, _, retriever, model = make_service(chunks=[])
    session = service.create_session(
        tenant_id="tenant-1", user_id=None, chatbot_id="bot-1",
        knowledge_base_id="kb-1", locale="en", channel="CUSTOM_API",
        integration_token_id="token-1",
    )

    await service.submit_message(
        tenant_id="tenant-1", session_id=session.id, content="Question",
        integration_token_id="token-1",
    )

    assert retriever.calls == []
    assert len(model.calls) == 1
