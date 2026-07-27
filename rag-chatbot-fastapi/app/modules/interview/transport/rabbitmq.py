from __future__ import annotations

import asyncio
import hashlib
import json
from collections.abc import Mapping
from datetime import UTC, datetime
from typing import Any

import aio_pika
from aio_pika import DeliveryMode, ExchangeType, Message
from aio_pika.abc import AbstractIncomingMessage, AbstractQueue, AbstractRobustConnection

from app.common.errors import StorageUnavailableError
from app.common.storage import ObjectStorageReader
from app.contracts.ai_interview_v1 import (
    ResumeAnalysisOutcome,
    ResumeAnalysisRequest,
    interview_event_id,
    parse_interview_event,
    resume_analysis_id,
)
from app.modules.ingestion.api import PermanentIngestionFailure
from app.modules.interview.internal.redis_state import (
    CheckpointRecovery,
    InterviewRedisState,
    payload_sha256,
)
from app.modules.interview.internal.resume_analysis import (
    ResumeAnalysisProcessor,
    ResumeAnalysisRejectedError,
)
from app.modules.model.api import ModelTimeoutError, ModelUnavailableError

INTERVIEW_EXCHANGE = "cacanode.interview.v1"
INTERVIEW_DLX = "cacanode.interview.dlx.v1"
RESUME_ANALYSIS_QUEUE = "cacanode.interview.resume-analysis.v1"
RECRUITMENT_EVENTS_QUEUE = "cacanode.recruitment.interview-events.v1"
RESUME_ANALYSIS_DLQ = "cacanode.interview.resume-analysis.dlq.v1"
RECRUITMENT_EVENTS_DLQ = "cacanode.recruitment.interview-events.dlq.v1"

RESUME_ANALYSIS_REQUESTED = "interview.resume-analysis.requested"
RESUME_ANALYSIS_OUTCOME = "interview.resume-analysis.outcome"
TURN_FINALIZED = "interview.turn.finalized"
SESSION_COMPLETED = "interview.session.completed"
SESSION_FAILED = "interview.session.failed"
PROVIDER_USAGE = "interview.provider.usage"
MAX_TRANSIENT_RETRIES = 3
CONFIRM_TIMEOUT_SECONDS = 5

JAVA_ROUTING_KEYS = (
    RESUME_ANALYSIS_OUTCOME,
    TURN_FINALIZED,
    SESSION_COMPLETED,
    SESSION_FAILED,
    PROVIDER_USAGE,
)


async def declare_interview_topology(connection: AbstractRobustConnection) -> None:
    channel = await connection.channel(publisher_confirms=True)
    try:
        exchange = await channel.declare_exchange(
            INTERVIEW_EXCHANGE, ExchangeType.TOPIC, durable=True
        )
        dlx = await channel.declare_exchange(INTERVIEW_DLX, ExchangeType.TOPIC, durable=True)
        resume = await channel.declare_queue(
            RESUME_ANALYSIS_QUEUE,
            durable=True,
            arguments={"x-dead-letter-exchange": INTERVIEW_DLX},
        )
        java = await channel.declare_queue(
            RECRUITMENT_EVENTS_QUEUE,
            durable=True,
            arguments={"x-dead-letter-exchange": INTERVIEW_DLX},
        )
        resume_dlq = await channel.declare_queue(RESUME_ANALYSIS_DLQ, durable=True)
        java_dlq = await channel.declare_queue(RECRUITMENT_EVENTS_DLQ, durable=True)
        await resume.bind(exchange, RESUME_ANALYSIS_REQUESTED)
        for routing_key in JAVA_ROUTING_KEYS:
            await java.bind(exchange, routing_key)
        await resume_dlq.bind(dlx, RESUME_ANALYSIS_REQUESTED)
        for routing_key in JAVA_ROUTING_KEYS:
            await java_dlq.bind(dlx, routing_key)
    finally:
        await channel.close()


async def connect_and_declare_interview_topology(
    rabbitmq_url: str,
) -> AbstractRobustConnection:
    connection = await aio_pika.connect_robust(rabbitmq_url)
    try:
        await declare_interview_topology(connection)
    except Exception:
        await connection.close()
        raise
    return connection


