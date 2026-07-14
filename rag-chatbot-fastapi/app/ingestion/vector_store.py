from __future__ import annotations

from collections.abc import Sequence
from uuid import NAMESPACE_URL, uuid5

from qdrant_client import AsyncQdrantClient, models

from app.core.config import Settings
from app.ingestion.chunking import TextChunk
from app.ingestion.errors import PermanentIngestionError, TransientIngestionError
from app.ingestion.events import DocumentIngestRequestedEvent
from app.ingestion.sparse import SparseEmbedding


class QdrantChunkStore:
    def __init__(self, settings: Settings, client: AsyncQdrantClient | None = None):
        self._collection = settings.QDRANT_COLLECTION
        self._dense_vector_name = settings.QDRANT_DENSE_VECTOR_NAME
        self._sparse_vector_name = settings.QDRANT_SPARSE_VECTOR_NAME
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
        sparse_embeddings: Sequence[SparseEmbedding] | None = None,
    ) -> None:
        if len(chunks) != len(embeddings):
            raise PermanentIngestionError("Chunk and embedding counts do not match")
        if not chunks:
            raise PermanentIngestionError("Document contains no chunks to upsert")
        if sparse_embeddings is not None and len(chunks) != len(sparse_embeddings):
            raise PermanentIngestionError("Chunk and sparse embedding counts do not match")

        dimension = len(embeddings[0])
        if any(len(vector) != dimension for vector in embeddings):
            raise PermanentIngestionError("Embedding dimensions are inconsistent")

        try:
            await self.ensure_collection(dimension)
            await self.delete_source(event)
            sparse_values: Sequence[SparseEmbedding | None] = (
                sparse_embeddings if sparse_embeddings is not None else [None] * len(chunks)
            )
            points = [
                models.PointStruct(
                    id=self.point_id(
                        str(event.document_id), chunk.unit_id or str(chunk.chunk_index)
                    ),
                    vector={
                        self._dense_vector_name: list(embedding),
                        **(
                            {
                                self._sparse_vector_name: models.SparseVector(
                                    indices=list(sparse.indices), values=list(sparse.values)
                                )
                            }
                            if sparse is not None
                            else {}
                        ),
                    },
                    payload=self._payload(event, chunk),
                )
                for chunk, embedding, sparse in zip(
                    chunks,
                    embeddings,
                    sparse_values,
                    strict=True,
                )
            ]
            await self._client.upsert(collection_name=self._collection, points=points, wait=True)
        except PermanentIngestionError:
            raise
        except Exception as exc:
            raise TransientIngestionError(
                f"Unable to upsert document chunks into Qdrant: {exc}"
            ) from exc

    async def delete_source(self, event: DocumentIngestRequestedEvent) -> None:
        await self.delete_source_ids(str(event.tenant_id), str(event.document_id))

    async def delete_source_ids(self, tenant_id: str, document_id: str) -> None:
        try:
            if not await self._client.collection_exists(self._collection):
                return
            await self._client.delete(
                collection_name=self._collection,
                points_selector=models.FilterSelector(
                    filter=models.Filter(
                        must=[
                            models.FieldCondition(
                                key=self._tenant_field,
                                match=models.MatchValue(value=tenant_id),
                            ),
                            models.FieldCondition(
                                key="document_id",
                                match=models.MatchValue(value=document_id),
                            ),
                        ]
                    )
                ),
                wait=True,
            )
        except Exception as exc:
            raise TransientIngestionError(
                f"Unable to delete existing document vectors: {exc}"
            ) from exc

    async def ensure_collection(self, dimension: int) -> None:
        exists = await self._client.collection_exists(self._collection)
        if not exists:
            await self._client.create_collection(
                collection_name=self._collection,
                vectors_config={
                    self._dense_vector_name: models.VectorParams(
                        size=dimension, distance=models.Distance.COSINE
                    )
                },
                sparse_vectors_config={
                    self._sparse_vector_name: models.SparseVectorParams(
                        modifier=models.Modifier.IDF
                    )
                },
            )
            await self._ensure_payload_indexes()
            return

        info = await self._client.get_collection(self._collection)
        current = self._collection_dimension(info)
        if current != dimension:
            raise PermanentIngestionError(
                "Qdrant collection dimension is "
                f"{current}, received embeddings with dimension {dimension}"
            )
        sparse_vectors = getattr(info.config.params, "sparse_vectors", None)
        if not isinstance(sparse_vectors, dict) or self._sparse_vector_name not in sparse_vectors:
            raise PermanentIngestionError(
                f"Qdrant collection is missing sparse vector {self._sparse_vector_name}"
            )

    async def _ensure_payload_indexes(self) -> None:
        for field_name, schema in (
            (self._tenant_field, models.PayloadSchemaType.KEYWORD),
            (self._knowledge_base_field, models.PayloadSchemaType.KEYWORD),
            ("document_id", models.PayloadSchemaType.KEYWORD),
            ("chunk_index", models.PayloadSchemaType.INTEGER),
        ):
            await self._client.create_payload_index(
                collection_name=self._collection,
                field_name=field_name,
                field_schema=schema,
                wait=True,
            )

    def _collection_dimension(self, info: object) -> int:
        vectors = info.config.params.vectors  # type: ignore[attr-defined]
        if isinstance(vectors, dict):
            dense = vectors.get(self._dense_vector_name)
            if dense is None:
                raise PermanentIngestionError(
                    f"Qdrant collection is missing dense vector {self._dense_vector_name}"
                )
            return int(dense.size)
        raise PermanentIngestionError(
            f"Qdrant collection {self._collection} uses the legacy unnamed-vector schema; "
            "set QDRANT_COLLECTION=knowledge_units_v2 and reindex documents"
        )

    def _payload(self, event: DocumentIngestRequestedEvent, chunk: TextChunk) -> dict[str, object]:
        return {
            self._tenant_field: str(event.tenant_id),
            self._knowledge_base_field: str(event.knowledge_base_id),
            "document_id": str(event.document_id),
            "source_name": event.file_name,
            "page_number": chunk.page_number,
            "chunk_index": chunk.chunk_index,
            "unit_id": chunk.unit_id,
            "modality": chunk.modality,
            "section_path": list(chunk.section_path),
            "block_type": chunk.block_type,
            "heading_context": chunk.heading_context,
            "sheet_name": chunk.sheet_name,
            "cell_range": chunk.cell_range,
            "table_id": chunk.table_id,
            "source_start": chunk.source_start,
            "source_end": chunk.source_end,
            "parser_version": chunk.parser_version,
            "chunker_version": chunk.chunker_version,
            "text": chunk.text,
            "content_hash": chunk.content_hash,
        }

    @staticmethod
    def point_id(document_id: str, unit_identity: int | str) -> str:
        return str(uuid5(NAMESPACE_URL, f"{document_id}:{unit_identity}"))
