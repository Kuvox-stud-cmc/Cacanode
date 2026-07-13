from __future__ import annotations

import json
from datetime import UTC, datetime
from types import SimpleNamespace
from typing import Any, cast
from uuid import UUID, uuid4

import pytest
from prometheus_client import REGISTRY

from app.core.config import Settings
from app.ingestion.chunking import DeterministicChunker
from app.ingestion.embedding import OllamaEmbeddingClient
from app.ingestion.errors import PermanentIngestionError, TransientIngestionError
from app.ingestion.events import DocumentIngestRequestedEvent
from app.ingestion.extraction import DocumentTextExtractor, ExtractedPage
from app.ingestion.vector_store import QdrantChunkStore
from app.workers import document as document_worker
from app.workers.document import DocumentWorker


def metric_value(name: str, labels: dict[str, str]) -> float:
    return REGISTRY.get_sample_value(name, labels) or 0.0


def event_payload(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "schema_version": "1.0",
        "event_id": str(uuid4()),
        "job_id": str(uuid4()),
        "tenant_id": str(uuid4()),
        "knowledge_base_id": str(uuid4()),
        "document_id": str(uuid4()),
        "uploader_id": str(uuid4()),
        "storage_key": "tenants/t/kb/doc/file.txt",
        "file_name": "file.txt",
        "content_type": "text/plain",
        "file_size_bytes": 12,
        "occurred_at": datetime.now(UTC).isoformat(),
    }
    payload.update(overrides)
    return payload


def event_bytes(**overrides: object) -> bytes:
    return json.dumps(event_payload(**overrides)).encode("utf-8")


