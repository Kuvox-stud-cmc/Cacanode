from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field
from qdrant_client import AsyncQdrantClient, models

from app.core.config import Settings


class DocumentUnit(BaseModel):
    unit_id: str | None = None
    chunk_index: int
    text: str
    source_name: str | None = None
    modality: str | None = None
    block_type: str | None = None
    section_path: list[str] = Field(default_factory=list)
    heading_context: str | None = None
    page_number: int | None = None
    sheet_name: str | None = None
    cell_range: str | None = None
    table_id: str | None = None
    source_start: int | None = None
    source_end: int | None = None


class QdrantDocumentUnitStore:
    def __init__(self, settings: Settings, client: AsyncQdrantClient | None = None) -> None:
        self._settings = settings
        self._client = client or AsyncQdrantClient(
            url=settings.QDRANT_URL,
            api_key=settings.QDRANT_API_KEY or None,
            check_compatibility=False,
        )

    async def list_units(self, *, tenant_id: str, document_id: str) -> list[DocumentUnit]:
        if not await self._client.collection_exists(self._settings.QDRANT_COLLECTION):
            return []
        records: list[Any] = []
        offset: Any = None
        while True:
            page, next_offset = await self._client.scroll(
                collection_name=self._settings.QDRANT_COLLECTION,
                scroll_filter=models.Filter(
                    must=[
                        models.FieldCondition(
                            key=self._settings.QDRANT_TENANT_FIELD,
                            match=models.MatchValue(value=tenant_id),
                        ),
                        models.FieldCondition(
                            key="document_id", match=models.MatchValue(value=document_id)
                        ),
                    ]
                ),
                limit=256,
                offset=offset,
                with_payload=True,
                with_vectors=False,
            )
            records.extend(page)
            if next_offset is None or next_offset == offset:
                break
            offset = next_offset
        units = [
            self._from_payload(record.payload or {}, tenant_id, document_id) for record in records
        ]
        return sorted(
            (unit for unit in units if unit is not None), key=lambda item: item.chunk_index
        )

    def _from_payload(
        self, payload: dict[str, Any], tenant_id: str, document_id: str
    ) -> DocumentUnit | None:
        if str(payload.get(self._settings.QDRANT_TENANT_FIELD)) != tenant_id:
            return None
        if str(payload.get("document_id")) != document_id:
            return None
        if payload.get("text") is None or payload.get("chunk_index") is None:
            return None
        return DocumentUnit(
            unit_id=str(payload["unit_id"]) if payload.get("unit_id") else None,
            chunk_index=int(payload["chunk_index"]),
            text=str(payload["text"]),
            source_name=str(payload["source_name"]) if payload.get("source_name") else None,
            modality=str(payload["modality"]) if payload.get("modality") else None,
            block_type=str(payload["block_type"]) if payload.get("block_type") else None,
            section_path=[str(item) for item in payload.get("section_path", [])],
            heading_context=(
                str(payload["heading_context"]) if payload.get("heading_context") else None
            ),
            page_number=(
                int(payload["page_number"]) if payload.get("page_number") is not None else None
            ),
            sheet_name=str(payload["sheet_name"]) if payload.get("sheet_name") else None,
            cell_range=str(payload["cell_range"]) if payload.get("cell_range") else None,
            table_id=str(payload["table_id"]) if payload.get("table_id") else None,
            source_start=(
                int(payload["source_start"]) if payload.get("source_start") is not None else None
            ),
            source_end=(
                int(payload["source_end"]) if payload.get("source_end") is not None else None
            ),
        )
