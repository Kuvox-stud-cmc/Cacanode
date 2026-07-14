from __future__ import annotations

import asyncio
import re
import time
from collections import Counter
from collections.abc import Sequence
from dataclasses import dataclass, replace
from enum import StrEnum
from typing import Any

from qdrant_client import AsyncQdrantClient, models

from app.core.config import Settings
from app.core.metrics import (
    AI_CONTEXT_UNITS,
    AI_FUSION_CANDIDATES,
    AI_RETRIEVAL_CHANNEL_RESULTS,
    AI_RETRIEVAL_CHANNEL_SECONDS,
    AI_RETRIEVAL_FALLBACKS_TOTAL,
    AI_ROUTER_PROFILES_TOTAL,
)
from app.graph import GraphSearchRequest, GraphServiceClient
from app.ingestion.sparse import FastEmbedSparseEncoder, SparseEmbedding
from app.rag.models import RetrievedChunk
from app.rag.reranking import Reranker


class QueryProfile(StrEnum):
    CALCULATION = "calculation"
    RELATIONAL = "relational"
    EXACT = "exact"
    SEMANTIC = "semantic"


@dataclass(frozen=True, slots=True)
class RetrievalWeights:
    dense: float
    sparse: float
    graph: float


class QueryRouter:
    """Deterministic Vietnamese-friendly router with explicit precedence."""

    _CALCULATION = re.compile(
        r"(?:\b(?:sum|total|average|avg|count|minimum|maximum|top|bottom)\b|"
        r"tính|tổng|trung bình|đếm|lớn nhất|nhỏ nhất|cao nhất|thấp nhất|"
        r"phần trăm|tỷ lệ|doanh thu|chi phí|bảng tính|spreadsheet|excel)",
        re.IGNORECASE,
    )
    _RELATIONAL = re.compile(
        r"(?:quan hệ|liên quan|kết nối|thuộc về|phụ thuộc|giữa .+ và|"
        r"relationship|related to|connected to|depends on|between .+ and)",
        re.IGNORECASE,
    )
    _QUOTED = re.compile(r"[\"“”'‘’][^\"“”'‘’]{2,}[\"“”'‘’]")
    _IDENTIFIER = re.compile(
        r"(?:\b[A-Z]{2,}[\-_]?\d+[A-Z0-9\-_]*\b|\b\d+[A-Z][A-Z0-9\-_]*\b|"
        r"\b(?:SKU|MÃ|CODE|ID|MST|SĐT|PHONE|EMAIL)\s*[:#-]?\s*\S+)",
        re.IGNORECASE,
    )
    _PRICE = re.compile(
        r"(?:\b\d[\d.,]*\s*(?:₫|đ|vnd|usd|eur|dollar|triệu|nghìn|ngàn)\b|"
        r"(?:giá|price|cost)\s+(?:của|of|là|is)?)",
        re.IGNORECASE,
    )

    def __init__(self, settings: Settings):
        self._weights = {
            QueryProfile.SEMANTIC: RetrievalWeights(
                settings.SEMANTIC_DENSE_WEIGHT,
                settings.SEMANTIC_SPARSE_WEIGHT,
                settings.SEMANTIC_GRAPH_WEIGHT,
            ),
            QueryProfile.EXACT: RetrievalWeights(
                settings.EXACT_DENSE_WEIGHT,
                settings.EXACT_SPARSE_WEIGHT,
                settings.EXACT_GRAPH_WEIGHT,
            ),
            QueryProfile.RELATIONAL: RetrievalWeights(
                settings.RELATIONAL_DENSE_WEIGHT,
                settings.RELATIONAL_SPARSE_WEIGHT,
                settings.RELATIONAL_GRAPH_WEIGHT,
            ),
            QueryProfile.CALCULATION: RetrievalWeights(
                settings.CALCULATION_DENSE_WEIGHT,
                settings.CALCULATION_SPARSE_WEIGHT,
                settings.CALCULATION_GRAPH_WEIGHT,
            ),
        }

    def route(self, query_text: str) -> QueryProfile:
        if self._CALCULATION.search(query_text):
            return QueryProfile.CALCULATION
        if self._RELATIONAL.search(query_text):
            return QueryProfile.RELATIONAL
        if (
            self._QUOTED.search(query_text)
            or self._IDENTIFIER.search(query_text)
            or self._PRICE.search(query_text)
        ):
            return QueryProfile.EXACT
        return QueryProfile.SEMANTIC

    def weights(self, profile: QueryProfile) -> RetrievalWeights:
        return self._weights[profile]


