from __future__ import annotations

import time
from collections.abc import Sequence
from typing import Any

from qdrant_client import AsyncQdrantClient, models

from app.core.config import Settings
from app.core.metrics import AI_RETRIEVAL_SECONDS
from app.rag.models import RetrievedChunk


class QdrantVectorRetriever:
    def __init__(self, settings: Settings, client: AsyncQdrantClient | None = None):
        self._collection = settings.QDRANT_COLLECTION
        self._tenant_field = settings.QDRANT_TENANT_FIELD
        self._knowledge_base_field = settings.QDRANT_KNOWLEDGE_BASE_FIELD
        self._client = client or AsyncQdrantClient(
            url=settings.QDRANT_URL,
            api_key=settings.QDRANT_API_KEY or None,
            check_compatibility=False,
        )

    async def retrieve(
        self,
        *,
        tenant_id: str,
        knowledge_base_id: str,
        query_vector: Sequence[float],
        limit: int,
        score_threshold: float,
        document_ids: Sequence[str] | None = None,
    ) -> list[RetrievedChunk]:
        started_at = time.perf_counter()
        outcome = "success"
        try:
            response = await self._client.query_points(
                collection_name=self._collection,
                query=list(query_vector),
                query_filter=models.Filter(
                    must=[
                        models.FieldCondition(
                            key=self._tenant_field,
                            match=models.MatchValue(value=tenant_id),
                        ),
                        *(
                            [models.FieldCondition(
                                key="document_id", match=models.MatchAny(any=list(document_ids))
                            )]
                            if document_ids is not None else []
                        ),
                        models.FieldCondition(
                            key=self._knowledge_base_field,
                            match=models.MatchValue(value=knowledge_base_id),
                        ),
                    ]
                ),
                limit=limit,
                with_payload=True,
                with_vectors=False,
                score_threshold=score_threshold if score_threshold > 0 else None,
            )
            return [chunk for point in response.points if (chunk := self._chunk_from_point(point))]
        except Exception:
            outcome = "error"
            raise
        finally:
            AI_RETRIEVAL_SECONDS.labels(
                provider="qdrant",
                outcome=outcome,
            ).observe(time.perf_counter() - started_at)

    def _chunk_from_point(self, point: Any) -> RetrievedChunk | None:
        payload = point.payload or {}
        text = payload.get("text")
        document_id = payload.get("document_id")
        source_name = payload.get("source_name")
        chunk_index = payload.get("chunk_index")
        if text is None or document_id is None or source_name is None or chunk_index is None:
            return None

        page_number = payload.get("page_number")
        return RetrievedChunk(
            document_id=str(document_id),
            source_name=str(source_name),
            page_number=int(page_number) if page_number is not None else None,
            chunk_index=int(chunk_index),
            text=str(text),
            score=float(point.score),
        )
