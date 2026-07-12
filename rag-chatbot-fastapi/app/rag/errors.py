class ChatError(Exception):
    """Base chat/RAG error."""


class ChatSessionNotFoundError(ChatError):
    """Session does not exist or does not belong to the tenant."""


class ChatModelTimeoutError(ChatError):
    """Model generation exceeded the configured timeout."""
