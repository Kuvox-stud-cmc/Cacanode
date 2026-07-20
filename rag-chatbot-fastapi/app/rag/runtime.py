from __future__ import annotations

from dataclasses import dataclass

from app.core.cache import CacheStore
from app.core.config import Settings
from app.graph import GraphServiceClient
from app.infrastructure.model_gateway import create_chat_model
from app.ingestion.embedding import EmbeddingClient
from app.ingestion.storage import SeaweedS3DocumentStore
from app.rag.calculation import SpreadsheetCalculationCoordinator
from app.rag.chat_service import RagChatService
from app.rag.reranking import TeiReranker
from app.rag.retrieval import (
    HybridRetriever,
    QdrantNeighborLoader,
    QdrantSparseRetriever,
    QdrantVectorRetriever,
)
from app.rag.retrieval_cache import CachedRetriever, RetrievalCacheKeyBuilder
from app.rag.revision import KnowledgeBaseRevisionStore
from app.rag.semantic_answer_cache import SemanticAnswerCache
from app.rag.sessions import ChatSessionStore


@dataclass(slots=True)
class RagRuntime:
    settings: Settings
    embedder: EmbeddingClient
    retriever: CachedRetriever
    chat_model: object
    calculations: SpreadsheetCalculationCoordinator
    semantic_answer_cache: SemanticAnswerCache | None

    @classmethod
    def create(
        cls,
        settings: Settings,
        *,
        embedder: EmbeddingClient,
        cache_store: CacheStore,
        revision_store: KnowledgeBaseRevisionStore,
        semantic_answer_cache: SemanticAnswerCache | None,
    ) -> RagRuntime:
        authoritative_retriever = HybridRetriever(
            settings=settings,
            dense=QdrantVectorRetriever(settings),
            sparse=QdrantSparseRetriever(settings),
            graph=GraphServiceClient(settings),
            reranker=TeiReranker(settings),
            neighbor_loader=QdrantNeighborLoader(settings),
        )
        return cls(
            settings=settings,
            embedder=embedder,
            retriever=CachedRetriever(
                authoritative_retriever,
                cache_store=cache_store,
                revision_store=revision_store,
                key_builder=RetrievalCacheKeyBuilder(settings),
                ttl_seconds=settings.RETRIEVAL_CACHE_TTL_SECONDS,
                max_results=settings.FINAL_CONTEXT_TOP_K,
                enabled=settings.CACHE_ENABLED and settings.RETRIEVAL_CACHE_ENABLED,
            ),
            chat_model=create_chat_model(settings),
            calculations=SpreadsheetCalculationCoordinator(
                SeaweedS3DocumentStore(settings), create_chat_model(settings)
            ),
            semantic_answer_cache=semantic_answer_cache,
        )

    def chat_service(self, sessions: ChatSessionStore) -> RagChatService:
        return RagChatService(
            settings=self.settings,
            sessions=sessions,
            embedder=self.embedder,
            retriever=self.retriever,
            chat_model=self.chat_model,  # type: ignore[arg-type]
            calculations=self.calculations,
            semantic_answer_cache=self.semantic_answer_cache,
        )
