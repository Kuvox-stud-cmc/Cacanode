"""Pydantic models for the GraphRAG Chatbot API.

Exports all models for convenient imports.
"""

from app.models.common import ApiResponse, ErrorResponse, PaginatedResponse
from app.models.document import (
    DocumentStatus,
    DocumentUploadResponse,
    DocumentResponse,
    IngestionStatusResponse,
)
from app.models.chat import (
    MessageRole,
    ChatMessage,
    ChatRequest,
    ChatResponse,
    SessionCreateResponse,
)

__all__ = [
    # Common
    "ApiResponse",
    "ErrorResponse",
    "PaginatedResponse",
    # Document
    "DocumentStatus",
    "DocumentUploadResponse",
    "DocumentResponse",
    "IngestionStatusResponse",
    # Chat
    "MessageRole",
    "ChatMessage",
    "ChatRequest",
    "ChatResponse",
    "SessionCreateResponse",
]