class ConfirmedInterviewPublisher:
    def __init__(
        self,
        connection: AbstractRobustConnection,
        state: InterviewRedisState,
    ) -> None:
        self._connection = connection
        self._state = state

    async def publish(self, *, event_id: str, routing_key: str, payload: bytes) -> None:
        channel = await self._connection.channel(publisher_confirms=True)
        try:
            exchange = await channel.get_exchange(INTERVIEW_EXCHANGE, ensure=True)
            confirmed = await asyncio.wait_for(
                exchange.publish(
                    Message(
                        payload,
                        content_type="application/json",
                        delivery_mode=DeliveryMode.PERSISTENT,
                        message_id=event_id,
                    ),
                    routing_key,
                    mandatory=True,
                ),
                timeout=CONFIRM_TIMEOUT_SECONDS,
            )
            if confirmed is False:
                raise RuntimeError("Interview event publication was negatively acknowledged")
            await self._state.mark_confirmed_publication(event_id)
        finally:
            await channel.close()

    async def publish_checkpointed(
        self,
        *,
        session_id: str,
        expected_revision: int,
        phase: str,
        commit_phase: str,
        current_runtime_state: Mapping[str, Any],
        next_runtime_state: Mapping[str, Any],
        event_id: str,
        routing_key: str,
        payload: bytes,
        call_sid: str,
    ) -> int:
        revision = await self._state.stage_checkpoint_event(
            session_id,
            expected_revision=expected_revision,
            phase=phase,
            commit_phase=commit_phase,
            current_runtime_state=current_runtime_state,
            next_runtime_state=next_runtime_state,
            event_id=event_id,
            routing_key=routing_key,
            payload=payload,
            call_sid=call_sid,
        )
        if not await self._state.publication_confirmed(event_id):
            await self.publish(event_id=event_id, routing_key=routing_key, payload=payload)
        return await self._state.commit_checkpoint_event(
            session_id, expected_revision=revision, event_id=event_id
        )

    async def recover_checkpoint(
        self, session_id: str, *, requested_event_id: str | None = None
    ) -> CheckpointRecovery | None:
        checkpoint = await self._state.load_checkpoint(session_id)
        if checkpoint is None:
            return None
        if checkpoint.pending_event is None:
            if requested_event_id is None:
                return None
            confirmed_event_id = (
                requested_event_id
                if await self._state.publication_confirmed(requested_event_id)
                else None
            )
            return CheckpointRecovery(
                event_id=confirmed_event_id,
                revision=checkpoint.revision,
                phase=checkpoint.phase,
            )
        pending = checkpoint.pending_event
        event_id = str(pending["event_id"])
        if not await self._state.publication_confirmed(event_id):
            await self.publish(
                event_id=event_id,
                routing_key=str(pending["routing_key"]),
                payload=str(pending["payload"]).encode("utf-8"),
            )
        revision = await self._state.commit_checkpoint_event(
            session_id,
            expected_revision=checkpoint.revision,
            event_id=event_id,
        )
        return CheckpointRecovery(
            event_id=event_id,
            revision=revision,
            phase=str(pending["commit_phase"]),
        )


