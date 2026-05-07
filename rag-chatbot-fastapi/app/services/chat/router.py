"""Chat router with messaging and session endpoints.

Provides REST API endpoints for chat sessions, messaging, and streaming responses.
"""

from fastapi import APIRouter, Depends

from app.core.dependencies import get_current_tenant

router = APIRouter()


@router.post("/")
async def send_message(
    tenant: dict = Depends(get_current_tenant),
):
    """Send a message to the chatbot and receive a response.

    Processes the message through the RAG pipeline and returns the assistant's reply.

    Args:
        tenant: Authenticated tenant context from JWT.

    Returns:
        Stub response with tenant_id (not implemented yet).
    """
    return {
        "message": "not implemented yet",
        "tenant_id": tenant["tenant_id"],
    }


@router.get("/history/{session_id}")
async def get_chat_history(
    session_id: str,
    tenant: dict = Depends(get_current_tenant),
):
    """Get the chat history for a specific session.

    Retrieves all messages in chronological order for the given session.

    Args:
        session_id: The chat session ID to query.
        tenant: Authenticated tenant context from JWT.

    Returns:
        Stub response with tenant_id (not implemented yet).
    """
    return {
        "message": "not implemented yet",
        "tenant_id": tenant["tenant_id"],
        "session_id": session_id,
    }


@router.get("/stream")
async def stream_response(
    tenant: dict = Depends(get_current_tenant),
):
    """Stream chatbot responses via Server-Sent Events (SSE).

    Provides real-time streaming of the assistant's response chunks.

    Args:
        tenant: Authenticated tenant context from JWT.

    Returns:
        Stub response with tenant_id (not implemented yet).
    """
    return {
        "message": "not implemented yet",
        "tenant_id": tenant["tenant_id"],
    }


@router.post("/session")
async def create_session(
    tenant: dict = Depends(get_current_tenant),
):
    """Create a new chat session.

    Initializes a new conversation session for the tenant.

    Args:
        tenant: Authenticated tenant context from JWT.

    Returns:
        Stub response with tenant_id (not implemented yet).
    """
    return {
        "message": "not implemented yet",
        "tenant_id": tenant["tenant_id"],
    }
