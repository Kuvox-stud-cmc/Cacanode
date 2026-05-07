"""Document and ingestion models for the RAG system.

Defines Pydantic models for document uploads, status tracking, and responses.
"""

from datetime import datetime, timezone
from enum import Enum
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


def utc_now() -> datetime:
    """Return current UTC datetime."""
    return datetime.now(timezone.utc)


class DocumentStatus(str, Enum):
    """Document processing status enumeration.

    Tracks the lifecycle of a document through the ingestion pipeline.
    """

    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class DocumentUploadResponse(BaseModel):
    """Response model for document upload requests.

    Returned when a document is successfully queued for processing.
    """

    model_config = ConfigDict(from_attributes=True)

    job_id: str
    document_id: str
    status: DocumentStatus = DocumentStatus.PENDING
    file_name: str
    file_size: int


class DocumentResponse(BaseModel):
    """Complete document information response.

    Contains all metadata about a processed or processing document.
    """

    model_config = ConfigDict(from_attributes=True)

    id: str
    tenant_id: str
    file_name: str
    file_type: str
    file_size_bytes: int
    status: DocumentStatus
    chunk_count: Optional[int] = None
    error_message: Optional[str] = None
    created_at: datetime = Field(default_factory=utc_now)
    updated_at: datetime = Field(default_factory=utc_now)


class IngestionStatusResponse(BaseModel):
    """Ingestion job status response for polling.

    Provides current status and progress information for an ingestion job.
    """

    model_config = ConfigDict(from_attributes=True)

    job_id: str
    document_id: str
    status: DocumentStatus
    progress_message: Optional[str] = None
