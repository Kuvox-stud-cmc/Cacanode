from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from typing import Protocol

from app.modules.ingestion.api.errors import (
    IngestionError,
    PermanentIngestionFailure,
    TransientIngestionFailure,
)


@dataclass(frozen=True, slots=True)
class IngestDocumentCommand:
    schema_version: str
    event_id: str
    job_id: str
    tenant_id: str
    knowledge_base_id: str
    document_id: str
    uploader_id: str
    storage_key: str
    file_name: str
    content_type: str
    file_size_bytes: int
    occurred_at: datetime


class IngestionOutcomeStatus(StrEnum):
    COMPLETED = "COMPLETED"
    DUPLICATE = "DUPLICATE"


@dataclass(frozen=True, slots=True)
class IngestionOutcome:
    status: IngestionOutcomeStatus
    chunk_count: int


class IngestionApi(Protocol):
    async def process(self, command: IngestDocumentCommand) -> IngestionOutcome: ...


class DocumentIndexLifecycleApi(Protocol):
    async def delete_document(
        self, tenant_id: str, knowledge_base_id: str, document_id: str
    ) -> None: ...


class IngestionCheckpointMaintenanceApi(Protocol):
    async def republish_incomplete(self, *, limit: int) -> int: ...


__all__ = [
    "DocumentIndexLifecycleApi",
    "IngestDocumentCommand",
    "IngestionApi",
    "IngestionCheckpointMaintenanceApi",
    "IngestionError",
    "IngestionOutcome",
    "IngestionOutcomeStatus",
    "PermanentIngestionFailure",
    "TransientIngestionFailure",
]
