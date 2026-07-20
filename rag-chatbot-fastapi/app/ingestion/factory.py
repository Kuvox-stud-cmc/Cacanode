from __future__ import annotations

from app.core.config import Settings
from app.graph import EntityRelationExtractor, GraphServiceClient
from app.infrastructure.model_gateway import create_chat_model
from app.ingestion.chunking import DeterministicChunker
from app.ingestion.embedding import EmbeddingClient, OllamaEmbeddingClient
from app.ingestion.extraction import DocumentTextExtractor
from app.ingestion.pipeline import DocumentIngestionPipeline
from app.ingestion.sparse import FastEmbedSparseEncoder
from app.ingestion.storage import SeaweedS3DocumentStore
from app.ingestion.vector_store import QdrantChunkStore


def create_document_ingestion_pipeline(
    settings: Settings,
    *,
    embedder: EmbeddingClient | None = None,
) -> DocumentIngestionPipeline:
    extraction_settings = settings.model_copy(
        update={"LLM_MAX_OUTPUT_TOKENS": settings.GRAPH_EXTRACTION_MAX_OUTPUT_TOKENS}
    )
    return DocumentIngestionPipeline(
        store=SeaweedS3DocumentStore(settings),
        extractor=DocumentTextExtractor(),
        chunker=DeterministicChunker(),
        embedder=embedder or OllamaEmbeddingClient(settings),
        sparse_encoder=FastEmbedSparseEncoder(settings),
        vector_store=QdrantChunkStore(settings),
        graph_store=GraphServiceClient(settings),
        graph_extractor=EntityRelationExtractor(
            create_chat_model(
                extraction_settings,
                reasoning_effort=settings.GRAPH_EXTRACTION_REASONING_EFFORT,
            ),
            batch_size=settings.GRAPH_EXTRACTION_BATCH_SIZE,
        ),
    )
