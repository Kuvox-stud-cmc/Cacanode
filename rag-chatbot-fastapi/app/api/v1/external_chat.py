from typing import Any
from uuid import uuid4

from fastapi import APIRouter, Depends, status
from pydantic import BaseModel, Field

from app.api.v1.chat import (
    AssistantMessageResponse,
    ChatMessageResponse,
    ChatSessionResponse,
    SubmitMessageRequest,
    get_chat_service,
)
from app.core.dependencies import get_api_principal, get_widget_principal
from app.core.errors import ApiError
from app.rag.chat_service import RagChatService
from app.rag.errors import (
    ChatModelProviderError,
    ChatModelTimeoutError,
    ChatQuotaExceededError,
    ChatSessionNotFoundError,
    ChatSessionStoreUnavailableError,
    ChatWorkspaceNotFoundError,
)

widget_router = APIRouter(prefix="/widget/chat", tags=["widget-chat"])
external_router = APIRouter(prefix="/external/chat", tags=["external-chat"])
widget_principal_dependency = Depends(get_widget_principal)
api_principal_dependency = Depends(get_api_principal)
chat_service_dependency = Depends(get_chat_service)


class ExternalCreateSessionRequest(BaseModel):
    external_user_id: str | None = Field(default=None, max_length=255)
    customer_name: str | None = Field(default=None, max_length=255)
    customer_email: str | None = Field(default=None, max_length=320)
    locale: str = Field(default="vi-VN", max_length=20)
    metadata: dict[str, Any] = Field(default_factory=dict)


async def _create_session(
    request: ExternalCreateSessionRequest,
    principal: dict[str, Any],
    chat_service: RagChatService,
    channel: str,
) -> ChatSessionResponse:
    try:
        session = chat_service.create_session(
            tenant_id=principal["tenant_id"],
            user_id=None,
            chatbot_id=principal["chatbot_id"],
            knowledge_base_id=principal["knowledge_base_id"],
            locale=request.locale,
            channel=channel,
            external_user_id=request.external_user_id or f"visitor_{uuid4().hex[:12]}",
            customer_name=request.customer_name,
            customer_email=str(request.customer_email) if request.customer_email else None,
            customer_metadata=request.metadata,
            integration_token_id=principal["token_id"],
        )
        return ChatSessionResponse.from_session(session)
    except ChatWorkspaceNotFoundError as exc:
        raise ApiError(404, "WORKSPACE_NOT_FOUND", "Chat workspace was not found.") from exc
    except ChatSessionStoreUnavailableError as exc:
        raise ApiError(
            503, "CHAT_SESSION_STORE_UNAVAILABLE", "Chat storage is unavailable."
        ) from exc


@widget_router.post("/sessions", response_model=ChatSessionResponse)
async def create_widget_session(
    request: ExternalCreateSessionRequest,
    principal: dict[str, Any] = widget_principal_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> ChatSessionResponse:
    return await _create_session(request, principal, chat_service, "WIDGET")


@external_router.post("/sessions", response_model=ChatSessionResponse)
async def create_api_session(
    request: ExternalCreateSessionRequest,
    principal: dict[str, Any] = api_principal_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> ChatSessionResponse:
    return await _create_session(request, principal, chat_service, "CUSTOM_API")


async def _submit(
    session_id: str,
    request: SubmitMessageRequest,
    principal: dict[str, Any],
    chat_service: RagChatService,
) -> AssistantMessageResponse:
    try:
        message = await chat_service.submit_message(
            tenant_id=principal["tenant_id"],
            session_id=session_id,
            content=request.content,
            integration_token_id=principal["token_id"],
        )
        return AssistantMessageResponse.from_message(message)
    except ChatSessionNotFoundError as exc:
        raise ApiError(404, "SESSION_NOT_FOUND", "Chat session was not found.") from exc
    except ChatQuotaExceededError as exc:
        raise ApiError(
            429, "MESSAGE_QUOTA_EXCEEDED", "The tenant message quota has been reached."
        ) from exc
    except ChatModelTimeoutError as exc:
        raise ApiError(504, "MODEL_TIMEOUT", "The model took too long to answer.") from exc
    except ChatModelProviderError as exc:
        raise ApiError(502, "MODEL_PROVIDER_ERROR", "The model provider could not answer.") from exc


@widget_router.post("/sessions/{session_id}/messages", response_model=AssistantMessageResponse)
async def submit_widget_message(
    session_id: str,
    request: SubmitMessageRequest,
    principal: dict[str, Any] = widget_principal_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> AssistantMessageResponse:
    return await _submit(session_id, request, principal, chat_service)


@external_router.post("/sessions/{session_id}/messages", response_model=AssistantMessageResponse)
async def submit_api_message(
    session_id: str,
    request: SubmitMessageRequest,
    principal: dict[str, Any] = api_principal_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> AssistantMessageResponse:
    return await _submit(session_id, request, principal, chat_service)


async def _history(
    session_id: str,
    principal: dict[str, Any],
    chat_service: RagChatService,
) -> list[ChatMessageResponse]:
    try:
        messages = chat_service.list_messages(
            tenant_id=principal["tenant_id"],
            session_id=session_id,
            integration_token_id=principal["token_id"],
        )
        return [ChatMessageResponse.from_message(message) for message in messages]
    except ChatSessionNotFoundError as exc:
        raise ApiError(404, "SESSION_NOT_FOUND", "Chat session was not found.") from exc


@widget_router.get("/sessions/{session_id}/messages", response_model=list[ChatMessageResponse])
async def widget_history(
    session_id: str,
    principal: dict[str, Any] = widget_principal_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> list[ChatMessageResponse]:
    return await _history(session_id, principal, chat_service)


@external_router.get("/sessions/{session_id}/messages", response_model=list[ChatMessageResponse])
async def api_history(
    session_id: str,
    principal: dict[str, Any] = api_principal_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> list[ChatMessageResponse]:
    return await _history(session_id, principal, chat_service)


async def _close(
    session_id: str,
    principal: dict[str, Any],
    chat_service: RagChatService,
) -> None:
    try:
        chat_service.close_session(
            tenant_id=principal["tenant_id"],
            session_id=session_id,
            integration_token_id=principal["token_id"],
        )
    except ChatSessionNotFoundError as exc:
        raise ApiError(404, "SESSION_NOT_FOUND", "Chat session was not found.") from exc


@widget_router.delete("/sessions/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def close_widget_session(
    session_id: str,
    principal: dict[str, Any] = widget_principal_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> None:
    await _close(session_id, principal, chat_service)


@external_router.delete("/sessions/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def close_api_session(
    session_id: str,
    principal: dict[str, Any] = api_principal_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> None:
    await _close(session_id, principal, chat_service)
