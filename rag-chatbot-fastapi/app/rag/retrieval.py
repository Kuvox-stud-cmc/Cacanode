from __future__ import annotations

import json
import re
import time
from collections.abc import Sequence
from contextvars import ContextVar
from typing import Any

from qdrant_client import AsyncQdrantClient, models

from app.core.config import Settings
from app.core.metrics import AI_RETRIEVAL_SECONDS
from app.graph import GraphSearchRequest, GraphServiceClient
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
                            [
                                models.FieldCondition(
                                    key="document_id", match=models.MatchAny(any=list(document_ids))
                                )
                            ]
                            if document_ids is not None
                            else []
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
            unit_id=str(payload["unit_id"]) if payload.get("unit_id") else None,
            modality=str(payload["modality"]) if payload.get("modality") else None,
            section_path=tuple(str(item) for item in payload.get("section_path", [])),
            block_type=str(payload["block_type"]) if payload.get("block_type") else None,
            sheet_name=str(payload["sheet_name"]) if payload.get("sheet_name") else None,
            cell_range=str(payload["cell_range"]) if payload.get("cell_range") else None,
            table_id=str(payload["table_id"]) if payload.get("table_id") else None,
        )


class HybridRetriever:
    def __init__(
        self, vector: QdrantVectorRetriever, graph: GraphServiceClient, planner: Any = None
    ):
        self._vector = vector
        self._graph = graph
        self._planner = planner
        self._query: ContextVar[str] = ContextVar("graph_query", default="")

    def set_query(self, query: str) -> None:
        self._query.set(query)

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
        vector_results = await self._vector.retrieve(
            tenant_id=tenant_id,
            knowledge_base_id=knowledge_base_id,
            query_vector=query_vector,
            limit=limit,
            score_threshold=score_threshold,
            document_ids=document_ids,
        )
        query_text = self._query.get()
        if not query_text.strip():
            return vector_results
        try:
            rows = await self._graph.search(
                GraphSearchRequest(
                    tenant_id=tenant_id,
                    knowledge_base_id=knowledge_base_id,
                    query=query_text,
                    limit=limit,
                )
            )
            if not rows and self._planner is not None:
                planned_query = await self._planned_entities(query_text)
                if planned_query:
                    rows = await self._graph.search(
                        GraphSearchRequest(
                            tenant_id=tenant_id,
                            knowledge_base_id=knowledge_base_id,
                            query=planned_query,
                            limit=limit,
                        )
                    )
        except Exception:
            rows = []
        allowed = set(document_ids) if document_ids is not None else None
        graph_results = [
            RetrievedChunk(
                document_id=str(row["document_id"]),
                source_name=str(row["source_name"]),
                page_number=int(row["page_number"]) if row.get("page_number") is not None else None,
                chunk_index=0,
                text=str(row["text"]),
                score=float(row.get("score", 1)),
                unit_id=str(row["unit_id"]),
                modality="document",
                section_path=tuple(str(item) for item in row.get("section_path", [])),
                sheet_name=str(row["sheet_name"]) if row.get("sheet_name") else None,
                cell_range=str(row["cell_range"]) if row.get("cell_range") else None,
            )
            for row in rows
            if allowed is None or str(row.get("document_id")) in allowed
        ]
        return _reciprocal_rank_fusion(vector_results, graph_results, limit)

    async def _planned_entities(self, query: str) -> str:
        raw = await self._planner.complete(
            [
                {
                    "role": "system",
                    "content": (
                        "Extract only entity names explicitly present in the query. "
                        "Return strict JSON "
                        'as {"entities":["name"]}. Never produce graph syntax, code, or filters.'
                    ),
                },
                {"role": "user", "content": query},
            ]
        )
        try:
            payload = json.loads(re.sub(r"^```(?:json)?\s*|\s*```$", "", raw.strip()))
            entities = payload.get("entities", [])
            if not isinstance(entities, list):
                return ""
            return " ".join(str(item)[:300] for item in entities[:10])
        except (json.JSONDecodeError, TypeError, ValueError):
            return ""


def _reciprocal_rank_fusion(
    vector: Sequence[RetrievedChunk], graph: Sequence[RetrievedChunk], limit: int, k: int = 60
) -> list[RetrievedChunk]:
    scores: dict[tuple[str, str], float] = {}
    chunks: dict[tuple[str, str], RetrievedChunk] = {}
    for results in (vector, graph):
        for rank, chunk in enumerate(results, start=1):
            key = (chunk.document_id, chunk.unit_id or str(chunk.chunk_index))
            scores[key] = scores.get(key, 0.0) + 1.0 / (k + rank)
            chunks.setdefault(key, chunk)
    ordered = sorted(scores, key=lambda item: (-scores[item], item))[:limit]
    return [
        RetrievedChunk(
            document_id=chunks[key].document_id,
            source_name=chunks[key].source_name,
            page_number=chunks[key].page_number,
            chunk_index=chunks[key].chunk_index,
            text=chunks[key].text,
            score=scores[key],
            unit_id=chunks[key].unit_id,
            modality=chunks[key].modality,
            section_path=chunks[key].section_path,
            block_type=chunks[key].block_type,
            sheet_name=chunks[key].sheet_name,
            cell_range=chunks[key].cell_range,
            table_id=chunks[key].table_id,
        )
        for key in ordered
    ]
