from __future__ import annotations

from app.ingestion.chunking import DeterministicChunker
from app.ingestion.embedding import OllamaEmbeddingClient
from app.ingestion.errors import PermanentIngestionError
from app.ingestion.events import DocumentIngestRequestedEvent
from app.ingestion.extraction import DocumentTextExtractor
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
    ):
        self._store = store
        self._extractor = extractor
        self._chunker = chunker
        self._embedder = embedder
        self._vector_store = vector_store

    async def ingest(self, event: DocumentIngestRequestedEvent) -> int:
        data = await self._store.download(event.storage_key)
        pages = self._extractor.extract(
            data,
            content_type=event.content_type,
            file_name=event.file_name,
        )
        chunks = self._chunker.chunk(pages)
        if not chunks:
            raise PermanentIngestionError("Document contains no extractable text")
        embeddings = await self._embedder.embed_documents([chunk.text for chunk in chunks])
        await self._vector_store.upsert(event, chunks, embeddings)
        return len(chunks)
