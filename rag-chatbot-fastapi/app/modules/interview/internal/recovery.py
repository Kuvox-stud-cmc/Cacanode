from __future__ import annotations

import asyncio
from contextlib import suppress
from decimal import Decimal
from typing import Literal, Protocol

from app.modules.interview.internal.durable_events import (
    canonical_event,
    failed_result,
    provider_usage,
)
from app.modules.interview.internal.engine import DeterministicInterviewEngine
from app.modules.interview.internal.redis_state import InterviewRedisState
from app.modules.interview.transport.rabbitmq import ConfirmedInterviewPublisher


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
                    continue

    async def _recover(self, session_id: str) -> None:
        lease = await self._state.acquire_lease(session_id)
        if lease is None:
            return
        try:
            recovered = await self._publisher.recover_checkpoint(session_id)
            checkpoint = await self._state.load_checkpoint(session_id)
            prepared = await self._state.prepared_session(session_id)
            if checkpoint is None or prepared is None or prepared.get("status") == "terminal":
                await self._state.clear_recoverable(session_id)
                return
            if recovered is not None:
                checkpoint = await self._state.load_checkpoint(session_id)
                if checkpoint is None:
                    return
            payload = prepared["payload"]
            engine = DeterministicInterviewEngine.restore(
                payload, checkpoint.runtime_state["engine"], 0.0
            )
            result = engine.result_snapshot(partial=True)
            usage = checkpoint.runtime_state.get("usage") or {}
            revision = checkpoint.revision
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
                    current_runtime_state=checkpoint.runtime_state,
                    next_runtime_state=checkpoint.runtime_state,
                    event_id=str(event.event_id),
                    routing_key="interview.provider.usage",
                    payload=canonical_event(event),
                    call_sid=checkpoint.call_sid,
                )
            terminal = failed_result(
                tenant_id=str(prepared["tenant_id"]),
                session_id=session_id,
                call_attempt_id=str(prepared["call_attempt_id"]),
                failure_code="RECOVERY_EXPIRED",
                retryable=False,
                detail="The media connection was not restored before the execution lease expired.",
                expected_turn_count=engine.next_turn_sequence - 1,
                connected_seconds=int(
                    checkpoint.runtime_state["engine"].get("session_elapsed_seconds") or 0
                ),
                result=result,
            )
            await self._publisher.publish_checkpointed(
                session_id=session_id,
                expected_revision=revision,
                phase="TERMINAL_PUBLICATION",
                commit_phase="TERMINAL_COMPLETE",
                current_runtime_state=checkpoint.runtime_state,
                next_runtime_state=checkpoint.runtime_state,
                event_id=str(terminal.event_id),
                routing_key="interview.session.failed",
                payload=canonical_event(terminal),
                call_sid=checkpoint.call_sid,
            )
            await self._state.cancel_runtime_session(
                session_id, str(prepared["call_attempt_id"])
            )
        finally:
            await self._state.release_lease(session_id, lease)
