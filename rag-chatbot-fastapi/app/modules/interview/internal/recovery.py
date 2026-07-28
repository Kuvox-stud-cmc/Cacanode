from __future__ import annotations

import asyncio
from collections.abc import Mapping
from contextlib import suppress
from decimal import Decimal
from typing import Any, Literal, Protocol

from prometheus_client import Counter

from app.modules.interview.internal.durable_events import (
    canonical_event,
    failed_result,
    provider_usage,
)
from app.modules.interview.internal.engine import DeterministicInterviewEngine
from app.modules.interview.internal.redis_state import InterviewRedisState, RuntimeCheckpoint
from app.modules.interview.transport.rabbitmq import ConfirmedInterviewPublisher

RECOVERY_FAILURES = Counter(
    "interview_recovery_failures_total",
    "Interview watchdog recovery failures",
    ["operation"],
)
TERMINAL_COMPLETE = "TERMINAL_COMPLETE"


class RecoverySettings(Protocol):
    INTERVIEW_RECOVERY_POLL_SECONDS: int
    INTERVIEW_RECOVERY_BATCH_SIZE: int
    INTERVIEW_RECOVERY_MAX_ATTEMPTS: int
    LLM_PROVIDER: Literal["ollama", "openai"]


class InterviewRecoveryWorker:
    def __init__(
        self,
        *,
        settings: RecoverySettings,
        state: InterviewRedisState,
        publisher: ConfirmedInterviewPublisher,
    ) -> None:
        self._settings = settings
        self._state = state
        self._publisher = publisher
        self._task: asyncio.Task[None] | None = None

    async def start(self) -> None:
        if self._task is None:
            self._task = asyncio.create_task(self._run())

    async def stop(self) -> None:
        if self._task is None:
            return
        self._task.cancel()
        with suppress(asyncio.CancelledError):
            await self._task
        self._task = None

    async def _run(self) -> None:
        while True:
            await self.recover_due()
            await asyncio.sleep(self._settings.INTERVIEW_RECOVERY_POLL_SECONDS)

    async def recover_due(self) -> None:
        import time

        sessions = await self._state.due_recoveries(
            now_epoch_seconds=int(time.time()),
            limit=self._settings.INTERVIEW_RECOVERY_BATCH_SIZE,
        )
        for session_id in sessions:
            for _ in range(self._settings.INTERVIEW_RECOVERY_MAX_ATTEMPTS):
                try:
                    await self._recover(session_id)
                    break
                except Exception:
                    RECOVERY_FAILURES.labels("recover_session").inc()
                    continue

    async def _recover(self, session_id: str) -> None:
        lease = await self._state.acquire_lease(session_id)
        if lease is None:
            return
        try:
            await self._publisher.recover_checkpoint(session_id)
            checkpoint = await self._state.load_checkpoint(session_id)
            prepared = await self._state.prepared_session(session_id)
            if prepared is None:
                await self._state.clear_recoverable(session_id)
                return
            if checkpoint is not None and checkpoint.phase == TERMINAL_COMPLETE:
                await self._terminalize(
                    session_id,
                    prepared,
                    checkpoint.call_sid,
                )
                return
            if prepared.get("status") == "terminal":
                await self._state.clear_recoverable(session_id)
                return
            call_sid = (
                checkpoint.call_sid
                if checkpoint is not None and checkpoint.call_sid
                else str(prepared.get("claimed_call_sid") or "")
            )
            await self._publish_recovery_failure(
                session_id=session_id,
                prepared=prepared,
                checkpoint=checkpoint,
                call_sid=call_sid,
            )
            await self._terminalize(session_id, prepared, call_sid)
        finally:
            await self._state.release_lease(session_id, lease)

    async def _publish_recovery_failure(
        self,
        *,
        session_id: str,
        prepared: Mapping[str, Any],
        checkpoint: RuntimeCheckpoint | None,
        call_sid: str,
    ) -> None:
        payload = prepared["payload"]
        if checkpoint is None:
            engine = DeterministicInterviewEngine(payload)
            runtime_state: Mapping[str, Any] = {
                "engine": engine.snapshot(0.0),
                "pending_audio": None,
                "usage": {
                    "stt_audio_ms": 0,
                    "tts_characters": 0,
                    "twilio_media_ms": 0,
                    "llm_tokens": 0,
                },
            }
            revision = 0
            usage: Mapping[str, Any] = {}
            expected_turn_count = 0
            connected_seconds = 0
        else:
            runtime_state = checkpoint.runtime_state
            engine = DeterministicInterviewEngine.restore(
                payload, checkpoint.runtime_state["engine"], 0.0
            )
            revision = checkpoint.revision
            usage = checkpoint.runtime_state.get("usage") or {}
            expected_turn_count = engine.next_turn_sequence - 1
            connected_seconds = int(
                checkpoint.runtime_state["engine"].get("session_elapsed_seconds") or 0
            )
        result = engine.result_snapshot(partial=True)
        usage_values = [
            (
                "CARTESIA",
                "STT",
                Decimal(int(usage.get("stt_audio_ms", 0))) / Decimal(1000),
                "AUDIO_SECOND",
            ),
            (
                "CARTESIA",
                "TTS",
                Decimal(int(usage.get("tts_characters", 0))),
                "CHARACTER",
            ),
            (
                "OPENAI" if self._settings.LLM_PROVIDER == "openai" else "OLLAMA",
                "LLM",
                Decimal(int(usage.get("llm_tokens", 0))),
                "TOKEN",
            ),
            (
                "TWILIO",
                "MEDIA_STREAM",
                Decimal(int(usage.get("twilio_media_ms", 0))) / Decimal(1000),
                "AUDIO_SECOND",
            ),
        ]
        for provider, capability, quantity, unit in usage_values:
            if quantity <= 0:
                continue
            event = provider_usage(
                tenant_id=str(prepared["tenant_id"]),
                session_id=session_id,
                call_attempt_id=str(prepared["call_attempt_id"]),
                provider=provider,
                capability=capability,
                quantity=quantity,
                unit=unit,
            )
            revision = await self._publisher.publish_checkpointed(
                session_id=session_id,
                expected_revision=revision,
                phase="TERMINAL_PUBLICATION",
                commit_phase="TERMINAL_PUBLICATION",
                current_runtime_state=runtime_state,
                next_runtime_state=runtime_state,
                event_id=str(event.event_id),
                routing_key="interview.provider.usage",
                payload=canonical_event(event),
                call_sid=call_sid,
            )
        terminal = failed_result(
            tenant_id=str(prepared["tenant_id"]),
            session_id=session_id,
            call_attempt_id=str(prepared["call_attempt_id"]),
            failure_code="RECOVERY_EXPIRED",
            retryable=False,
            detail="The media connection was not restored before the execution lease expired.",
            expected_turn_count=expected_turn_count,
            connected_seconds=connected_seconds,
            result=result,
        )
        await self._publisher.publish_checkpointed(
            session_id=session_id,
            expected_revision=revision,
            phase="TERMINAL_PUBLICATION",
            commit_phase=TERMINAL_COMPLETE,
            current_runtime_state=runtime_state,
            next_runtime_state=runtime_state,
            event_id=str(terminal.event_id),
            routing_key="interview.session.failed",
            payload=canonical_event(terminal),
            call_sid=call_sid,
        )
        engine.discard()

    async def _terminalize(
        self,
        session_id: str,
        prepared: Mapping[str, Any],
        call_sid: str,
    ) -> None:
        terminalized = await self._state.terminalize_runtime_session(
            session_id,
            str(prepared["call_attempt_id"]),
            call_sid or str(prepared.get("claimed_call_sid") or ""),
        )
        if not terminalized:
            raise RuntimeError("Interview recovery terminalization failed")
