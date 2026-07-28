from __future__ import annotations

from dataclasses import dataclass

from app.common.errors import StorageUnavailableError
from app.common.storage import ObjectStorageReader
from app.modules.graph.api import (
    GraphBatch,
    GraphProjectionApi,
    GraphRejectedError,
    GraphUnavailableError,
)
from app.modules.index.api import (
    IndexRejectedError,
    IndexSparseVector,
    IndexUnavailableError,
    IndexUnit,
    KnowledgeIndexCommandApi,
    ReplaceDocumentIndex,
)
from app.modules.ingestion.api import (
    DocumentIndexLifecycleApi,
    IngestDocumentCommand,
    IngestionApi,
    IngestionOutcome,
    IngestionOutcomeStatus,
    PermanentIngestionFailure,
    TransientIngestionFailure,
)
from app.modules.ingestion.internal.chunking import DeterministicChunker, TextChunk
from app.modules.ingestion.internal.entity_extraction import EntityRelationExtractor, graph_units
from app.modules.ingestion.internal.extraction import DocumentTextExtractor, ParsedDocument
from app.modules.model.api import (
    ModelRejectedError,
    ModelUnavailableError,
    SparseEmbeddingApi,
    TextEmbeddingApi,
)


@dataclass(frozen=True, slots=True)
class PreparedDocument:
    command: IngestDocumentCommand
    parsed: ParsedDocument
    chunks: tuple[TextChunk, ...]
    index_command: ReplaceDocumentIndex


class DocumentIngestionPipeline(IngestionApi):
    def __init__(
        self,
        *,
        store: ObjectStorageReader,
        extractor: DocumentTextExtractor,
        chunker: DeterministicChunker,
        embedder: TextEmbeddingApi,
        sparse_encoder: SparseEmbeddingApi,
        vector_store: KnowledgeIndexCommandApi,
        graph_store: GraphProjectionApi,
        graph_extractor: EntityRelationExtractor,
    ):
        self._store = store
        self._extractor = extractor
        self._chunker = chunker
        self._embedder = embedder
        self._sparse_encoder = sparse_encoder
        self._vector_store = vector_store
        self._graph_store = graph_store
        self._graph_extractor = graph_extractor

    async def process(self, command: IngestDocumentCommand) -> IngestionOutcome:
        try:
            return await self._process(command)
        except (
            StorageUnavailableError,
            ModelUnavailableError,
            IndexUnavailableError,
            GraphUnavailableError,
        ) as exc:
            raise TransientIngestionFailure(str(exc)) from exc
        except (ModelRejectedError, IndexRejectedError, GraphRejectedError) as exc:
            raise PermanentIngestionFailure(str(exc)) from exc

    async def _process(self, event: IngestDocumentCommand) -> IngestionOutcome:
        prepared = await self.prepare(event)
        await self.replace_index(prepared)
        try:
            await self.replace_graph(prepared)
        except (ModelRejectedError, GraphRejectedError, PermanentIngestionFailure):
            await self.cleanup(event)
            raise
        return IngestionOutcome(IngestionOutcomeStatus.COMPLETED, len(prepared.chunks))

    async def prepare(self, event: IngestDocumentCommand) -> PreparedDocument:
        data = await self._store.download(event.storage_key)
        parsed = self._extractor.parse(
            data,
            content_type=event.content_type,
            file_name=event.file_name,
        )
        chunks = self._chunker.chunk(parsed)
        if not chunks:
            raise PermanentIngestionFailure("Document contains no extractable text")
        embeddings = await self._embedder.embed_documents([chunk.text for chunk in chunks])
        sparse_embeddings = await self._sparse_encoder.embed_documents(
            [chunk.text for chunk in chunks]
        )
        return PreparedDocument(
            command=event,
            parsed=parsed,
            chunks=tuple(chunks),
            index_command=ReplaceDocumentIndex(
                tenant_id=event.tenant_id,
                knowledge_base_id=event.knowledge_base_id,
                document_id=event.document_id,
                source_name=event.file_name,
                units=tuple(_index_unit(chunk) for chunk in chunks),
                dense_vectors=tuple(tuple(vector) for vector in embeddings),
                sparse_vectors=tuple(
                    IndexSparseVector(item.indices, item.values) for item in sparse_embeddings
                ),
            ),
        )

    async def replace_index(self, prepared: PreparedDocument) -> None:
        await self._vector_store.replace_document(prepared.index_command)

    async def replace_graph(self, prepared: PreparedDocument) -> None:
        event = prepared.command
        if prepared.parsed.modality == "document":
            batch = await self._graph_extractor.extract(event, prepared.chunks)
        else:
            batch = GraphBatch(
                tenant_id=event.tenant_id,
                knowledge_base_id=event.knowledge_base_id,
                source_id=event.document_id,
                source_name=event.file_name,
                units=graph_units(event.document_id, event.file_name, prepared.chunks),
            )
        await self._graph_store.replace_source(batch)

    async def cleanup(self, event: IngestDocumentCommand) -> None:
        await self._graph_store.delete_source(event.tenant_id, event.document_id)
        await self._vector_store.delete_document(event.tenant_id, event.document_id)


class DocumentIndexLifecycleService(DocumentIndexLifecycleApi):
    def __init__(
        self, index: KnowledgeIndexCommandApi, graph: GraphProjectionApi
    ) -> None:
        self._index = index
        self._graph = graph

    async def delete_document(
        self, tenant_id: str, knowledge_base_id: str, document_id: str
    ) -> None:
        del knowledge_base_id
        await self._index.delete_document(tenant_id, document_id)
        await self._graph.delete_source(tenant_id, document_id)


def _index_unit(chunk: TextChunk) -> IndexUnit:
    return IndexUnit(
        unit_id=str(chunk.unit_id or chunk.chunk_index),
        chunk_index=int(chunk.chunk_index),
        text=str(chunk.text),
        content_hash=str(chunk.content_hash),
        source_name="",
        modality=str(chunk.modality),
        block_type=str(chunk.block_type),
        section_path=tuple(chunk.section_path),
        heading_context=chunk.heading_context,
        page_number=chunk.page_number,
        sheet_name=chunk.sheet_name,
        cell_range=chunk.cell_range,
        table_id=chunk.table_id,
        source_start=chunk.source_start,
        source_end=chunk.source_end,
        parser_version=str(chunk.parser_version),
        chunker_version=str(chunk.chunker_version),
    )
