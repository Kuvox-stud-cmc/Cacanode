from typing import Any

from fastapi import APIRouter, Header, status
from pydantic import BaseModel, Field

from app.core.errors import ApiError, ErrorEnvelope

router = APIRouter(prefix="/chat", tags=["chat"])


class CreateSessionRequest(BaseModel):
    chatbot_id: str
    external_user_id: str | None = None
    locale: str = "vi-VN"
    metadata: dict[str, Any] = Field(default_factory=dict)


class SubmitMessageRequest(BaseModel):
    content: str = Field(min_length=1, max_length=32000)
    metadata: dict[str, Any] = Field(default_factory=dict)


def not_implemented(capability: str) -> None:
    raise ApiError(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        code="NOT_IMPLEMENTED",
        message=f"{capability} is scaffolded but not implemented.",
    )


@router.post("/sessions", responses={501: {"model": ErrorEnvelope}})
async def create_session(
    request: CreateSessionRequest,
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
) -> None:
    del request, idempotency_key
    not_implemented("Chat session creation")


@router.post("/sessions/{session_id}/messages", responses={501: {"model": ErrorEnvelope}})
async def submit_message(
    session_id: str,
    request: SubmitMessageRequest,
    accept: str = Header(default="application/json"),
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
) -> None:
    del session_id, request, accept, idempotency_key
    not_implemented("Chat message generation and SSE streaming")


@router.get("/sessions/{session_id}/messages", responses={501: {"model": ErrorEnvelope}})
async def history(session_id: str, limit: int = 50, after: str | None = None) -> None:
    del session_id, limit, after
    not_implemented("Chat history")


@router.delete("/sessions/{session_id}", responses={501: {"model": ErrorEnvelope}})
async def delete_session(session_id: str) -> None:
    del session_id
    not_implemented("Chat session deletion")
