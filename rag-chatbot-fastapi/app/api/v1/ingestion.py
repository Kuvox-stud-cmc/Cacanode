from fastapi import APIRouter, status

from app.core.errors import ApiError, ErrorEnvelope

router = APIRouter(prefix="/ingestion", tags=["ingestion"])


@router.get("/jobs/{job_id}", responses={501: {"model": ErrorEnvelope}})
async def ingestion_status(job_id: str) -> None:
    del job_id
    raise ApiError(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        code="NOT_IMPLEMENTED",
        message="Ingestion orchestration is scaffolded but not implemented.",
    )
