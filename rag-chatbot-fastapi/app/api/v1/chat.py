from typing import Any

from fastapi import APIRouter, Depends, Header, status
from pydantic import BaseModel, Field

from app.core.config import settings
from app.core.dependencies import get_current_tenant
from app.core.errors import ApiError, ErrorEnvelope
from app.infrastructure.model_gateway import create_chat_model
from app.ingestion.embedding import OllamaEmbeddingClient
from app.rag.chat_service import RagChatService
from app.rag.errors import (
    ChatModelProviderError,
    ChatModelTimeoutError,
    ChatQuotaExceededError,
    ChatSessionNotFoundError,
    ChatSessionStoreUnavailableError,
    ChatWorkspaceNotFoundError,
)
from app.rag.models import AssistantMessage, ChatMessage, ChatSession, Citation
from app.rag.retrieval import QdrantVectorRetriever
from app.rag.sessions import PostgresChatSessionStore

router = APIRouter(prefix="/chat", tags=["chat"])
_chat_service: RagChatService | None = None


class CreateSessionRequest(BaseModel):
    chatbot_id: str
    knowledge_base_id: str
    external_user_id: str | None = None
    locale: str = "vi-VN"
    metadata: dict[str, Any] = Field(default_factory=dict)


class SubmitMessageRequest(BaseModel):
    content: str = Field(min_length=1, max_length=32000)
    metadata: dict[str, Any] = Field(default_factory=dict)


class ChatSessionResponse(BaseModel):
    id: str
    chatbot_id: str
    knowledge_base_id: str
    tenant_id: str
    locale: str

    @classmethod
    def from_session(cls, session: ChatSession) -> "ChatSessionResponse":
        return cls(
            id=session.id,
            chatbot_id=session.chatbot_id,
            knowledge_base_id=session.knowledge_base_id,
            tenant_id=session.tenant_id,
            locale=session.locale,
        )


class CitationResponse(BaseModel):
    id: str
    document_id: str
    source_name: str
    page_number: int | None
    chunk_index: int
    score: float
    snippet: str

    @classmethod
    def from_citation(cls, citation: Citation) -> "CitationResponse":
        return cls(
            id=citation.id,
            document_id=citation.document_id,
            source_name=citation.source_name,
            page_number=citation.page_number,
            chunk_index=citation.chunk_index,
            score=citation.score,
            snippet=citation.snippet,
        )


class AssistantMessageResponse(BaseModel):
    role: str
    content: str
    citations: list[CitationResponse] = Field(default_factory=list)
    action: dict[str, Any] | None = None

    @classmethod
    def from_message(cls, message: AssistantMessage) -> "AssistantMessageResponse":
        return cls(
            role=message.role,
            content=message.content,
            citations=[CitationResponse.from_citation(item) for item in message.citations],
            action=message.action,
        )


class ChatMessageResponse(BaseModel):
    role: str
    content: str
    citations: list[CitationResponse] = Field(default_factory=list)
    sequence_number: int | None = None
    action: dict[str, Any] | None = None

    @classmethod
    def from_message(cls, message: ChatMessage) -> "ChatMessageResponse":
        return cls(
            role=message.role,
            content=message.content,
            citations=[CitationResponse.from_citation(item) for item in message.citations],
            sequence_number=message.sequence_number,
            action=message.action,
        )


class ConversationListItemResponse(BaseModel):
    id: str
    channel: str
    external_user_id: str | None
    customer_name: str | None
    customer_email: str | None
    status: str
    message_count: int
    created_at: Any
    updated_at: Any
    closed_at: Any | None


class ConversationDetailResponse(BaseModel):
    id: str
    channel: str
    external_user_id: str | None
    customer_name: str | None
    customer_email: str | None
    customer_metadata: dict[str, Any]
    status: str
    created_at: Any
    updated_at: Any
    closed_at: Any | None
    messages: list[ChatMessageResponse]


def get_chat_service() -> RagChatService:
    global _chat_service
    if _chat_service is None:
        _chat_service = RagChatService(
            settings=settings,
            sessions=PostgresChatSessionStore(settings.POSTGRES_URL),
            embedder=OllamaEmbeddingClient(settings),
            retriever=QdrantVectorRetriever(settings),
            chat_model=create_chat_model(settings),
        )
    return _chat_service


current_tenant_dependency = Depends(get_current_tenant)
chat_service_dependency = Depends(get_chat_service)


@router.post(
    "/sessions",
    response_model=ChatSessionResponse,
    responses={401: {"model": ErrorEnvelope}},
)
async def create_session(
    request: CreateSessionRequest,
    tenant: dict[str, Any] = current_tenant_dependency,
    chat_service: RagChatService = chat_service_dependency,
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
) -> ChatSessionResponse:
    del idempotency_key
    try:
        session = chat_service.create_session(
            tenant_id=str(tenant["tenant_id"]),
            user_id=str(tenant["user_id"]),
            chatbot_id=request.chatbot_id,
            knowledge_base_id=request.knowledge_base_id,
            locale=request.locale,
        )
    except ChatWorkspaceNotFoundError as exc:
        raise ApiError(
            status_code=status.HTTP_404_NOT_FOUND,
            code="WORKSPACE_NOT_FOUND",
            message="Chat workspace was not found.",
        ) from exc
    except ChatSessionStoreUnavailableError as exc:
        raise ApiError(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            code="CHAT_SESSION_STORE_UNAVAILABLE",
            message="Chat session storage is unavailable.",
        ) from exc
    return ChatSessionResponse.from_session(session)