class ResumeAnalysisWorker:
    def __init__(
        self,
        *,
        connection: AbstractRobustConnection,
        state: InterviewRedisState,
        store: ObjectStorageReader,
        processor: ResumeAnalysisProcessor,
        policy_version: str,
        model_version: str,
        max_attempts: int = 3,
        pending_outcome_ttl_seconds: int = 900,
    ) -> None:
        self._connection = connection
        self._state = state
        self._store = store
        self._processor = processor
        self._policy_version = policy_version
        self._model_version = model_version
        self._max_attempts = max_attempts
        self._pending_outcome_ttl_seconds = pending_outcome_ttl_seconds
        self._queue: AbstractQueue | None = None
        self._consumer_tag: str | None = None
        self._publisher = ConfirmedInterviewPublisher(connection, state)

    async def start(self) -> None:
        channel = await self._connection.channel(publisher_confirms=True)
        await channel.set_qos(prefetch_count=4)
        self._queue = await channel.get_queue(RESUME_ANALYSIS_QUEUE, ensure=True)
        self._consumer_tag = await self._queue.consume(self._handle, no_ack=False)

    async def stop(self) -> None:
        if self._queue is not None and self._consumer_tag is not None:
            await self._queue.cancel(self._consumer_tag)

    async def _handle(self, message: AbstractIncomingMessage) -> None:
        try:
            parsed = parse_interview_event(message.body)
            if not isinstance(parsed, ResumeAnalysisRequest):
                await message.reject(requeue=False)
                return
            request = parsed
            self._validate_request(request)
            digest = payload_sha256(message.body)
            await self._state.claim_resume_request(str(request.analysis_id), digest)
        except Exception:
            await message.reject(requeue=False)
            return

        event_id = interview_event_id(RESUME_ANALYSIS_OUTCOME, request.analysis_id, "outcome:v1.1")
        if await self._state.publication_confirmed(str(event_id)):
            await self._state.delete_pending_outcome(str(request.analysis_id))
            await message.ack()
            return
        lease = await self._state.acquire_lease(str(request.analysis_id))
        if lease is None:
            await message.nack(requeue=True)
            return
        try:
            pending = await self._state.pending_outcome(str(request.analysis_id))
            if pending is None:
                pending = await self._produce_outcome(request, event_id)
                await self._state.store_pending_outcome(
                    str(request.analysis_id),
                    pending,
                    ttl_seconds=self._pending_outcome_ttl_seconds,
                )
            await self._publisher.publish(
                event_id=str(event_id), routing_key=RESUME_ANALYSIS_OUTCOME, payload=pending
            )
            await self._state.delete_pending_outcome(str(request.analysis_id))
            await message.ack()
        except (StorageUnavailableError, ModelTimeoutError, ModelUnavailableError):
            attempts = await self._state.increment_resume_attempts(str(request.analysis_id))
            if attempts < self._max_attempts:
                await message.nack(requeue=True)
            else:
                await self._publish_failed(request, event_id, "CV_ANALYSIS_RETRY_EXHAUSTED")
                await message.ack()
        except (PermanentIngestionFailure, ResumeAnalysisRejectedError) as exc:
            code = (
                str(exc)
                if str(exc).startswith("CV_ANALYSIS_")
                else "CV_ANALYSIS_UNSUPPORTED_DOCUMENT"
            )
            await self._publish_failed(request, event_id, code[:100])
            await message.ack()
        except Exception:
            await message.nack(requeue=True)
        finally:
            await self._state.release_lease(str(request.analysis_id), lease)

    async def _produce_outcome(self, request: ResumeAnalysisRequest, event_id: Any) -> bytes:
        limited = getattr(self._store, "download_limited", None)
        try:
            file_bytes = (
                await limited(request.storage_key, 5 * 1024 * 1024)
                if callable(limited)
                else await self._store.download(request.storage_key)
            )
        except ValueError as exc:
            raise ResumeAnalysisRejectedError("CV_ANALYSIS_FILE_TOO_LARGE") from exc
        if len(file_bytes) != request.file_size_bytes or len(file_bytes) > 5 * 1024 * 1024:
            raise ResumeAnalysisRejectedError("CV_ANALYSIS_FILE_METADATA_MISMATCH")
        if hashlib.sha256(file_bytes).hexdigest() != request.cv_sha256:
            raise ResumeAnalysisRejectedError("CV_ANALYSIS_CONTENT_HASH_MISMATCH")
        completed = await self._processor.process(request, file_bytes)
        outcome = ResumeAnalysisOutcome(
            schema_version="1.1",
            event_id=event_id,
            event_type="interview.resume-analysis.outcome",
            occurred_at=datetime.now(UTC),
            tenant_id=request.tenant_id,
            aggregate_id=request.analysis_id,
            analysis_id=request.analysis_id,
            application_id=request.application_id,
            cv_sha256=request.cv_sha256,
            analysis_mode=request.analysis_mode,
            policy_version=request.policy_version,
            model_version=request.model_version,
            status="COMPLETED",
            summary=completed.summary,
            evidence=list(completed.evidence),
            skills=list(completed.skills),
            personalized_questions=list(completed.personalized_questions),
            error_code=None,
        )
        return _canonical_model(outcome)

    async def _publish_failed(
        self, request: ResumeAnalysisRequest, event_id: Any, error_code: str
    ) -> None:
        pending = await self._state.pending_outcome(str(request.analysis_id))
        if pending is None:
            failed = ResumeAnalysisOutcome(
                schema_version="1.1",
                event_id=event_id,
                event_type="interview.resume-analysis.outcome",
                occurred_at=datetime.now(UTC),
                tenant_id=request.tenant_id,
                aggregate_id=request.analysis_id,
                analysis_id=request.analysis_id,
                application_id=request.application_id,
                cv_sha256=request.cv_sha256,
                analysis_mode=request.analysis_mode,
                policy_version=request.policy_version,
                model_version=request.model_version,
                status="FAILED",
                summary=None,
                evidence=[],
                skills=[],
                personalized_questions=[],
                error_code=error_code,
            )
            pending = _canonical_model(failed)
            await self._state.store_pending_outcome(
                str(request.analysis_id),
                pending,
                ttl_seconds=self._pending_outcome_ttl_seconds,
            )
        await self._publisher.publish(
            event_id=str(event_id), routing_key=RESUME_ANALYSIS_OUTCOME, payload=pending
        )
        await self._state.delete_pending_outcome(str(request.analysis_id))

    def _validate_request(self, request: ResumeAnalysisRequest) -> None:
        expected = resume_analysis_id(
            request.tenant_id,
            request.application_id,
            request.cv_sha256,
            request.analysis_mode,
            request.policy_version,
            request.model_version,
        )
        event_id = interview_event_id(
            RESUME_ANALYSIS_REQUESTED, request.analysis_id, "requested:v1.1"
        )
        prefix = f"recruitment/{request.tenant_id}/applications/{request.application_id}/"
        if (
            request.analysis_id != expected
            or request.event_id != event_id
            or request.aggregate_id != request.analysis_id
            or not request.storage_key.startswith(prefix)
            or request.policy_version != self._policy_version
            or request.model_version != self._model_version
        ):
            raise ValueError("Invalid resume-analysis identity")


def _canonical_model(model: ResumeAnalysisOutcome) -> bytes:
    return json.dumps(
        model.model_dump(mode="json"),
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