class _QdrantRetrieverBase:
    def __init__(self, settings: Settings, client: AsyncQdrantClient | None = None):
        self._collection = settings.QDRANT_COLLECTION
        self._tenant_field = settings.QDRANT_TENANT_FIELD
        self._knowledge_base_field = settings.QDRANT_KNOWLEDGE_BASE_FIELD
        self._client = client or AsyncQdrantClient(
            url=settings.QDRANT_URL,
            api_key=settings.QDRANT_API_KEY or None,
            check_compatibility=False,
        )

    def _filter(
        self,
        tenant_id: str,
        knowledge_base_id: str,
        document_ids: Sequence[str] | None,
        extra: Sequence[models.Condition] = (),
    ) -> models.Filter:
        conditions: list[models.Condition] = [
            models.FieldCondition(key=self._tenant_field, match=models.MatchValue(value=tenant_id)),
            models.FieldCondition(
                key=self._knowledge_base_field,
                match=models.MatchValue(value=knowledge_base_id),
            ),
        ]
        if document_ids is not None:
            conditions.append(
                models.FieldCondition(
                    key="document_id", match=models.MatchAny(any=list(document_ids))
                )
            )
        conditions.extend(extra)
        return models.Filter(must=conditions)

    @staticmethod
    def _chunk_from_point(point: Any) -> RetrievedChunk | None:
        payload = point.payload or {}
        required = ("text", "document_id", "source_name", "chunk_index")
        if any(payload.get(name) is None for name in required):
            return None
        page_number = payload.get("page_number")
        return RetrievedChunk(
            document_id=str(payload["document_id"]),
            source_name=str(payload["source_name"]),
            page_number=int(page_number) if page_number is not None else None,
            chunk_index=int(payload["chunk_index"]),
            text=str(payload["text"]),
            score=float(getattr(point, "score", 0.0) or 0.0),
            unit_id=str(payload["unit_id"]) if payload.get("unit_id") else None,
            modality=str(payload["modality"]) if payload.get("modality") else None,
            section_path=tuple(str(item) for item in payload.get("section_path", [])),
            block_type=str(payload["block_type"]) if payload.get("block_type") else None,
            sheet_name=str(payload["sheet_name"]) if payload.get("sheet_name") else None,
            cell_range=str(payload["cell_range"]) if payload.get("cell_range") else None,
            table_id=str(payload["table_id"]) if payload.get("table_id") else None,
        )


class QdrantVectorRetriever(_QdrantRetrieverBase):
    def __init__(self, settings: Settings, client: AsyncQdrantClient | None = None):
        super().__init__(settings, client)
        self._vector_name = settings.QDRANT_DENSE_VECTOR_NAME

    async def retrieve(
        self,
        *,
        tenant_id: str,
        knowledge_base_id: str,
        query_vector: Sequence[float],
        limit: int,
        query_text: str = "",
        document_ids: Sequence[str] | None = None,
    ) -> list[RetrievedChunk]:
        del query_text
        response = await self._client.query_points(
            collection_name=self._collection,
            query=list(query_vector),
            using=self._vector_name,
            query_filter=self._filter(tenant_id, knowledge_base_id, document_ids),
            limit=limit,
            with_payload=True,
            with_vectors=False,
        )
        return [chunk for point in response.points if (chunk := self._chunk_from_point(point))]


