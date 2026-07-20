from __future__ import annotations

import grpc
import pytest

from app.core.config import Settings
from app.generated import cacanode_ai_v1_pb2 as pb
from app.grpc_service import InferenceGrpcService
from app.rag.models import AssistantMessage, Citation


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


class Runtime:
    def __init__(self) -> None:
        self.calls = 0
        self.store: object | None = None

    def chat_service(self, store: object) -> object:
        self.store = store
        runtime = self

        class Service:
            async def submit_message(self, **kwargs: object) -> AssistantMessage:
                del kwargs
                runtime.calls += 1
                return AssistantMessage(
                    role="assistant",
                    content="Grounded answer [S1].",
                    citations=[
                        Citation(
                            id="S1",
                            document_id="doc-1",
                            source_name="source.txt",
                            page_number=1,
                            chunk_index=0,
                            score=0.9,
                            snippet="Grounded source",
                        )
                    ],
                )

        return Service()


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
    runtime = Runtime()
    redis = Redis()
    service = InferenceGrpcService(
        Settings(_env_file=()),
        runtime,
        redis,  # type: ignore[arg-type]
    )

    first = await service.GenerateAnswer(request(), Context())  # type: ignore[arg-type]
    second = await service.GenerateAnswer(request(), Context())  # type: ignore[arg-type]

    assert first.authoritative_revision == 7
    assert first.citations[0].document_id == "doc-1"
    assert runtime.calls == 1
    assert second.cache_tier == "generation_id"
    assert runtime.store is not None
    assert runtime.store.session.authoritative_revision == 7  # type: ignore[attr-defined]
    assert [item.content for item in runtime.store.prior_messages] == [  # type: ignore[attr-defined]
        "Earlier"
    ]


@pytest.mark.asyncio
async def test_customer_visibility_rejects_out_of_scope_citation() -> None:
    runtime = Runtime()
    service = InferenceGrpcService(
        Settings(_env_file=()),
        runtime,
        Redis(),  # type: ignore[arg-type]
    )
    invalid = request()
    invalid.visible_document_ids[:] = []

    with pytest.raises(RuntimeError, match="visibility scope"):
        await service.GenerateAnswer(invalid, Context())  # type: ignore[arg-type]
