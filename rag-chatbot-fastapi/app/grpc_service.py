from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

import grpc
from redis.asyncio import Redis

from app.core.config import Settings
from app.document_units import QdrantDocumentUnitStore
from app.generated import cacanode_ai_v1_pb2 as pb
from app.generated import cacanode_ai_v1_pb2_grpc as pb_grpc
from app.graph import GraphServiceClient
from app.ingestion.vector_store import QdrantChunkStore
from app.rag.errors import ChatModelProviderError, ChatModelTimeoutError
from app.rag.models import ChatMessage, ChatSession
from app.rag.revision import authoritative_revision
from app.rag.runtime import RagRuntime
from app.rag.sessions import GenerationChatSessionStore

logger = logging.getLogger(__name__)


class InferenceGrpcService(pb_grpc.InferenceServiceServicer):
    def __init__(self, settings: Settings, runtime: RagRuntime, redis_client: Redis) -> None:
        self._settings = settings
        self._runtime = runtime
        self._redis = redis_client
        self._units = QdrantDocumentUnitStore(settings)

    async def GenerateAnswer(
        self, request: pb.GenerateAnswerRequest, context: grpc.aio.ServicerContext
    ) -> pb.GenerateAnswerResponse:
        if not request.generation_id or not request.tenant_id or not request.knowledge_base_id:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "Generation scope is incomplete")
        if request.authoritative_revision < 0:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "Revision must not be negative")

        cached = await self._cached_result(request.generation_id)
        if cached is not None:
            cached.cache_tier = "generation_id"
            if cached.HasField("input_tokens"):
                cached.avoided_input_tokens = cached.input_tokens
            if cached.HasField("output_tokens"):
                cached.avoided_output_tokens = cached.output_tokens
            return cached

        visible_ids = sorted(set(request.visible_document_ids))
        customer_visibility = request.visibility_mode == pb.CUSTOMER_VISIBLE_DOCUMENTS
        session = ChatSession(
            id=request.turn_id or request.generation_id,
            tenant_id=request.tenant_id,
            user_id=None,
            chatbot_id=request.chatbot_id,
            knowledge_base_id=request.knowledge_base_id,
            locale=request.locale or self._settings.DEFAULT_LOCALE,
            channel=request.channel or "EMPLOYEE_PLAYGROUND",
            customer_answer_prompt=request.customer_answer_prompt,
            tenant_name=request.tenant_name,
            authoritative_revision=request.authoritative_revision,
        )
        history = [
            ChatMessage(role=item.role, content=item.content, sequence_number=index)
            for index, item in enumerate(request.prior_messages[:20], start=1)
            if item.role in {"user", "assistant", "system"}
        ]
        store = GenerationChatSessionStore(
            session=session,
            prior_messages=history,
            visible_document_ids=visible_ids if customer_visibility else [],
        )
        try:
            with authoritative_revision(
                request.tenant_id,
                request.knowledge_base_id,
                request.authoritative_revision,
            ):
                message = await self._runtime.chat_service(store).submit_message(
                    tenant_id=request.tenant_id,
                    session_id=session.id,
                    content=request.question,
                )
        except ChatModelTimeoutError:
            await context.abort(grpc.StatusCode.DEADLINE_EXCEEDED, "Model deadline exceeded")
        except ChatModelProviderError:
            await context.abort(grpc.StatusCode.INTERNAL, "Model provider failed")
        except Exception:
            logger.exception(
                "gRPC answer generation failed generation_id=%s", request.generation_id
            )
            await context.abort(grpc.StatusCode.INTERNAL, "Answer generation failed")

        if customer_visibility and any(
            citation.document_id not in visible_ids for citation in message.citations
        ):
            await context.abort(
                grpc.StatusCode.INTERNAL, "Generated citation escaped visibility scope"
            )

        response = pb.GenerateAnswerResponse(
            generation_id=request.generation_id,
            authoritative_revision=request.authoritative_revision,
            answer=message.content,
            citations=[self._citation(item) for item in message.citations],
            cache_tier="none",
        )
        if message.action:
            draft = response.ticket_draft
            draft.title = str(message.action.get("title") or "")
            draft.description = str(message.action.get("description") or "")
            draft.customer_email = str(message.action.get("customer_email") or "")
            metadata = message.action.get("metadata")
            if isinstance(metadata, dict):
                draft.metadata.update({str(key): str(value) for key, value in metadata.items()})
        await self._cache_result(request.generation_id, response)
        return response

    async def ListDocumentUnits(
        self, request: pb.ListDocumentUnitsRequest, context: grpc.aio.ServicerContext
    ) -> pb.ListDocumentUnitsResponse:
        try:
            units = await self._units.list_units(
                tenant_id=request.tenant_id, document_id=request.document_id
            )
        except Exception:
            logger.exception("gRPC document-unit read failed document_id=%s", request.document_id)
            await context.abort(grpc.StatusCode.UNAVAILABLE, "Indexed document content unavailable")
        if not units:
            await context.abort(grpc.StatusCode.NOT_FOUND, "Indexed document was not found")
        return pb.ListDocumentUnitsResponse(units=[self._document_unit(unit) for unit in units])

    async def DeleteDocumentIndex(
        self, request: pb.DeleteDocumentIndexRequest, context: grpc.aio.ServicerContext
    ) -> pb.DeleteDocumentIndexResponse:
        try:
            await QdrantChunkStore(self._settings).delete_source_ids(
                request.tenant_id, request.document_id
            )
            await GraphServiceClient(self._settings).delete_source(
                request.tenant_id, request.document_id
            )
        except Exception:
            logger.exception(
                "gRPC document-index deletion failed document_id=%s", request.document_id
            )
            await context.abort(grpc.StatusCode.UNAVAILABLE, "Document index deletion failed")
        return pb.DeleteDocumentIndexResponse(deleted=True)

    async def _cached_result(self, generation_id: str) -> pb.GenerateAnswerResponse | None:
        try:
            value = await self._redis.get(self._result_key(generation_id))
            if value is None:
                return None
            return pb.GenerateAnswerResponse.FromString(value)
        except Exception:
            logger.warning("Generation result-cache read failed open", exc_info=True)
            return None

    async def _cache_result(self, generation_id: str, response: pb.GenerateAnswerResponse) -> None:
        try:
            await self._redis.setex(
                self._result_key(generation_id),
                self._settings.GENERATION_RESULT_CACHE_TTL_SECONDS,
                response.SerializeToString(),
            )
        except Exception:
            logger.warning("Generation result-cache write failed open", exc_info=True)

    def _result_key(self, generation_id: str) -> str:
        return f"{self._settings.CACHE_KEY_PREFIX.rstrip(':')}:generation-result:{generation_id}"

    def _citation(self, citation: Any) -> pb.Citation:
        value = pb.Citation(
            id=citation.id,
            document_id=citation.document_id,
            source_name=citation.source_name,
            chunk_index=citation.chunk_index,
            score=citation.score,
            snippet=citation.snippet,
            section_path=list(citation.section_path),
        )
        for field in (
            "page_number",
            "unit_id",
            "modality",
            "block_type",
            "sheet_name",
            "cell_range",
            "table_id",
        ):
            item = getattr(citation, field)
            if item is not None:
                setattr(value, field, item)
        return value

    def _document_unit(self, unit: Any) -> pb.DocumentUnit:
        value = pb.DocumentUnit(
            chunk_index=unit.chunk_index,
            text=unit.text,
            section_path=list(unit.section_path),
        )
        for field in (
            "unit_id",
            "source_name",
            "modality",
            "block_type",
            "heading_context",
            "page_number",
            "sheet_name",
            "cell_range",
            "table_id",
            "source_start",
            "source_end",
        ):
            item = getattr(unit, field)
            if item is not None:
                setattr(value, field, item)
        return value


async def start_grpc_server(
    settings: Settings, runtime: RagRuntime, redis_client: Redis
) -> grpc.aio.Server:
    server = grpc.aio.server(
        options=(
            ("grpc.max_receive_message_length", settings.GRPC_MAX_MESSAGE_BYTES),
            ("grpc.max_send_message_length", settings.GRPC_MAX_MESSAGE_BYTES),
        )
    )
    pb_grpc.add_InferenceServiceServicer_to_server(
        InferenceGrpcService(settings, runtime, redis_client), server
    )
    address = f"{settings.GRPC_HOST}:{settings.GRPC_PORT}"
    if settings.GRPC_PLAINTEXT:
        server.add_insecure_port(address)
    else:
        private_key = Path(settings.GRPC_SERVER_KEY).read_bytes()
        certificate = Path(settings.GRPC_SERVER_CERTIFICATE).read_bytes()
        client_ca = Path(settings.GRPC_CLIENT_CA_CERTIFICATE).read_bytes()
        credentials = grpc.ssl_server_credentials(
            ((private_key, certificate),),
            root_certificates=client_ca,
            require_client_auth=True,
        )
        server.add_secure_port(address, credentials)
    await server.start()
    logger.info("Cacanode inference gRPC server listening on %s", address)
    return server