class QdrantSparseRetriever(_QdrantRetrieverBase):
    def __init__(
        self,
        settings: Settings,
        encoder: FastEmbedSparseEncoder | None = None,
        client: AsyncQdrantClient | None = None,
    ):
        super().__init__(settings, client)
        self._vector_name = settings.QDRANT_SPARSE_VECTOR_NAME
        self._encoder = encoder or FastEmbedSparseEncoder(settings)

    async def retrieve(
        self,
        *,
        tenant_id: str,
        knowledge_base_id: str,
        query_text: str,
        limit: int,
        document_ids: Sequence[str] | None = None,
    ) -> list[RetrievedChunk]:
        vector = await self._encoder.embed_query(query_text)
        response = await self._client.query_points(
            collection_name=self._collection,
            query=_qdrant_sparse(vector),
            using=self._vector_name,
            query_filter=self._filter(tenant_id, knowledge_base_id, document_ids),
            limit=limit,
            with_payload=True,
            with_vectors=False,
        )
        return [chunk for point in response.points if (chunk := self._chunk_from_point(point))]


class QdrantNeighborLoader(_QdrantRetrieverBase):
    _ELIGIBLE_BLOCK_TYPES = {"paragraph", "page", "quote"}

    async def load(
        self,
        *,
        tenant_id: str,
        knowledge_base_id: str,
        primary: RetrievedChunk,
        document_ids: Sequence[str] | None = None,
    ) -> list[RetrievedChunk]:
        if primary.block_type not in self._ELIGIBLE_BLOCK_TYPES or primary.modality not in {
            None,
            "document",
        }:
            return []
        allowed = set(document_ids) if document_ids is not None else None
        if allowed is not None and primary.document_id not in allowed:
            return []
        response = await self._client.scroll(
            collection_name=self._collection,
            scroll_filter=self._filter(
                tenant_id,
                knowledge_base_id,
                [primary.document_id],
                [
                    models.FieldCondition(
                        key="chunk_index",
                        match=models.MatchAny(
                            any=[primary.chunk_index - 1, primary.chunk_index + 1]
                        ),
                    )
                ],
            ),
            limit=4,
            with_payload=True,
            with_vectors=False,
        )
        points = response[0] if isinstance(response, tuple) else response.points
        neighbors = [
            chunk
            for point in points
            if (chunk := self._chunk_from_point(point))
            and chunk.section_path == primary.section_path
            and chunk.block_type in self._ELIGIBLE_BLOCK_TYPES
            and chunk.modality in {None, "document"}
        ]
        return sorted(
            neighbors,
            key=lambda item: (abs(item.chunk_index - primary.chunk_index), item.chunk_index),
        )


