from __future__ import annotations

from app.graph import EntityRelationExtractor, GraphBatch, GraphServiceClient, _unit_payload
from app.ingestion.chunking import DeterministicChunker
from app.ingestion.embedding import OllamaEmbeddingClient
from app.ingestion.errors import PermanentIngestionError
from app.ingestion.events import DocumentIngestRequestedEvent
from app.ingestion.extraction import DocumentTextExtractor
from app.ingestion.spreadsheets import parquet_bytes
from app.ingestion.storage import SeaweedS3DocumentStore
from app.ingestion.vector_store import QdrantChunkStore


class DocumentIngestionPipeline:
    def __init__(
        self,
        *,
        store: SeaweedS3DocumentStore,
        extractor: DocumentTextExtractor,
        chunker: DeterministicChunker,
        embedder: OllamaEmbeddingClient,
        vector_store: QdrantChunkStore,
        graph_store: GraphServiceClient,
        graph_extractor: EntityRelationExtractor,
    ):
        self._store = store
        self._extractor = extractor
        self._chunker = chunker
        self._embedder = embedder
        self._vector_store = vector_store
        self._graph_store = graph_store
        self._graph_extractor = graph_extractor

    async def ingest(self, event: DocumentIngestRequestedEvent) -> int:
        data = await self._store.download(event.storage_key)
        parsed = self._extractor.parse(
            data,
            content_type=event.content_type,
            file_name=event.file_name,
        )
        chunks = self._chunker.chunk(parsed)
        if not chunks:
            raise PermanentIngestionError("Document contains no extractable text")
        artifact_keys: list[str] = []
        vector_written = False
        graph_attempted = False
        try:
            for table in parsed.tables:
                key = (
                    f"tenants/{event.tenant_id}/knowledge-bases/{event.knowledge_base_id}/"
                    f"documents/{event.document_id}/tables/{table.table_id}.parquet"
                )
                await self._store.upload_artifact(
                    key, parquet_bytes(table), "application/vnd.apache.parquet"
                )
                artifact_keys.append(key)
            embeddings = await self._embedder.embed_documents([chunk.text for chunk in chunks])
            await self._vector_store.upsert(event, chunks, embeddings)
            vector_written = True
            if parsed.modality == "document":
                batch = await self._graph_extractor.extract(event, chunks)
            else:
                batch = GraphBatch(
                    tenant_id=str(event.tenant_id),
                    knowledge_base_id=str(event.knowledge_base_id),
                    source_id=str(event.document_id),
                    source_name=event.file_name,
                    units=tuple(_unit_payload(chunk) for chunk in chunks),
                )
            graph_attempted = True
            await self._graph_store.replace_source(batch)
            return len(chunks)
        except Exception:
            if vector_written:
                try:
                    await self._vector_store.delete_source(event)
                except Exception:
                    pass
            if graph_attempted:
                try:
                    await self._graph_store.delete_source(
                        str(event.tenant_id), str(event.document_id)
                    )
                except Exception:
                    pass
            for key in artifact_keys:
                try:
                    await self._store.delete_artifact(key)
                except Exception:
                    pass
            raise