def minimal_pdf(text: str | None) -> bytes:
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    ]
    if text is None:
        objects.append(b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>")
    else:
        stream = f"BT /F1 24 Tf 72 720 Td ({text}) Tj ET".encode("ascii")
        objects.append(
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            b"/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>"
        )
        objects.append(
            b"<< /Length "
            + str(len(stream)).encode("ascii")
            + b" >>\nstream\n"
            + stream
            + b"\nendstream"
        )
        objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    pdf = b"%PDF-1.4\n"
    offsets: list[int] = []
    for index, obj in enumerate(objects, start=1):
        offsets.append(len(pdf))
        pdf += f"{index} 0 obj\n".encode("ascii") + obj + b"\nendobj\n"
    xref = len(pdf)
    pdf += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode("ascii")
    for offset in offsets:
        pdf += f"{offset:010d} 00000 n \n".encode("ascii")
    pdf += (
        f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
        f"startxref\n{xref}\n%%EOF\n"
    ).encode("ascii")
    return pdf


def test_event_parsing_accepts_spring_contract() -> None:
    event = DocumentIngestRequestedEvent.parse_payload(event_bytes())

    assert event.schema_version == "1.0"
    assert isinstance(event.job_id, UUID)
    assert event.storage_key == "tenants/t/kb/doc/file.txt"


def test_event_parsing_rejects_malformed_required_fields() -> None:
    with pytest.raises(PermanentIngestionError, match="Invalid document ingestion event"):
        DocumentIngestRequestedEvent.parse_payload(event_bytes(document_id=None))


def test_txt_extraction_uses_strict_utf8() -> None:
    extractor = DocumentTextExtractor()

    pages = extractor.extract(b"hello\nworld", content_type="text/plain", file_name="file.txt")

    assert pages == [ExtractedPage(page_number=1, text="hello\nworld")]
    with pytest.raises(PermanentIngestionError, match="not valid UTF-8"):
        extractor.extract(b"\xff", content_type="text/plain", file_name="bad.txt")


def test_pdf_extraction_and_no_text_failure() -> None:
    pytest.importorskip("pypdf")
    extractor = DocumentTextExtractor()

    pages = extractor.extract(
        minimal_pdf("Hello PDF text"),
        content_type="application/pdf",
        file_name="file.pdf",
    )

    assert pages[0].page_number == 1
    assert "Hello PDF text" in pages[0].text
    with pytest.raises(PermanentIngestionError, match="no extractable text"):
        extractor.extract(minimal_pdf(None), content_type="application/pdf", file_name="empty.pdf")


def test_chunking_is_deterministic_page_aware_and_hashes_content() -> None:
    chunker = DeterministicChunker(chunk_size=40, overlap=10)
    pages = [
        ExtractedPage(page_number=2, text="alpha " * 20),
        ExtractedPage(page_number=3, text="beta " * 4),
    ]

    first = chunker.chunk(pages)
    second = chunker.chunk(pages)

    assert first == second
    assert {chunk.page_number for chunk in first} == {2, 3}
    assert [chunk.chunk_index for chunk in first] == list(range(len(first)))
    assert all(len(chunk.content_hash) == 64 for chunk in first)


class FakeOllamaResponse:
    def __init__(self, payload: dict[str, object], status_code: int = 200):
        self._payload = payload
        self.status_code = status_code

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
async def test_embedding_adapter_parses_ollama_embed_response(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    FakeOllamaClient.payload = {"embeddings": [[1, 2, 3], [4, 5, 6]]}
    monkeypatch.setattr("app.ingestion.embedding.httpx.AsyncClient", FakeOllamaClient)
    embedder = OllamaEmbeddingClient(
        Settings(TEXT_EMBEDDING_DIMENSION=3, TEXT_EMBEDDING_BATCH_SIZE=10)
    )
    labels = {"operation": "documents", "provider": "ollama", "outcome": "success"}
    before = metric_value("cacanode_ai_embedding_seconds_count", labels)

    embeddings = await embedder.embed_documents(["a", "b"])

    assert embeddings == [[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]
    assert metric_value("cacanode_ai_embedding_seconds_count", labels) == before + 1


@pytest.mark.asyncio
async def test_embedding_adapter_reports_model_errors(monkeypatch: pytest.MonkeyPatch) -> None:
    FakeOllamaClient.payload = {"error": "model not found"}
    monkeypatch.setattr("app.ingestion.embedding.httpx.AsyncClient", FakeOllamaClient)
    embedder = OllamaEmbeddingClient(Settings(TEXT_EMBEDDING_DIMENSION=3))

    with pytest.raises(TransientIngestionError, match="model not found"):
        await embedder.embed_documents(["a"])


class FakeQdrantClient:
    def __init__(self, exists: bool = False, dimension: int = 3):
        self.exists = exists
        self.dimension = dimension
        self.created: object | None = None
        self.upserted: list[object] = []
        self.deleted: object | None = None

    async def collection_exists(self, collection_name: str) -> bool:
        self.collection_name = collection_name
        return self.exists

    async def create_collection(self, collection_name: str, vectors_config: object) -> None:
        self.created = (collection_name, vectors_config)
        self.exists = True

    async def get_collection(self, collection_name: str) -> object:
        del collection_name
        return SimpleNamespace(
            config=SimpleNamespace(params=SimpleNamespace(vectors=SimpleNamespace(size=self.dimension)))
        )

    async def upsert(self, collection_name: str, points: list[object], wait: bool) -> None:
        self.upserted = points
        self.upsert_collection = collection_name
        self.upsert_wait = wait

    async def delete(self, collection_name: str, points_selector: object, wait: bool) -> None:
        self.deleted = (collection_name, points_selector, wait)


@pytest.mark.asyncio
async def test_qdrant_adapter_creates_collection_and_upserts_payloads() -> None:
    client = FakeQdrantClient(exists=False)
    store = QdrantChunkStore(Settings(QDRANT_COLLECTION="chunks"), client=client)  # type: ignore[arg-type]
    event = DocumentIngestRequestedEvent.parse_payload(event_bytes())
    chunks = DeterministicChunker().chunk([ExtractedPage(page_number=1, text="hello")])

    await store.upsert(event, chunks, [[0.1, 0.2, 0.3]])

    assert client.created is not None
    assert len(client.upserted) == 1
    point = cast(Any, client.upserted[0])
    assert point.id == QdrantChunkStore.point_id(str(event.document_id), chunks[0].unit_id)
    assert point.payload["tenant_id"] == str(event.tenant_id)
    assert point.payload["knowledge_base_id"] == str(event.knowledge_base_id)
    assert point.payload["text"] == "hello"


@pytest.mark.asyncio
async def test_qdrant_adapter_rejects_collection_dimension_mismatch() -> None:
    client = FakeQdrantClient(exists=True, dimension=4)
    store = QdrantChunkStore(Settings(QDRANT_COLLECTION="chunks"), client=client)  # type: ignore[arg-type]

    with pytest.raises(PermanentIngestionError, match="collection dimension"):
        await store.ensure_collection(3)


class FakeExchange:
    def __init__(self) -> None:
        self.published: list[tuple[str, dict[str, object], dict[str, object]]] = []

    async def publish(self, message: object, routing_key: str, mandatory: bool) -> None:
        self.published.append(
            (
                routing_key,
                json.loads(message.body.decode("utf-8")),  # type: ignore[attr-defined]
                dict(message.headers or {}),  # type: ignore[attr-defined]
            )
        )
        assert mandatory is True


class FakeMessage:
    def __init__(self, body: bytes, headers: dict[str, object] | None = None):
        self.body = body
        self.headers = headers or {}
        self.content_type = "application/json"
        self.content_encoding = "utf-8"
        self.message_id = "message-id"
        self.correlation_id = "correlation-id"
        self.acked = False
        self.rejected = False
        self.requeue: bool | None = None

    async def ack(self) -> None:
        self.acked = True

    async def reject(self, requeue: bool) -> None:
        self.rejected = True
        self.requeue = requeue


class SuccessfulPipeline:
    async def ingest(self, event: DocumentIngestRequestedEvent) -> int:
        self.event = event
        return 2


class PermanentFailurePipeline:
    async def ingest(self, event: DocumentIngestRequestedEvent) -> int:
        del event
        raise PermanentIngestionError("bad document")


class TransientFailurePipeline:
    async def ingest(self, event: DocumentIngestRequestedEvent) -> int:
        del event
        raise TransientIngestionError("qdrant unavailable")


def worker_with(pipeline: object) -> tuple[DocumentWorker, FakeExchange]:
    worker = DocumentWorker(Settings(), pipeline=pipeline)  # type: ignore[arg-type]
    exchange = FakeExchange()
    worker._exchange = exchange
    return worker, exchange


@pytest.mark.asyncio
async def test_worker_success_path_publishes_statuses_and_acks() -> None:
    worker, exchange = worker_with(SuccessfulPipeline())
    message = FakeMessage(event_bytes())

    await worker.handle_message(message)  # type: ignore[arg-type]

    assert message.acked is True
    assert message.rejected is False
    assert [item[0] for item in exchange.published] == [
        document_worker.INGEST_PROCESSING,
        document_worker.INGEST_COMPLETED,
    ]
    assert exchange.published[1][1]["chunk_count"] == 2
    assert exchange.published[1][1]["status"] == "COMPLETED"


@pytest.mark.asyncio
async def test_worker_permanent_failure_publishes_failed_and_rejects_to_dlq() -> None:
    worker, exchange = worker_with(PermanentFailurePipeline())
    message = FakeMessage(event_bytes())

    await worker.handle_message(message)  # type: ignore[arg-type]

    assert message.acked is False
    assert message.rejected is True
    assert message.requeue is False
    assert [item[0] for item in exchange.published] == [
        document_worker.INGEST_PROCESSING,
        document_worker.INGEST_FAILED,
    ]
    assert exchange.published[-1][1]["error_message"] == "bad document"


@pytest.mark.asyncio
async def test_worker_transient_failure_retries_then_dead_letters() -> None:
    worker, exchange = worker_with(TransientFailurePipeline())
    message = FakeMessage(event_bytes())

    await worker.handle_message(message)  # type: ignore[arg-type]

    assert message.acked is True
    assert message.rejected is False
    assert exchange.published[-1][0] == document_worker.INGEST_REQUESTED
    assert exchange.published[-1][2][document_worker.RETRY_HEADER] == 1

    exhausted = FakeMessage(
        event_bytes(),
        headers={document_worker.RETRY_HEADER: document_worker.MAX_TRANSIENT_RETRIES},
    )
    await worker.handle_message(exhausted)  # type: ignore[arg-type]

    assert exhausted.rejected is True
    assert exhausted.requeue is False
    assert exchange.published[-1][0] == document_worker.INGEST_FAILED
    assert exchange.published[-1][1]["error_message"] == "qdrant unavailable"