class HybridRetriever:
    def __init__(
        self,
        *,
        settings: Settings,
        dense: QdrantVectorRetriever,
        sparse: QdrantSparseRetriever,
        graph: GraphServiceClient,
        reranker: Reranker | None = None,
        neighbor_loader: QdrantNeighborLoader | None = None,
        router: QueryRouter | None = None,
    ):
        self._settings = settings
        self._dense = dense
        self._sparse = sparse
        self._graph = graph
        self._reranker = reranker
        self._neighbors = neighbor_loader
        self._router = router or QueryRouter(settings)

    async def retrieve(
        self,
        *,
        tenant_id: str,
        knowledge_base_id: str,
        query_text: str,
        query_vector: Sequence[float],
        document_ids: Sequence[str] | None = None,
    ) -> list[RetrievedChunk]:
        profile = self._router.route(query_text)
        weights = self._router.weights(profile)
        AI_ROUTER_PROFILES_TOTAL.labels(profile=profile.value).inc()

        channel_results = await asyncio.gather(
            self._timed_channel(
                "dense",
                self._dense.retrieve(
                    tenant_id=tenant_id,
                    knowledge_base_id=knowledge_base_id,
                    query_text=query_text,
                    query_vector=query_vector,
                    limit=self._settings.DENSE_CANDIDATE_COUNT,
                    document_ids=document_ids,
                ),
            ),
            self._timed_channel(
                "sparse",
                self._sparse.retrieve(
                    tenant_id=tenant_id,
                    knowledge_base_id=knowledge_base_id,
                    query_text=query_text,
                    limit=self._settings.SPARSE_CANDIDATE_COUNT,
                    document_ids=document_ids,
                ),
            ),
            self._timed_channel(
                "graph",
                self._graph_results(
                    tenant_id=tenant_id,
                    knowledge_base_id=knowledge_base_id,
                    query_text=query_text,
                    document_ids=document_ids,
                ),
            ),
            return_exceptions=True,
        )
        normalized: list[list[RetrievedChunk]] = []
        for channel, result in zip(("dense", "sparse", "graph"), channel_results, strict=True):
            if isinstance(result, BaseException):
                AI_RETRIEVAL_FALLBACKS_TOTAL.labels(component=channel).inc()
                normalized.append([])
            else:
                normalized.append(result)
        dense_results, sparse_results, graph_results = normalized
        fused = weighted_reciprocal_rank_fusion(
            dense_results,
            sparse_results,
            graph_results,
            weights=weights,
            limit=self._settings.FUSION_CANDIDATE_COUNT,
            k=self._settings.RRF_K,
        )
        AI_FUSION_CANDIDATES.observe(len(fused))
        reranked = await self._rerank(query_text, fused)
        primary = select_diverse_evidence(
            reranked,
            limit=self._settings.PRIMARY_CONTEXT_TOP_K,
            per_document_soft_limit=self._settings.CONTEXT_DOCUMENT_SOFT_LIMIT,
        )
        final = await self._expand_neighbors(
            tenant_id=tenant_id,
            knowledge_base_id=knowledge_base_id,
            primary=primary,
            document_ids=document_ids,
        )
        AI_CONTEXT_UNITS.observe(len(final))
        return final

    async def _timed_channel(self, channel: str, operation: Any) -> list[RetrievedChunk]:
        started_at = time.perf_counter()
        outcome = "success"
        try:
            result = await operation
            AI_RETRIEVAL_CHANNEL_RESULTS.labels(channel=channel).observe(len(result))
            return result
        except Exception:
            outcome = "error"
            raise
        finally:
            AI_RETRIEVAL_CHANNEL_SECONDS.labels(channel=channel, outcome=outcome).observe(
                time.perf_counter() - started_at
            )

    async def _graph_results(
        self,
        *,
        tenant_id: str,
        knowledge_base_id: str,
        query_text: str,
        document_ids: Sequence[str] | None,
    ) -> list[RetrievedChunk]:
        rows = await self._graph.search(
            GraphSearchRequest(
                tenant_id=tenant_id,
                knowledge_base_id=knowledge_base_id,
                query=query_text,
                limit=self._settings.GRAPH_CANDIDATE_COUNT,
            )
        )
        allowed = set(document_ids) if document_ids is not None else None
        return [
            RetrievedChunk(
                document_id=str(row["document_id"]),
                source_name=str(row["source_name"]),
                page_number=(
                    int(row["page_number"]) if row.get("page_number") is not None else None
                ),
                chunk_index=int(row.get("chunk_index", 0)),
                text=str(row["text"]),
                score=float(row.get("score", 1.0)),
                unit_id=str(row["unit_id"]),
                modality=str(row.get("modality", "document")),
                section_path=tuple(str(item) for item in row.get("section_path", [])),
                block_type=str(row["block_type"]) if row.get("block_type") else None,
                sheet_name=str(row["sheet_name"]) if row.get("sheet_name") else None,
                cell_range=str(row["cell_range"]) if row.get("cell_range") else None,
                table_id=str(row["table_id"]) if row.get("table_id") else None,
            )
            for row in rows
            if allowed is None or str(row.get("document_id")) in allowed
        ]

    async def _rerank(
        self, query_text: str, candidates: list[RetrievedChunk]
    ) -> list[RetrievedChunk]:
        if not candidates or self._reranker is None or not self._settings.RERANKER_ENABLED:
            return candidates
        try:
            return await self._reranker.rerank(query_text, candidates)
        except Exception:
            AI_RETRIEVAL_FALLBACKS_TOTAL.labels(component="reranker").inc()
            return candidates

    async def _expand_neighbors(
        self,
        *,
        tenant_id: str,
        knowledge_base_id: str,
        primary: list[RetrievedChunk],
        document_ids: Sequence[str] | None,
    ) -> list[RetrievedChunk]:
        final = list(primary[: self._settings.FINAL_CONTEXT_TOP_K])
        if self._neighbors is None:
            return final[: self._settings.FINAL_CONTEXT_TOP_K]
        seen = {_identity(item) for item in final}
        added = 0
        for item in primary:
            if (
                added >= self._settings.NEIGHBOR_EXPANSION_LIMIT
                or len(final) >= self._settings.FINAL_CONTEXT_TOP_K
            ):
                break
            try:
                neighbors = await self._neighbors.load(
                    tenant_id=tenant_id,
                    knowledge_base_id=knowledge_base_id,
                    primary=item,
                    document_ids=document_ids,
                )
            except Exception:
                AI_RETRIEVAL_FALLBACKS_TOTAL.labels(component="neighbors").inc()
                continue
            for neighbor in neighbors:
                key = _identity(neighbor)
                if key in seen:
                    continue
                final.append(neighbor)
                seen.add(key)
                added += 1
                if (
                    added >= self._settings.NEIGHBOR_EXPANSION_LIMIT
                    or len(final) >= self._settings.FINAL_CONTEXT_TOP_K
                ):
                    break
        return final


