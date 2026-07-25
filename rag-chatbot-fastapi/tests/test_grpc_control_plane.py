from __future__ import annotations

import grpc
import pytest

from app.bootstrap.grpc import InferenceGrpcService
from app.generated import cacanode_ai_v1_pb2 as pb
from app.modules.generation.api import (
    Citation,
    GenerationContext,
    GenerationResult,
    GenerationUnavailableError,
    GenerationVisibility,
)
from app.modules.generation.transport.grpc import GenerationGrpcHandler
from app.modules.generation.transport.result_cache import ProtobufGenerationResultCache


class Redis:
    def __init__(self) -> None:
        self.values: dict[str, bytes] = {}

    async def get(self, key: str) -> bytes | None:
        return self.values.get(key)

    async def setex(self, key: str, ttl: int, value: bytes) -> None:
        assert ttl == 600
        self.values[key] = value


class Context:
    async def abort(self, code: grpc.StatusCode, details: str) -> None:
        raise RuntimeError(f"{code.name}:{details}")


class Generation:
    def __init__(self) -> None:
        self.calls = 0
        self.context: GenerationContext | None = None

    async def generate(self, context: GenerationContext) -> GenerationResult:
        self.calls += 1
        self.context = context
        if (
            context.visibility is GenerationVisibility.CUSTOMER_VISIBLE_DOCUMENTS
            and "doc-1" not in context.visible_document_ids
        ):
            raise GenerationUnavailableError(
                "Generated citation escaped visibility scope"
            )
        return GenerationResult(
            generation_id=context.generation_id,
            authoritative_revision=context.authoritative_revision,
            answer="Grounded answer [S1].",
            citations=(
                Citation(
                    id="S1",
                    document_id="doc-1",
                    source_name="source.txt",
                    page_number=1,
                    chunk_index=0,
                    score=0.9,
                    snippet="Grounded source",
                ),
            ),
        )


class Unused:
    async def list_document_units(self, request: object, context: object) -> object:
        raise AssertionError

    async def delete_document_index(self, request: object, context: object) -> object:
        raise AssertionError

    async def prepare(self, request: object, context: object) -> object:
        raise AssertionError

    async def cancel(self, request: object, context: object) -> object:
        raise AssertionError


def service(generation: Generation, redis: Redis) -> InferenceGrpcService:
    return InferenceGrpcService(
        GenerationGrpcHandler(
            generation,
            ProtobufGenerationResultCache(
                redis, prefix="ccn:v1", ttl_seconds=600  # type: ignore[arg-type]
            ),
        ),
        Unused(),  # type: ignore[arg-type]
        Unused(),  # type: ignore[arg-type]
        Unused(),  # type: ignore[arg-type]
    )


def request() -> pb.GenerateAnswerRequest:
    return pb.GenerateAnswerRequest(
        generation_id="generation-1",
        turn_id="turn-1",
        tenant_id="tenant-1",
        chatbot_id="bot-1",
        knowledge_base_id="kb-1",
        authoritative_revision=7,
        channel="WIDGET",
        locale="vi-VN",
        question="Question",
        prior_messages=[pb.PriorMessage(role="user", content="Earlier")],
        tenant_name="Tenant",
        customer_answer_prompt="Be helpful",
        visibility_mode=pb.CUSTOMER_VISIBLE_DOCUMENTS,
        visible_document_ids=["doc-1"],
        prompt_schema_version="chat-prompts-v2",
    )


@pytest.mark.asyncio
async def test_generation_context_is_supplied_and_result_is_deduplicated() -> None:
    generation = Generation()
    redis = Redis()
    grpc_service = service(generation, redis)

    first = await grpc_service.GenerateAnswer(request(), Context())  # type: ignore[arg-type]
    second = await grpc_service.GenerateAnswer(request(), Context())  # type: ignore[arg-type]

    assert first.authoritative_revision == 7
    assert first.citations[0].document_id == "doc-1"
    assert generation.calls == 1
    assert second.cache_tier == "generation_id"
    assert generation.context is not None
    assert generation.context.authoritative_revision == 7
    assert [item.content for item in generation.context.prior_messages] == ["Earlier"]


@pytest.mark.asyncio
async def test_customer_visibility_rejects_out_of_scope_citation() -> None:
    invalid = request()
    invalid.visible_document_ids[:] = []

    with pytest.raises(RuntimeError, match="visibility scope"):
        await service(Generation(), Redis()).GenerateAnswer(
            invalid, Context()  # type: ignore[arg-type]
        )
