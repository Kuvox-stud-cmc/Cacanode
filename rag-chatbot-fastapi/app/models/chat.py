"""Chat and message models for the RAG chatbot.

Defines Pydantic models for chat sessions, messages, and streaming responses.
"""

from datetime import datetime, timezone
from enum import Enum
from typing import Optional, Dict, Any

from pydantic import BaseModel, ConfigDict, Field


def utc_now() -> datetime:
    """Return current UTC datetime."""
    return datetime.now(timezone.utc)


class MessageRole(str, Enum):
    """Message role enumeration for chat participants.

    Defines the possible roles in a conversation.
    """

    USER = "user"
    ASSISTANT = "assistant"
    SYSTEM = "system"


class ChatMessage(BaseModel):
    """Individual chat message model.

    Represents a single message in a chat conversation.
    """

    model_config = ConfigDict(from_attributes=True)

    id: str
    role: MessageRole
    content: str
    timestamp: datetime = Field(default_factory=utc_now)
    is_streaming: bool = False


class ChatRequest(BaseModel):
    """Chat request model for sending messages.

    Contains the message content and session context.
    """

    model_config = ConfigDict(from_attributes=True)

    session_id: Optional[str] = None
    message: str
    stream: bool = True


class ChatResponse(BaseModel):
    """Chat response model for assistant replies.

    Contains the assistant's message and usage statistics.
    """

    model_config = ConfigDict(from_attributes=True)

    session_id: str
    message: ChatMessage
    usage: Optional[Dict[str, Any]] = None


class SessionCreateResponse(BaseModel):
    """Response model for new chat session creation.

    Returned when a new chat session is successfully created.
    """

    model_config = ConfigDict(from_attributes=True)

    session_id: str
    created_at: datetime = Field(default_factory=utc_now)
