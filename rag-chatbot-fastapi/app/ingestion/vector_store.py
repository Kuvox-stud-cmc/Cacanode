from __future__ import annotations

from collections.abc import Sequence
from uuid import NAMESPACE_URL, uuid5

from qdrant_client import AsyncQdrantClient, models

from app.core.config import Settings
from app.ingestion.chunking import TextChunk
from app.ingestion.errors import PermanentIngestionError, TransientIngestionError
from app.ingestion.events import DocumentIngestRequestedEvent


class QdrantChunkStore:
    def __init__(self, settings: Settings, client: AsyncQdrantClient | None = None):
        self._collection = settings.QDRANT_COLLECTION
        self._tenant_field = settings.QDRANT_TENANT_FIELD
        self._knowledge_base_field = settings.QDRANT_KNOWLEDGE_BASE_FIELD
        self._client = client or AsyncQdrantClient(
            url=settings.QDRANT_URL,
            api_key=settings.QDRANT_API_KEY or None,
            check_compatibility=False,
        )

    async def upsert(
        self,
        event: DocumentIngestRequestedEvent,
        chunks: Sequence[TextChunk],
        embeddings: Sequence[Sequence[float]],
    ) -> None:
        if len(chunks) != len(embeddings):
            raise PermanentIngestionError("Chunk and embedding counts do not match")
        if not chunks:
            raise PermanentIngestionError("Document contains no chunks to upsert")

        dimension = len(embeddings[0])
        if any(len(vector) != dimension for vector in embeddings):
            raise PermanentIngestionError("Embedding dimensions are inconsistent")

        try:
            await self.ensure_collection(dimension)
            points = [
                models.PointStruct(
                    id=self.point_id(str(event.document_id), chunk.chunk_index),
                    vector=list(embedding),
                    payload=self._payload(event, chunk),
                )
                for chunk, embedding in zip(chunks, embeddings, strict=True)
            ]
            await self._client.upsert(collection_name=self._collection, points=points, wait=True)
        except PermanentIngestionError:
            raise
        except Exception as exc:
            raise TransientIngestionError(
                f"Unable to upsert document chunks into Qdrant: {exc}"
            ) from exc

    async def ensure_collection(self, dimension: int) -> None:
        exists = await self._client.collection_exists(self._collection)
        if not exists:
            await self._client.create_collection(
                collection_name=self._collection,
                vectors_config=models.VectorParams(size=dimension, distance=models.Distance.COSINE),
            )
            return

        info = await self._client.get_collection(self._collection)
        current = self._collection_dimension(info)
        if current != dimension:
            raise PermanentIngestionError(
                "Qdrant collection dimension is "
                f"{current}, received embeddings with dimension {dimension}"
            )

    def _collection_dimension(self, info: object) -> int:
        vectors = info.config.params.vectors  # type: ignore[attr-defined]
        if isinstance(vectors, dict):
            first = next(iter(vectors.values()))
            return int(first.size)
        return int(vectors.size)

    def _payload(self, event: DocumentIngestRequestedEvent, chunk: TextChunk) -> dict[str, object]:
        return {
            self._tenant_field: str(event.tenant_id),
            self._knowledge_base_field: str(event.knowledge_base_id),
            "document_id": str(event.document_id),
            "source_name": event.file_name,
            "page_number": chunk.page_number,
            "chunk_index": chunk.chunk_index,
            "text": chunk.text,
            "content_hash": chunk.content_hash,
        }

    @staticmethod
    def point_id(document_id: str, chunk_index: int) -> str:
        return str(uuid5(NAMESPACE_URL, f"{document_id}:{chunk_index}"))
