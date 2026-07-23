from fastapi import APIRouter, Request, status
from fastapi.responses import JSONResponse

from app.bootstrap.settings import settings
from app.bootstrap.workers import WorkerManager

router = APIRouter(tags=["health"])


@router.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "live", "environment": settings.APP_ENV}


@router.get("/health/ready")
async def ready(request: Request) -> JSONResponse:
    manager: WorkerManager | None = getattr(request.app.state, "worker_manager", None)
    workers = {
        kind: {"running": state.running, "capability": state.capability}
        for kind, state in (manager.states.items() if manager else [])
    }
    model_ready = settings.model_configured
    is_ready = model_ready or not settings.READINESS_REQUIRE_MODELS
    payload = {
        "status": "ready" if is_ready else "not_ready",
        "components": {
            "model": "configured" if model_ready else "not_configured",
            "workers": workers,
        },
    }
    return JSONResponse(
        status_code=status.HTTP_200_OK if is_ready else status.HTTP_503_SERVICE_UNAVAILABLE,
        content=payload,
    )
