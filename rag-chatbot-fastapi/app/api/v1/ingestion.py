from __future__ import annotations

import logging

from fastapi import APIRouter, Header, HTTPException, status

from app.core.config import settings
from app.core.errors import ApiError, ErrorEnvelope
from app.graph import GraphServiceClient
from app.ingestion.storage import SeaweedS3DocumentStore
from app.ingestion.vector_store import QdrantChunkStore

router = APIRouter(prefix="/ingestion", tags=["ingestion"])
logger = logging.getLogger(__name__)


@router.get("/jobs/{job_id}", responses={501: {"model": ErrorEnvelope}})
async def ingestion_status(job_id: str) -> None:
    del job_id
    raise ApiError(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        code="NOT_IMPLEMENTED",
        message="Ingestion orchestration is scaffolded but not implemented.",
    )


@router.delete("/internal/sources/{document_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_source(
    document_id: str,
    tenant_id: str,
    knowledge_base_id: str,
    x_ingestion_token: str = Header(default=""),
) -> None:
    if (
        not settings.INGESTION_INTERNAL_TOKEN
        or x_ingestion_token != settings.INGESTION_INTERNAL_TOKEN
    ):
        raise HTTPException(status_code=401, detail="Invalid ingestion service credentials")

    try:
        await QdrantChunkStore(settings).delete_source_ids(tenant_id, document_id)
        logger.info("source_cleanup_qdrant_complete document_id=%s", document_id)
        await GraphServiceClient(settings).delete_source(tenant_id, document_id)
        logger.info("source_cleanup_graph_complete document_id=%s", document_id)
        prefix = (
            f"tenants/{tenant_id}/knowledge-bases/{knowledge_base_id}/"
            f"documents/{document_id}/"
        )
        await SeaweedS3DocumentStore(settings).delete_prefix(prefix)
        logger.info("source_cleanup_storage_complete document_id=%s", document_id)
    except Exception:
        logger.exception("source_cleanup_failed document_id=%s", document_id)
        raise
