from __future__ import annotations

import asyncio
import base64
import binascii
import hashlib
import hmac
import json
import time
from collections.abc import Callable, Mapping
from contextlib import suppress
from decimal import Decimal
from typing import Any, Protocol

from fastapi import WebSocket
from prometheus_client import Counter, Gauge, Histogram

from app.bootstrap.settings import Settings
from app.modules.interview.internal.durable_events import (
    canonical_event,
    completed_result,
    failed_result,
    finalized_turn,
    provider_usage,
)
from app.modules.interview.internal.engine import (
    CompletionReason,
    DeterministicInterviewEngine,
    EngineOutput,
    InterviewModelEvaluator,
    SpokenSegment,
    SpokenTurnKind,
    detect_candidate_command,
)
from app.modules.interview.internal.redis_state import InterviewRedisState, RuntimeTokenClaim
from app.modules.model.api import (
    AudioEncoding,
    AudioFrame,
    ChatModelApi,
    StreamingSpeechToTextSessionApi,
    StreamingTextToSpeechApi,
)

TOKEN_REJECTIONS = Counter(
    "interview_runtime_token_rejections_total", "Rejected interview runtime tokens", ["reason"]
)
CONCURRENCY_REJECTIONS = Counter(
    "interview_concurrency_rejections_total", "Rejected interview concurrency admissions", ["scope"]
)
WS_CLOSURES = Counter(
    "interview_twilio_websocket_closures_total", "Twilio media socket closures", ["reason"]
)
SILENCE_OUTCOMES = Counter(
    "interview_silence_outcomes_total", "Interview silence outcomes", ["outcome"]
)
MODEL_ACTIONS = Counter(
    "interview_model_actions_total", "Validated interview model actions", ["action"]
)
MODEL_FAILURES = Counter(
    "interview_model_failures_total", "Interview model failures", ["code"]
)
COMPLETIONS = Counter(
    "interview_completions_total", "Interview completion reasons", ["reason"]
)
WORKPLACE_BANDS = Counter(
    "interview_workplace_english_bands_total", "Advisory English bands", ["band"]
)
PROVIDER_FAILURES = Counter(
    "interview_speech_provider_failures_total", "Interview speech provider failures", ["provider"]
)
FIRST_AUDIO_SECONDS = Histogram(
    "interview_speech_end_to_first_audio_seconds",
    "Time from candidate speech completion to first AI audio",
    buckets=(0.1, 0.25, 0.5, 0.75, 1.0, 1.5, 2.5, 4.0, 8.0),
)
ACTIVE_CALLS = Gauge("interview_active_media_calls", "Active authenticated interview media calls")

TURN_FINALIZED = "interview.turn.finalized"
SESSION_COMPLETED = "interview.session.completed"
SESSION_FAILED = "interview.session.failed"
PROVIDER_USAGE = "interview.provider.usage"


class InterviewEventPublisher(Protocol):
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
    ) -> int: ...

    async def recover_checkpoint(self, session_id: str) -> int | None: ...


class InterviewMediaRuntime:
    def __init__(
        self,
        *,
        settings: Settings,
        state: InterviewRedisState,
        tts: StreamingTextToSpeechApi,
        stt_factory: Callable[[], StreamingSpeechToTextSessionApi],
        model: ChatModelApi | None = None,
        publisher: InterviewEventPublisher | None = None,
        monotonic: Callable[[], float] = time.monotonic,
    ) -> None:
        self._settings = settings
        self._state = state
        self._tts = tts
        self._stt_factory = stt_factory
        self._model = model
        self._publisher = publisher
        self._monotonic = monotonic

    def signature_valid(self, websocket: WebSocket) -> bool:
        raw_headers = dict(websocket.scope.get("headers", []))
        signature = raw_headers.get(b"x-twilio-signature")
        if signature is None:
            return False
        url = self._settings.TWILIO_MEDIA_STREAM_WSS_URL
        query = websocket.scope.get("query_string", b"")
        if query:
            url += "?" + bytes(query).decode("ascii")
        expected = base64.b64encode(
            hmac.new(self._settings.TWILIO_AUTH_TOKEN.encode(), url.encode(), hashlib.sha1).digest()
        )
        return hmac.compare_digest(expected, signature)

    async def run(self, websocket: WebSocket) -> None:
        if not self.signature_valid(websocket):
            TOKEN_REJECTIONS.labels("signature").inc()
            await websocket.close(code=1008, reason="INVALID_TWILIO_SIGNATURE")
            return
        await websocket.accept()
        if not (self._settings.INTERVIEW_ENABLED and self._settings.INTERVIEW_MEDIA_STREAM_ENABLED):
            await websocket.close(code=1008, reason="INTERVIEW_DISABLED")
            return
        if self._settings.INTERVIEW_ENGINE_ENABLED and self._model is None:
            await websocket.close(code=1013, reason="INTERVIEW_RUNTIME_NOT_READY")
            return
        if self._settings.INTERVIEW_DURABLE_RESULTS_ENABLED and self._publisher is None:
            await websocket.close(code=1013, reason="INTERVIEW_RESULTS_NOT_READY")
            return
        evaluator = (
            InterviewModelEvaluator(
                self._model,
                timeout_seconds=self._settings.INTERVIEW_MODEL_TIMEOUT_SECONDS,
                max_attempts=self._settings.INTERVIEW_MODEL_MAX_ATTEMPTS,
            )
            if self._model is not None and self._settings.INTERVIEW_ENGINE_ENABLED
            else None
        )
        session = _MediaSession(
            websocket=websocket,
            settings=self._settings,
            state=self._state,
            tts=self._tts,
            stt_factory=self._stt_factory,
            evaluator=evaluator,
            publisher=self._publisher,
            monotonic=self._monotonic,
        )
        ACTIVE_CALLS.inc()
        try:
            await session.run()
        except RuntimeError as exception:
            reason = _bounded_reason(str(exception))
            WS_CLOSURES.labels(reason).inc()
            await _safe_close(websocket, 1008, reason)
        except Exception:
            WS_CLOSURES.labels("INTERNAL_ERROR").inc()
            await _safe_close(websocket, 1011, "INTERNAL_ERROR")
        finally:
            ACTIVE_CALLS.dec()
            await session.cleanup()


