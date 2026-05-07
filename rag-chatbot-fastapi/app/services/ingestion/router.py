"""Document ingestion router with upload and status endpoints.

Provides REST API endpoints for document upload, status polling, and management.
"""

from fastapi import APIRouter, Depends, UploadFile, File

from app.core.dependencies import get_current_tenant

router = APIRouter()


@router.post("/upload")
async def upload_document(
    file: UploadFile = File(...),
    tenant: dict = Depends(get_current_tenant),
):
    """Upload a document for ingestion processing.

    Queues the document for processing and returns a job ID for status polling.

    Args:
        file: Uploaded file to process.
        tenant: Authenticated tenant context from JWT.

    Returns:
        Stub response with tenant_id (not implemented yet).
    """
    return {
        "message": "not implemented yet",
        "tenant_id": tenant["tenant_id"],
        "filename": file.filename,
    }


@router.get("/status/{job_id}")
async def get_ingestion_status(
    job_id: str,
    tenant: dict = Depends(get_current_tenant),
):
    """Get the processing status of an ingestion job.

    Poll this endpoint to track document processing progress.

    Args:
        job_id: The ingestion job ID to query.
        tenant: Authenticated tenant context from JWT.

    Returns:
        Stub response with tenant_id (not implemented yet).
    """
    return {
        "message": "not implemented yet",
        "tenant_id": tenant["tenant_id"],
        "job_id": job_id,
    }


@router.get("/documents")
async def list_documents(
    tenant: dict = Depends(get_current_tenant),
):
    """List all documents for the current tenant.

    Returns a paginated list of documents with their processing status.

    Args:
        tenant: Authenticated tenant context from JWT.

    Returns:
        Stub response with tenant_id (not implemented yet).
    """
    return {
        "message": "not implemented yet",
        "tenant_id": tenant["tenant_id"],
    }


@router.delete("/doc/{doc_id}")
async def delete_document(
    doc_id: str,
    tenant: dict = Depends(get_current_tenant),
):
    """Delete a document and its associated data.

    Removes the document from storage and vector/graph databases.

    Args:
        doc_id: The document ID to delete.
        tenant: Authenticated tenant context from JWT.

    Returns:
        Stub response with tenant_id (not implemented yet).
    """
    return {
        "message": "not implemented yet",
        "tenant_id": tenant["tenant_id"],
        "doc_id": doc_id,
    }
