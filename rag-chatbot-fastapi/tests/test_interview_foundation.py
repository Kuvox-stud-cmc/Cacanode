from __future__ import annotations

import grpc
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from app.bootstrap.settings import Settings
from app.generated import cacanode_ai_v1_pb2 as pb
from app.modules.interview.internal.redis_state import (
    CHECKPOINT_RETENTION_SECONDS,
    EVENT_RETENTION_SECONDS,
    LEASE_HEARTBEAT_SECONDS,
    LEASE_SECONDS,
    RESUME_RETENTION_SECONDS,
    SESSION_RETENTION_SECONDS,
    TOKEN_TTL_SECONDS,
    InterviewRedisKeys,
    payload_sha256,
)
from app.modules.interview.internal.runtime import ConfiguredInterviewRuntime
from app.modules.interview.transport.grpc import InterviewGrpcHandler
from app.modules.interview.transport.http import interview_router


class Context:
    async def abort(self, code: grpc.StatusCode, details: str) -> None:
        raise RuntimeError(f"{code.name}:{details}")


def test_interview_redis_keys_and_ttls_are_stable_and_pii_free() -> None:
    keys = InterviewRedisKeys("ccn:v1:")
    assert keys.session("session-id") == "ccn:v1:interview:session:session-id"
    assert keys.checkpoint("session-id") == "ccn:v1:interview:checkpoint:session-id"
    assert keys.lease("session-id") == "ccn:v1:interview:lease:session-id"
    assert keys.resume("analysis-id") == "ccn:v1:interview:resume:analysis-id"
    assert keys.event("event-id") == "ccn:v1:interview:event:event-id"
    assert "secret-token" not in keys.token("secret-token")
    assert payload_sha256({"b": 2, "a": 1}) == payload_sha256('{"a":1,"b":2}')
    assert SESSION_RETENTION_SECONDS == CHECKPOINT_RETENTION_SECONDS == 604800
    assert LEASE_SECONDS == 30
    assert LEASE_HEARTBEAT_SECONDS == 10
    assert TOKEN_TTL_SECONDS == 900
    assert RESUME_RETENTION_SECONDS == EVENT_RETENTION_SECONDS == 2592000


@pytest.mark.parametrize(
    ("enabled", "expected_code", "expected_reason"),
    [
        (False, 1008, "INTERVIEW_DISABLED"),
        (True, 1013, "INTERVIEW_RUNTIME_NOT_READY"),
    ],
)
def test_websocket_fails_closed(enabled: bool, expected_code: int, expected_reason: str) -> None:
    app = FastAPI()
    settings_values = {"INTERVIEW_ENABLED": enabled}
    if enabled:
        settings_values.update(
            {
                "INTERVIEW_MESSAGING_ENABLED": True,
                "INTERVIEW_MEDIA_STREAM_ENABLED": True,
                "INTERVIEW_TRANSPORT_SMOKE_MODE": True,
                "INTERVIEW_RUNTIME_TOKEN_SECRET": "test-runtime-secret",
                "TWILIO_ACCOUNT_SID": "AC" + "1" * 32,
                "TWILIO_AUTH_TOKEN": "test-auth-token",
                "TWILIO_MEDIA_STREAM_WSS_URL": "wss://example.test/ws/v1/interviews/twilio/media",
                "CARTESIA_API_KEY": "test-cartesia-key",
                "CARTESIA_ENGLISH_VOICE_ID": "english-voice",
                "CARTESIA_VIETNAMESE_VOICE_ID": "vietnamese-voice",
            }
        )
    app.include_router(interview_router(Settings(_env_file=(), **settings_values)))
    with TestClient(app) as client:
        with client.websocket_connect("/ws/v1/interviews/twilio/media?token=opaque") as websocket:
            with pytest.raises(WebSocketDisconnect) as closed:
                websocket.receive_text()
    assert closed.value.code == expected_code
    assert closed.value.reason == expected_reason


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("enabled", "expected"),
    [
        (False, "FAILED_PRECONDITION:INTERVIEW_DISABLED"),
        (True, "UNAVAILABLE:INTERVIEW_RUNTIME_NOT_READY"),
    ],
)
async def test_prepare_and_cancel_grpc_fail_closed(enabled: bool, expected: str) -> None:
    handler = InterviewGrpcHandler(ConfiguredInterviewRuntime(enabled=enabled))
    prepare = pb.PrepareInterviewSessionRequest(
        session_id="session",
        call_attempt_id="attempt",
        interaction_limits=pb.InterviewInteractionLimits(),
    )
    with pytest.raises(RuntimeError, match=expected):
        await handler.prepare(prepare, Context())  # type: ignore[arg-type]
    with pytest.raises(RuntimeError, match=expected):
        await handler.cancel(
            pb.CancelInterviewSessionRequest(
                session_id="session", call_attempt_id="attempt", reason="cancelled"
            ),
            Context(),  # type: ignore[arg-type]
        )