class _MediaSession:
    def __init__(
        self,
        *,
        websocket: WebSocket,
        settings: Settings,
        state: InterviewRedisState,
        tts: StreamingTextToSpeechApi,
        stt_factory: Callable[[], StreamingSpeechToTextSessionApi],
        evaluator: InterviewModelEvaluator | None,
        publisher: InterviewEventPublisher | None = None,
        monotonic: Callable[[], float],
        query_token: str | None = None,
    ) -> None:
        self.websocket = websocket
        self.settings = settings
        self.state = state
        self.tts = tts
        self.stt_factory = stt_factory
        self.evaluator = evaluator
        self.publisher = publisher
        self.monotonic = monotonic
        self.expected_sequence = 1
        self.stream_sid = ""
        self.call_sid = ""
        self.claim: RuntimeTokenClaim | None = None
        self.prepared: dict[str, Any] | None = None
        self.engine: DeterministicInterviewEngine | None = None
        self.stt: StreamingSpeechToTextSessionApi | None = None
        self.language_tag = "en-US"
        self.mark_waiting = ""
        self.mark_counter = 0
        self.listening = False
        self.speech_started = False
        self.speech_ms = 0
        self.silence_ms = 0
        self.waiting_ms = 0
        self.partial_transcript = ""
        self.smoke_silence_prompts = 0
        self.closing = False
        self.clean_finished = False
        self.heartbeat_task: asyncio.Task[None] | None = None
        self.pending_model_task: asyncio.Task[Any] | None = None
        self.pending_candidate_task: asyncio.Task[int] | None = None
        self.pending_candidate_turn_id: str | None = None
        self.pending_transcript = ""
        self.pending_speech_end: float | None = None
        self.partial_transcript = ""
        self.awaiting_segments: tuple[SpokenSegment, ...] = ()
        self.awaiting_segment_started_ms: list[int] = []
        self.checkpoint_revision = 0
        self.execution_lease: str | None = None
        self.connected_monotonic: float | None = None
        self.stt_audio_ms = 0
        self.tts_characters = 0
        self.twilio_media_ms = 0

    async def run(self) -> None:
        while True:
            message = await self.websocket.receive_text()
            try:
                event = json.loads(message)
            except json.JSONDecodeError as exception:
                raise RuntimeError("MALFORMED_JSON") from exception
            event_type = str(event.get("event", ""))
            if event_type not in {"connected", "start", "media", "mark", "dtmf", "stop"}:
                raise RuntimeError("UNSUPPORTED_EVENT")
            self._sequence(event_type, event)
            if event_type == "connected":
                self._connected(event)
            elif event_type == "start":
                await self._start(event)
            elif event_type == "media":
                await self._media(event)
            elif event_type == "mark":
                await self._mark(event)
                if self.clean_finished:
                    return
            elif event_type == "dtmf":
                self._dtmf(event)
            elif event_type == "stop":
                self._stream_consistent(event)
                WS_CLOSURES.labels("TWILIO_STOP").inc()
                return

    async def cleanup(self) -> None:
        if self.heartbeat_task is not None:
            self.heartbeat_task.cancel()
            with suppress(asyncio.CancelledError):
                await self.heartbeat_task
        if self.pending_model_task is not None:
            if not self.pending_model_task.done():
                self.pending_model_task.cancel()
            with suppress(asyncio.CancelledError, Exception):
                await self.pending_model_task
        if self.pending_candidate_task is not None:
            with suppress(Exception):
                self.checkpoint_revision = await self.pending_candidate_task
            self.pending_candidate_task = None
        await self._close_stt()
        if (
            not self.clean_finished
            and self._durable
            and self.claim is not None
            and self.engine is not None
        ):
            if self.partial_transcript.strip():
                with suppress(Exception):
                    await self._publish_candidate_turn(
                        self.partial_transcript.strip(), interrupted=True
                    )
            with suppress(Exception):
                await self._checkpoint(
                    "AI_AUDIO_AWAITING_MARK"
                    if self.awaiting_segments
                    else "INTERRUPTED_RECOVERY"
                )
                await self.state.mark_recoverable(
                    self.claim.session_id,
                    recover_at_epoch_seconds=int(time.time())
                    + self.settings.INTERVIEW_SESSION_LEASE_SECONDS,
                )
        if self.engine is not None:
            self.engine.discard()
        if self.claim is not None:
            if self.clean_finished:
                await self.state.terminalize_runtime_session(
                    self.claim.session_id, self.claim.call_attempt_id, self.call_sid
                )
            else:
                await self.state.release_runtime_claim(
                    self.claim.session_id, self.claim.call_attempt_id
                )
        if self.execution_lease is not None and self.claim is not None:
            await self.state.release_lease(self.claim.session_id, self.execution_lease)

    @property
    def _durable(self) -> bool:
        return bool(
            self.settings.INTERVIEW_DURABLE_RESULTS_ENABLED and self.publisher is not None
        )

    def _sequence(self, event_type: str, event: dict[str, Any]) -> None:
        if event_type == "connected" and "sequenceNumber" not in event:
            return
        try:
            sequence = int(event["sequenceNumber"])
        except (KeyError, TypeError, ValueError) as exception:
            raise RuntimeError("INVALID_SEQUENCE") from exception
        if sequence != self.expected_sequence:
            raise RuntimeError("SEQUENCE_GAP")
        self.expected_sequence += 1

    def _connected(self, event: dict[str, Any]) -> None:
        if event.get("protocol") != "Call" or event.get("version") not in {"1.0", "1.0.0"}:
            raise RuntimeError("INVALID_CONNECTED_EVENT")

    async def _start(self, event: dict[str, Any]) -> None:
        start = event.get("start") or {}
        if start.get("accountSid") != self.settings.TWILIO_ACCOUNT_SID:
            raise RuntimeError("ACCOUNT_SID_MISMATCH")
        self.call_sid = str(start.get("callSid", ""))
        self.stream_sid = str(start.get("streamSid", event.get("streamSid", "")))
        if not self.call_sid.startswith("CA") or not self.stream_sid.startswith("MZ"):
            raise RuntimeError("INVALID_STREAM_BINDING")
        media = start.get("mediaFormat") or {}
        if media != {"encoding": "audio/x-mulaw", "sampleRate": 8000, "channels": 1}:
            raise RuntimeError("INVALID_MEDIA_FORMAT")
        custom = (start.get("customParameters") or {}).get("token")
        token = custom
        if not token:
            raise RuntimeError("MISSING_RUNTIME_TOKEN")
        try:
            self.claim = await self.state.claim_runtime_token(
                token=token,
                call_sid=self.call_sid,
                global_limit=self.settings.INTERVIEW_GLOBAL_CONCURRENCY,
                tenant_limit=self.settings.INTERVIEW_TENANT_CONCURRENCY,
                lease_seconds=self.settings.INTERVIEW_SESSION_LEASE_SECONDS,
            )
        except ValueError as exception:
            TOKEN_REJECTIONS.labels("claim").inc()
            raise RuntimeError("RUNTIME_TOKEN_REJECTED") from exception
        except RuntimeError as exception:
            CONCURRENCY_REJECTIONS.labels("limit").inc()
            raise RuntimeError("CONCURRENCY_REJECTED") from exception
        self.prepared = await self.state.prepared_session(self.claim.session_id)
        if self.prepared is None:
            raise RuntimeError("PREPARED_SESSION_MISSING")
        self.execution_lease = await self.state.acquire_lease(self.claim.session_id)
        if self.execution_lease is None:
            raise RuntimeError("EXECUTION_LEASE_HELD")
        await self.state.clear_recoverable(self.claim.session_id)
        self.connected_monotonic = self.monotonic()
        self.heartbeat_task = asyncio.create_task(self._heartbeat_loop())
        payload = self.prepared["payload"]
        sections = payload["sections"]
        core = next((item for item in sections if item["kind"] == "CORE"), sections[0])
        self.language_tag = str(core["languageTag"])
        if self.settings.INTERVIEW_TRANSPORT_SMOKE_MODE:
            output = EngineOutput(
                str(payload["introductionText"]) + " " + str(core["questions"][0]["prompt"]),
                self.language_tag,
                True,
            )
        else:
            checkpoint = await self.state.load_checkpoint(self.claim.session_id)
            if checkpoint is not None and self._durable:
                assert self.publisher is not None
                recovered = await self.publisher.recover_checkpoint(self.claim.session_id)
                if recovered is not None:
                    checkpoint = await self.state.load_checkpoint(self.claim.session_id)
                if checkpoint is None:
                    raise RuntimeError("CHECKPOINT_RECOVERY_FAILED")
                if checkpoint.call_sid and checkpoint.call_sid != self.call_sid:
                    raise RuntimeError("CHECKPOINT_CALL_MISMATCH")
                self.checkpoint_revision = checkpoint.revision
                self._restore_usage(checkpoint.runtime_state)
                self.engine = DeterministicInterviewEngine.restore(
                    payload,
                    checkpoint.runtime_state["engine"],
                    self.monotonic(),
                    min_question_window_seconds=(
                        self.settings.INTERVIEW_MIN_QUESTION_WINDOW_SECONDS
                    ),
                    closing_reserve_seconds=self.settings.INTERVIEW_CLOSING_RESERVE_SECONDS,
                    max_consecutive_failures=(
                        self.settings.INTERVIEW_ENGINE_MAX_CONSECUTIVE_FAILURES
                    ),
                )
                pending_audio = checkpoint.runtime_state.get("pending_audio")
                if pending_audio:
                    self.awaiting_segments = tuple(
                        SpokenSegment(
                            text=str(item["text"]),
                            language_tag=str(item["language_tag"]),
                            kind=SpokenTurnKind(str(item["kind"])),
                            speaker=str(item["speaker"]),
                            section_id=item.get("section_id"),
                            question_id=item.get("question_id"),
                        )
                        for item in pending_audio
                    )
                    await self._send_audio_segments(self.awaiting_segments, "replay")
                    return
                if checkpoint.phase in {"LISTENING", "INTERRUPTED_RECOVERY"}:
                    self.language_tag = self.engine.language_tag
                    await self._checkpoint("LISTENING")
                    await self._start_listening_turn()
                    return
                if checkpoint.phase == "TERMINAL_COMPLETE":
                    self.clean_finished = True
                    await _safe_close(self.websocket, 1000, "ALREADY_COMPLETE")
                    return
                raise RuntimeError("CHECKPOINT_PHASE_INVALID")
            self.engine = DeterministicInterviewEngine(
                payload,
                min_question_window_seconds=self.settings.INTERVIEW_MIN_QUESTION_WINDOW_SECONDS,
                closing_reserve_seconds=self.settings.INTERVIEW_CLOSING_RESERVE_SECONDS,
                max_consecutive_failures=self.settings.INTERVIEW_ENGINE_MAX_CONSECUTIVE_FAILURES,
            )
            output = self.engine.begin(self.monotonic())
        await self._send_output(output, "opening")

    async def _media(self, event: dict[str, Any]) -> None:
        self._stream_consistent(event)
        media = event.get("media") or {}
        try:
            data = base64.b64decode(str(media["payload"]), validate=True)
        except (KeyError, binascii.Error, ValueError) as exception:
            raise RuntimeError("INVALID_MEDIA_PAYLOAD") from exception
        if not data or len(data) > self.settings.INTERVIEW_MEDIA_MAX_PAYLOAD_BYTES:
            raise RuntimeError("MEDIA_PAYLOAD_SIZE")
        duration_ms = max(1, len(data) // 8)
        self.twilio_media_ms += duration_ms
        if not self.listening:
            return
        energy = _mulaw_energy(data)
        threshold = self.settings.INTERVIEW_SPEECH_ENERGY_THRESHOLD
        if not self.speech_started:
            self.waiting_ms += duration_ms
            if energy >= threshold:
                self.speech_started = True
                self.speech_ms = duration_ms
                self.silence_ms = 0
                await self._send_stt(data)
            elif await self._silence_timeout():
                return
        else:
            self.speech_ms += duration_ms
            self.silence_ms = 0 if energy >= threshold else self.silence_ms + duration_ms
            await self._send_stt(data)
            limit = (
                self.settings.INTERVIEW_SMOKE_UTTERANCE_MAX_SECONDS
                if self.settings.INTERVIEW_TRANSPORT_SMOKE_MODE
                else self.settings.INTERVIEW_UTTERANCE_MAX_SECONDS
            ) * 1000
            if (
                self.silence_ms >= self.settings.INTERVIEW_END_OF_UTTERANCE_SILENCE_MS
                or self.speech_ms >= limit
            ):
                await self._finish_utterance()

    async def _finish_utterance(self) -> None:
        if self.stt is None:
            raise RuntimeError("STT_NOT_STARTED")
        speech_end = self.monotonic()
        try:
            events = await self.stt.finish()
        except Exception as exception:
            PROVIDER_FAILURES.labels("stt").inc()
            raise RuntimeError("STT_FAILURE") from exception
        transcript = " ".join(
            getattr(item, "text", "") for item in events if getattr(item, "is_final", False)
        ).strip()
        if not transcript:
            transcript = self.partial_transcript.strip()
        self.partial_transcript = ""
        await self._close_stt()
        self.listening = False
        SILENCE_OUTCOMES.labels("utterance_complete").inc()
        if self.settings.INTERVIEW_TRANSPORT_SMOKE_MODE:
            assert self.prepared is not None
            await self._send_output(
                EngineOutput(
                    str(self.prepared["payload"]["closingText"]), self.language_tag, False, True
                ),
                "smoke-closing",
            )
            return
        assert self.engine is not None and self.evaluator is not None
        command = detect_candidate_command(transcript, self.engine.language_tag)
        if command is not None:
            await self._send_output(
                self.engine.handle_command(command, self.monotonic()),
                f"command-{command.value.lower()}",
            )
            return
        question = self.engine.active_question
        if question is None:
            raise RuntimeError("ACTIVE_QUESTION_MISSING")
        self.pending_transcript = transcript
        self.pending_speech_end = speech_end
        self.pending_model_task = asyncio.create_task(
            self.evaluator.evaluate(
                question=question,
                transcript=transcript,
                language_tag=self.engine.language_tag,
                english_screen=self.engine.english_screen,
            )
        )
        acknowledgement = "Cảm ơn." if self.engine.language_tag == "vi-VN" else "Thank you."
        acknowledgement_segment = SpokenSegment(
            text=acknowledgement,
            language_tag=self.engine.language_tag,
            kind=SpokenTurnKind.ACKNOWLEDGEMENT,
            speaker="INTERVIEWER",
            section_id=str(self.engine.current_section["sectionId"]),
            question_id=str(question["questionId"]),
        )
        self.awaiting_segments = (acknowledgement_segment,)
        if self._durable:
            self.pending_candidate_task = asyncio.create_task(
                self._publish_candidate_turn(transcript, interrupted=False)
            )
        await self._send_audio_segments(
            self.awaiting_segments,
            "acknowledgement",
            latency_started_at=self.pending_speech_end,
        )

    async def _silence_timeout(self) -> bool:
        assert self.prepared is not None
        limits = self.prepared["payload"]["interactionLimits"]
        if self.waiting_ms < int(limits["silenceTimeoutSeconds"]) * 1000:
            return False
        await self._close_stt()
        self.listening = False
        if self.settings.INTERVIEW_TRANSPORT_SMOKE_MODE:
            if self.smoke_silence_prompts >= int(limits["silencePromptLimit"]):
                SILENCE_OUTCOMES.labels("exhausted").inc()
                await _safe_close(self.websocket, 1000, "SILENCE_EXHAUSTED")
                return True
            self.smoke_silence_prompts += 1
            output = EngineOutput(
                "Vui lòng trả lời ngắn gọn."
                if self.language_tag == "vi-VN"
                else "Please give a short answer.",
                self.language_tag,
                True,
            )
        else:
            assert self.engine is not None
            output = self.engine.handle_silence(self.monotonic())
        SILENCE_OUTCOMES.labels("reprompted" if output.listen else "exhausted").inc()
        await self._send_output(output, "silence")
        return False

    async def _mark(self, event: dict[str, Any]) -> None:
        self._stream_consistent(event)
        name = str((event.get("mark") or {}).get("name", ""))
        if not self.mark_waiting or name != self.mark_waiting:
            raise RuntimeError("UNEXPECTED_MARK")
        self.mark_waiting = ""
        if self.pending_candidate_task is not None:
            self.checkpoint_revision = await self.pending_candidate_task
            self.pending_candidate_task = None
        if self._durable and self.awaiting_segments:
            await self._publish_awaiting_ai_segments()
        self.awaiting_segments = ()
        self.awaiting_segment_started_ms = []
        if self.pending_model_task is not None:
            action = await self.pending_model_task
            self.pending_model_task = None
            assert self.engine is not None
            if action is None:
                MODEL_FAILURES.labels("exhausted").inc()
                output = self.engine.handle_model_failure(self.monotonic())
            else:
                MODEL_ACTIONS.labels(action.action.value).inc()
                output = self.engine.apply_model_action(
                    action,
                    self.pending_transcript,
                    self.monotonic(),
                    candidate_turn_id=self.pending_candidate_turn_id,
                )
            self.pending_transcript = ""
            self.pending_candidate_turn_id = None
            self.pending_speech_end = None
            await self._send_output(output, "model-result")
            return
        if self.closing:
            if self._durable:
                await self._publish_terminal_and_usage()
            self.clean_finished = True
            reason = (
                self.engine.terminal_reason.value
                if self.engine is not None and self.engine.terminal_reason is not None
                else "SMOKE_COMPLETE"
            )
            COMPLETIONS.labels(reason).inc()
            if self.engine is not None:
                band = self.engine.workplace_band()
                if band is not None:
                    WORKPLACE_BANDS.labels(band.value).inc()
            WS_CLOSURES.labels(reason).inc()
            await _safe_close(self.websocket, 1000, reason)
            return
        if self._durable:
            await self._checkpoint("LISTENING")
        await self._start_listening_turn()

    async def _start_listening_turn(self) -> None:
        await self._close_stt()
        self.stt = self.stt_factory()
        try:
            await self.stt.start(language_tag=self.language_tag)
        except Exception as exception:
            PROVIDER_FAILURES.labels("stt").inc()
            raise RuntimeError("STT_FAILURE") from exception
        self.listening = True
        self.speech_started = False
        self.speech_ms = 0
        self.silence_ms = 0
        self.waiting_ms = 0

    async def _send_stt(self, data: bytes) -> None:
        if self.stt is None:
            raise RuntimeError("STT_NOT_STARTED")
        try:
            events = await self.stt.send(
                AudioFrame(
                    data=data,
                    timestamp_ms=self.speech_ms,
                    sample_rate_hz=8000,
                    channels=1,
                    encoding=AudioEncoding.MULAW,
                )
            )
            self.stt_audio_ms += max(1, len(data) // 8)
            transcripts = [
                str(getattr(item, "text", "")).strip()
                for item in events
                if str(getattr(item, "text", "")).strip()
            ]
            if transcripts:
                self.partial_transcript = " ".join(transcripts)[-8000:]
        except Exception as exception:
            PROVIDER_FAILURES.labels("stt").inc()
            raise RuntimeError("STT_FAILURE") from exception

    async def _close_stt(self) -> None:
        if self.stt is not None:
            with suppress(Exception):
                await self.stt.close()
            self.stt = None

    async def _send_output(self, output: EngineOutput, purpose: str) -> None:
        self.language_tag = output.language_tag
        self.listening = False
        self.closing = output.terminal
        self.awaiting_segments = output.audible_segments
        if self._durable:
            await self._checkpoint("AI_AUDIO_AWAITING_MARK")
        await self._send_audio_segments(self.awaiting_segments, purpose)

    async def _send_audio_segments(
        self,
        segments: tuple[SpokenSegment, ...],
        purpose: str,
        *,
        latency_started_at: float | None = None,
    ) -> None:
        first = True
        self.awaiting_segment_started_ms = []
        for segment in segments:
            self.awaiting_segment_started_ms.append(int(time.time() * 1000))
            self.tts_characters += len(segment.text)
            try:
                async for frame in self.tts.synthesize(
                    segment.text, language_tag=segment.language_tag
                ):
                    if first and latency_started_at is not None:
                        FIRST_AUDIO_SECONDS.observe(
                            max(0.0, self.monotonic() - latency_started_at)
                        )
                    first = False
                    await self.websocket.send_json(
                        {
                            "event": "media",
                            "streamSid": self.stream_sid,
                            "media": {"payload": base64.b64encode(frame.data).decode("ascii")},
                        }
                    )
            except Exception as exception:
                PROVIDER_FAILURES.labels("tts").inc()
                raise RuntimeError("TTS_FAILURE") from exception
        self.mark_counter += 1
        self.mark_waiting = f"ai-{self.mark_counter}-{purpose}"
        await self.websocket.send_json(
            {"event": "mark", "streamSid": self.stream_sid, "mark": {"name": self.mark_waiting}}
        )

    async def _send_audio_batch(
        self,
        text: str,
        language_tag: str,
        purpose: str,
        *,
        latency_started_at: float | None = None,
    ) -> None:
        """Compatibility adapter for the Phase 7 transport-level tests."""
        await self._send_audio_segments(
            (
                SpokenSegment(
                    text=text,
                    language_tag=language_tag,
                    kind=SpokenTurnKind.QUESTION,
                    speaker="INTERVIEWER",
                ),
            ),
            purpose,
            latency_started_at=latency_started_at,
        )

    def _runtime_state(
        self,
        *,
        next_turn_sequence: int | None = None,
        include_pending_audio: bool = True,
    ) -> dict[str, Any]:
        assert self.engine is not None
        engine = self.engine.snapshot(self.monotonic())
        if next_turn_sequence is not None:
            engine["next_turn_sequence"] = next_turn_sequence
        pending_audio = None
        if include_pending_audio and self.awaiting_segments:
            pending_audio = [
                {
                    "text": item.text,
                    "language_tag": item.language_tag,
                    "kind": item.kind.value,
                    "speaker": item.speaker,
                    "section_id": item.section_id,
                    "question_id": item.question_id,
                }
                for item in self.awaiting_segments
            ]
        return {
            "engine": engine,
            "pending_audio": pending_audio,
            "usage": {
                "stt_audio_ms": self.stt_audio_ms,
                "tts_characters": self.tts_characters,
                "twilio_media_ms": self.twilio_media_ms,
                "llm_tokens": self.evaluator.measured_tokens if self.evaluator else 0,
            },
        }

    def _restore_usage(self, runtime_state: Mapping[str, Any]) -> None:
        usage = runtime_state.get("usage") or {}
        self.stt_audio_ms = int(usage.get("stt_audio_ms", 0))
        self.tts_characters = int(usage.get("tts_characters", 0))
        self.twilio_media_ms = int(usage.get("twilio_media_ms", 0))
        if self.evaluator is not None:
            self.evaluator.measured_tokens = int(usage.get("llm_tokens", 0))

    async def _checkpoint(self, phase: str) -> None:
        if not self._durable or self.claim is None or self.engine is None:
            return
        self.checkpoint_revision = await self.state.cas_checkpoint(
            self.claim.session_id,
            expected_revision=self.checkpoint_revision,
            phase=phase,
            runtime_state=self._runtime_state(
                include_pending_audio=phase == "AI_AUDIO_AWAITING_MARK"
            ),
            call_sid=self.call_sid,
        )

    async def _publish_candidate_turn(self, transcript: str, *, interrupted: bool) -> int:
        assert self.claim is not None and self.engine is not None and self.publisher is not None
        question = self.engine.active_question
        if question is None:
            raise RuntimeError("ACTIVE_QUESTION_MISSING")
        sequence = self.engine.next_turn_sequence
        ended = int(time.time() * 1000)
        event = finalized_turn(
            tenant_id=self.claim.tenant_id,
            session_id=self.claim.session_id,
            call_attempt_id=self.claim.call_attempt_id,
            sequence=sequence,
            speaker="CANDIDATE",
            turn_kind="CANDIDATE_UTTERANCE",
            section_id=str(self.engine.current_section["sectionId"]),
            question_id=str(question["questionId"]),
            language_tag=self.engine.language_tag,
            started_at_epoch_ms=max(0, ended - max(1, self.speech_ms)),
            ended_at_epoch_ms=ended,
            transcript=transcript[:8000],
            interrupted=interrupted,
        )
        self.pending_candidate_turn_id = str(event.turn_id)
        next_state = self._runtime_state(next_turn_sequence=sequence + 1)
        revision = await self._publish_checkpointed_event(
            phase="CANDIDATE_PUBLICATION",
            commit_phase=("INTERRUPTED_RECOVERY" if interrupted else "CANDIDATE_EVALUATION"),
            event_id=str(event.event_id),
            routing_key=TURN_FINALIZED,
            payload=canonical_event(event),
            current_state=self._runtime_state(),
            next_state=next_state,
        )
        self.engine.next_turn_sequence = sequence + 1
        self.checkpoint_revision = revision
        return revision

    async def _publish_awaiting_ai_segments(self) -> None:
        assert self.engine is not None
        ended = int(time.time() * 1000)
        for index, segment in enumerate(self.awaiting_segments):
            sequence = self.engine.next_turn_sequence
            started = (
                self.awaiting_segment_started_ms[index]
                if index < len(self.awaiting_segment_started_ms)
                else ended
            )
            event = finalized_turn(
                tenant_id=self.claim.tenant_id,  # type: ignore[union-attr]
                session_id=self.claim.session_id,  # type: ignore[union-attr]
                call_attempt_id=self.claim.call_attempt_id,  # type: ignore[union-attr]
                sequence=sequence,
                speaker=segment.speaker,
                turn_kind=segment.kind.value,
                section_id=segment.section_id,
                question_id=segment.question_id,
                language_tag=segment.language_tag,
                started_at_epoch_ms=started,
                ended_at_epoch_ms=max(started, ended),
                transcript=segment.text,
                interrupted=False,
            )
            revision = await self._publish_checkpointed_event(
                phase="AI_AUDIO_AWAITING_MARK",
                commit_phase="AI_AUDIO_AWAITING_MARK",
                event_id=str(event.event_id),
                routing_key=TURN_FINALIZED,
                payload=canonical_event(event),
                current_state=self._runtime_state(),
                next_state=self._runtime_state(next_turn_sequence=sequence + 1),
            )
            self.engine.next_turn_sequence = sequence + 1
            self.checkpoint_revision = revision

    async def _publish_checkpointed_event(
        self,
        *,
        phase: str,
        commit_phase: str,
        event_id: str,
        routing_key: str,
        payload: bytes,
        current_state: Mapping[str, Any],
        next_state: Mapping[str, Any],
    ) -> int:
        assert self.claim is not None and self.publisher is not None
        try:
            return await self.publisher.publish_checkpointed(
                session_id=self.claim.session_id,
                expected_revision=self.checkpoint_revision,
                phase=phase,
                commit_phase=commit_phase,
                current_runtime_state=current_state,
                next_runtime_state=next_state,
                event_id=event_id,
                routing_key=routing_key,
                payload=payload,
                call_sid=self.call_sid,
            )
        except Exception:
            for _ in range(1, self.settings.INTERVIEW_PUBLISH_CONFIRM_MAX_ATTEMPTS):
                recovered = await self.publisher.recover_checkpoint(self.claim.session_id)
                if recovered is not None:
                    return recovered
            raise

    async def _publish_terminal_and_usage(self) -> None:
        assert self.claim is not None and self.engine is not None
        partial = self.engine.terminal_reason is not CompletionReason.COMPLETED
        result = self.engine.result_snapshot(partial=partial)
        expected_turn_count = self.engine.next_turn_sequence - 1
        connected_seconds = int(
            max(0.0, self.monotonic() - (self.connected_monotonic or self.monotonic()))
        )
        usages = [
            ("CARTESIA", "STT", Decimal(self.stt_audio_ms) / Decimal(1000), "AUDIO_SECOND"),
            ("CARTESIA", "TTS", Decimal(self.tts_characters), "CHARACTER"),
            (
                "OPENAI" if self.settings.LLM_PROVIDER == "openai" else "OLLAMA",
                "LLM",
                Decimal(self.evaluator.measured_tokens if self.evaluator else 0),
                "TOKEN",
            ),
            (
                "TWILIO",
                "MEDIA_STREAM",
                Decimal(self.twilio_media_ms) / Decimal(1000),
                "AUDIO_SECOND",
            ),
        ]
        for provider, capability, quantity, unit in usages:
            if quantity <= 0:
                continue
            event = provider_usage(
                tenant_id=self.claim.tenant_id,
                session_id=self.claim.session_id,
                call_attempt_id=self.claim.call_attempt_id,
                provider=provider,
                capability=capability,
                quantity=quantity,
                unit=unit,
            )
            self.checkpoint_revision = await self._publish_checkpointed_event(
                phase="TERMINAL_PUBLICATION",
                commit_phase="TERMINAL_PUBLICATION",
                event_id=str(event.event_id),
                routing_key=PROVIDER_USAGE,
                payload=canonical_event(event),
                current_state=self._runtime_state(include_pending_audio=False),
                next_state=self._runtime_state(include_pending_audio=False),
            )
        if self.engine.terminal_reason is CompletionReason.MODEL_FAILURE_LIMIT:
            failed = failed_result(
                tenant_id=self.claim.tenant_id,
                session_id=self.claim.session_id,
                call_attempt_id=self.claim.call_attempt_id,
                failure_code="MODEL_FAILURE_LIMIT",
                retryable=False,
                detail="The deterministic model failure limit was reached.",
                expected_turn_count=expected_turn_count,
                connected_seconds=connected_seconds,
                result=result,
            )
            routing_key = SESSION_FAILED
            terminal: Any = failed
        else:
            reasons = {
                CompletionReason.COMPLETED: "FINISHED",
                CompletionReason.CANDIDATE_STOP: "CANDIDATE_STOPPED",
                CompletionReason.TIME_LIMIT: "TIME_LIMIT",
            }
            reason = self.engine.terminal_reason
            completed = completed_result(
                tenant_id=self.claim.tenant_id,
                session_id=self.claim.session_id,
                call_attempt_id=self.claim.call_attempt_id,
                completion_reason=reasons[reason] if reason in reasons else "PARTIAL",
                expected_turn_count=expected_turn_count,
                connected_seconds=connected_seconds,
                result=result,
            )
            routing_key = SESSION_COMPLETED
            terminal = completed
        self.checkpoint_revision = await self._publish_checkpointed_event(
            phase="TERMINAL_PUBLICATION",
            commit_phase="TERMINAL_COMPLETE",
            event_id=str(terminal.event_id),
            routing_key=routing_key,
            payload=canonical_event(terminal),
            current_state=self._runtime_state(include_pending_audio=False),
            next_state=self._runtime_state(include_pending_audio=False),
        )

    async def _heartbeat_loop(self) -> None:
        assert self.claim is not None
        while True:
            await asyncio.sleep(self.settings.INTERVIEW_SESSION_HEARTBEAT_SECONDS)
            if not await self.state.renew_runtime_session(
                self.claim.session_id,
                self.call_sid,
                lease_seconds=self.settings.INTERVIEW_SESSION_LEASE_SECONDS,
            ):
                await _safe_close(self.websocket, 1011, "LEASE_LOST")
                return
            if self.execution_lease is None or not await self.state.renew_lease(
                self.claim.session_id, self.execution_lease
            ):
                await _safe_close(self.websocket, 1011, "EXECUTION_LEASE_LOST")
                return

    def _dtmf(self, event: dict[str, Any]) -> None:
        self._stream_consistent(event)
        digit = str((event.get("dtmf") or {}).get("digit", ""))
        if len(digit) != 1 or digit not in "0123456789*#":
            raise RuntimeError("INVALID_DTMF")

    def _stream_consistent(self, event: dict[str, Any]) -> None:
        stream = str(event.get("streamSid", ""))
        if not self.stream_sid or stream != self.stream_sid:
            raise RuntimeError("STREAM_SID_MISMATCH")


def _mulaw_energy(data: bytes) -> float:
    total = 0
    for value in data:
        value = ~value & 0xFF
        mantissa = value & 0x0F
        exponent = (value >> 4) & 0x07
        sample = ((mantissa << 3) + 0x84) << exponent
        sample -= 0x84
        total += abs(-sample if value & 0x80 else sample)
    return total / max(1, len(data))


async def _safe_close(websocket: WebSocket, code: int, reason: str) -> None:
    try:
        await websocket.close(code=code, reason=reason)
    except RuntimeError:
        pass


def _bounded_reason(value: str) -> str:
    allowed = {
        "MALFORMED_JSON",
        "UNSUPPORTED_EVENT",
        "INVALID_SEQUENCE",
        "SEQUENCE_GAP",
        "INVALID_CONNECTED_EVENT",
        "ACCOUNT_SID_MISMATCH",
        "INVALID_STREAM_BINDING",
        "INVALID_MEDIA_FORMAT",
        "TOKEN_SOURCE_MISMATCH",
        "MISSING_RUNTIME_TOKEN",
        "RUNTIME_TOKEN_REJECTED",
        "CONCURRENCY_REJECTED",
        "PREPARED_SESSION_MISSING",
        "EXECUTION_LEASE_HELD",
        "EXECUTION_LEASE_LOST",
        "CHECKPOINT_RECOVERY_FAILED",
        "CHECKPOINT_CALL_MISMATCH",
        "CHECKPOINT_PHASE_INVALID",
        "INVALID_MEDIA_PAYLOAD",
        "MEDIA_PAYLOAD_SIZE",
        "LEASE_LOST",
        "UNEXPECTED_MARK",
        "INVALID_DTMF",
        "STREAM_SID_MISMATCH",
        "STT_NOT_STARTED",
        "STT_FAILURE",
        "TTS_FAILURE",
        "ACTIVE_QUESTION_MISSING",
    }
    return value if value in allowed else "PROTOCOL_ERROR"
