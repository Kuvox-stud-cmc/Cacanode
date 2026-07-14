from __future__ import annotations

from typing import Annotated, Any

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from qdrant_client import AsyncQdrantClient, models

from app.core.config import settings
from app.core.dependencies import get_current_tenant

router = APIRouter(prefix="/documents", tags=["documents"])


class DocumentUnitResponse(BaseModel):
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
    def __init__(self, client: AsyncQdrantClient | None = None) -> None:
        self._client = client or AsyncQdrantClient(
            url=settings.QDRANT_URL,
            api_key=settings.QDRANT_API_KEY or None,
            check_compatibility=False,
        )
        self._collection = settings.QDRANT_COLLECTION
        self._tenant_field = settings.QDRANT_TENANT_FIELD

    async def list_units(self, *, tenant_id: str, document_id: str) -> list[DocumentUnitResponse]:
        if not await self._client.collection_exists(self._collection):
            return []

        records: list[Any] = []
        offset: Any = None
        while True:
            page, next_offset = await self._client.scroll(
                collection_name=self._collection,
                scroll_filter=models.Filter(
                    must=[
                        models.FieldCondition(
                            key=self._tenant_field,
                            match=models.MatchValue(value=tenant_id),
                        ),
                        models.FieldCondition(
                            key="document_id",
                            match=models.MatchValue(value=document_id),
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

        units: list[DocumentUnitResponse] = []
        for record in records:
            payload = record.payload or {}
            if str(payload.get(self._tenant_field)) != tenant_id:
                continue
            if str(payload.get("document_id")) != document_id:
                continue
            text = payload.get("text")
            chunk_index = payload.get("chunk_index")
            if text is None or chunk_index is None:
                continue
            units.append(
                DocumentUnitResponse(
                    unit_id=str(payload["unit_id"]) if payload.get("unit_id") else None,
                    chunk_index=int(chunk_index),
                    text=str(text),
                    source_name=(
                        str(payload["source_name"]) if payload.get("source_name") else None
                    ),
                    modality=str(payload["modality"]) if payload.get("modality") else None,
                    block_type=(
                        str(payload["block_type"]) if payload.get("block_type") else None
                    ),
                    section_path=[str(item) for item in payload.get("section_path", [])],
                    heading_context=(
                        str(payload["heading_context"])
                        if payload.get("heading_context")
                        else None
                    ),
                    page_number=(
                        int(payload["page_number"])
                        if payload.get("page_number") is not None
                        else None
                    ),
                    sheet_name=(
                        str(payload["sheet_name"]) if payload.get("sheet_name") else None
                    ),
                    cell_range=(
                        str(payload["cell_range"]) if payload.get("cell_range") else None
                    ),
                    table_id=str(payload["table_id"]) if payload.get("table_id") else None,
                    source_start=(
                        int(payload["source_start"])
                        if payload.get("source_start") is not None
                        else None
                    ),
                    source_end=(
                        int(payload["source_end"])
                        if payload.get("source_end") is not None
                        else None
                    ),
                )
            )
        return sorted(units, key=lambda unit: unit.chunk_index)


def get_document_unit_store() -> QdrantDocumentUnitStore:
    return QdrantDocumentUnitStore()


@router.get("/{document_id}/units", response_model=list[DocumentUnitResponse])
async def list_document_units(
    document_id: str,
    tenant: Annotated[dict[str, Any], Depends(get_current_tenant)],
    store: Annotated[QdrantDocumentUnitStore, Depends(get_document_unit_store)],
) -> list[DocumentUnitResponse]:
    try:
        units = await store.list_units(
            tenant_id=str(tenant["tenant_id"]), document_id=document_id
        )
    except Exception as exc:
        raise HTTPException(
            status_code=503, detail="Indexed document content is unavailable"
        ) from exc
    if not units:
        raise HTTPException(status_code=404, detail="Indexed document was not found")
    return units