def weighted_reciprocal_rank_fusion(
    dense: Sequence[RetrievedChunk],
    sparse: Sequence[RetrievedChunk],
    graph: Sequence[RetrievedChunk],
    *,
    weights: RetrievalWeights,
    limit: int = 30,
    k: int = 30,
) -> list[RetrievedChunk]:
    scores: dict[tuple[str, str], float] = {}
    chunks: dict[tuple[str, str], RetrievedChunk] = {}
    for weight, results in (
        (weights.dense, dense),
        (weights.sparse, sparse),
        (weights.graph, graph),
    ):
        for rank, chunk in enumerate(results, start=1):
            key = _identity(chunk)
            scores[key] = scores.get(key, 0.0) + weight / (k + rank)
            chunks.setdefault(key, chunk)
    ordered = sorted(scores, key=lambda key: (-scores[key], key))[:limit]
    return [replace(chunks[key], score=scores[key]) for key in ordered]


def select_diverse_evidence(
    candidates: Sequence[RetrievedChunk], *, limit: int = 5, per_document_soft_limit: int = 2
) -> list[RetrievedChunk]:
    selected: list[RetrievedChunk] = []
    deferred: list[RetrievedChunk] = []
    counts: Counter[str] = Counter()
    for candidate in candidates:
        if counts[candidate.document_id] < per_document_soft_limit:
            selected.append(candidate)
            counts[candidate.document_id] += 1
            if len(selected) == limit:
                return selected
        else:
            deferred.append(candidate)
    for candidate in deferred:
        selected.append(candidate)
        if len(selected) == limit:
            break
    return selected


def _identity(chunk: RetrievedChunk) -> tuple[str, str]:
    return chunk.document_id, chunk.unit_id or str(chunk.chunk_index)


def _qdrant_sparse(vector: SparseEmbedding) -> models.SparseVector:
    return models.SparseVector(indices=list(vector.indices), values=list(vector.values))