@router.post(
    "/sessions/{session_id}/messages",
    response_model=AssistantMessageResponse,
    responses={
        401: {"model": ErrorEnvelope},
        404: {"model": ErrorEnvelope},
        502: {"model": ErrorEnvelope},
        504: {"model": ErrorEnvelope},
    },
)
async def submit_message(
    session_id: str,
    request: SubmitMessageRequest,
    tenant: dict[str, Any] = current_tenant_dependency,
    chat_service: RagChatService = chat_service_dependency,
    accept: str = Header(default="application/json"),
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
) -> AssistantMessageResponse:
    del accept, idempotency_key
    try:
        message = await chat_service.submit_message(
            tenant_id=str(tenant["tenant_id"]),
            session_id=session_id,
            content=request.content,
        )
    except ChatSessionNotFoundError as exc:
        raise ApiError(
            status_code=status.HTTP_404_NOT_FOUND,
            code="SESSION_NOT_FOUND",
            message="Chat session was not found.",
        ) from exc
    except ChatSessionStoreUnavailableError as exc:
        raise ApiError(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            code="CHAT_SESSION_STORE_UNAVAILABLE",
            message="Chat session storage is unavailable.",
        ) from exc
    except ChatModelTimeoutError as exc:
        raise ApiError(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            code="MODEL_TIMEOUT",
            message="The model took too long to answer. Try a shorter question or retry.",
        ) from exc
    except ChatModelProviderError as exc:
        raise ApiError(
            status_code=status.HTTP_502_BAD_GATEWAY,
            code="MODEL_PROVIDER_ERROR",
            message="The model provider could not complete the request.",
        ) from exc
    except ChatQuotaExceededError as exc:
        raise ApiError(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            code="MESSAGE_QUOTA_EXCEEDED",
            message="The tenant message quota has been reached.",
        ) from exc
    return AssistantMessageResponse.from_message(message)


@router.get(
    "/sessions/{session_id}/messages",
    response_model=list[ChatMessageResponse],
    responses={401: {"model": ErrorEnvelope}, 404: {"model": ErrorEnvelope}},
)
async def history(
    session_id: str,
    limit: int = 50,
    after: int | None = None,
    tenant: dict[str, Any] = current_tenant_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> list[ChatMessageResponse]:
    try:
        messages = chat_service.list_messages(
            tenant_id=str(tenant["tenant_id"]),
            session_id=session_id,
            limit=limit,
            after=after,
        )
    except ChatSessionNotFoundError as exc:
        raise ApiError(
            status_code=status.HTTP_404_NOT_FOUND,
            code="SESSION_NOT_FOUND",
            message="Chat session was not found.",
        ) from exc
    except ChatSessionStoreUnavailableError as exc:
        raise ApiError(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            code="CHAT_SESSION_STORE_UNAVAILABLE",
            message="Chat session storage is unavailable.",
        ) from exc
    return [ChatMessageResponse.from_message(message) for message in messages]


@router.delete(
    "/sessions/{session_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    responses={401: {"model": ErrorEnvelope}, 404: {"model": ErrorEnvelope}},
)
async def delete_session(
    session_id: str,
    tenant: dict[str, Any] = current_tenant_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> None:
    try:
        chat_service.close_session(tenant_id=str(tenant["tenant_id"]), session_id=session_id)
    except ChatSessionNotFoundError as exc:
        raise ApiError(
            status_code=status.HTTP_404_NOT_FOUND,
            code="SESSION_NOT_FOUND",
            message="Chat session was not found.",
        ) from exc
    except ChatSessionStoreUnavailableError as exc:
        raise ApiError(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            code="CHAT_SESSION_STORE_UNAVAILABLE",
            message="Chat session storage is unavailable.",
        ) from exc


@router.get("/conversations", response_model=list[ConversationListItemResponse])
async def list_conversations(
    conversation_status: str | None = None,
    limit: int = 50,
    offset: int = 0,
    tenant: dict[str, Any] = current_tenant_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> list[ConversationListItemResponse]:
    rows = chat_service.list_external_conversations(
        tenant_id=str(tenant["tenant_id"]),
        status=conversation_status,
        limit=limit,
        offset=offset,
    )
    return [ConversationListItemResponse(**row) for row in rows]


@router.get("/conversations/{session_id}", response_model=ConversationDetailResponse)
async def get_conversation(
    session_id: str,
    tenant: dict[str, Any] = current_tenant_dependency,
    chat_service: RagChatService = chat_service_dependency,
) -> ConversationDetailResponse:
    result = chat_service.get_external_conversation(
        tenant_id=str(tenant["tenant_id"]), session_id=session_id
    )
    if result is None:
        raise ApiError(404, "CONVERSATION_NOT_FOUND", "Conversation was not found.")
    row, messages = result
    return ConversationDetailResponse(
        **row,
        messages=[ChatMessageResponse.from_message(message) for message in messages],
    )
