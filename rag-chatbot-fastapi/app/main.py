import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import make_asgi_app

from app.api.v1.chat import router as chat_router
from app.api.v1.health import router as health_router
from app.api.v1.ingestion import router as ingestion_router
from app.core.config import settings
from app.core.errors import ApiError, api_error_handler
from app.core.middleware import RequestIdMiddleware
from app.workers.manager import WorkerManager

logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    manager: WorkerManager | None = None
    if settings.WORKER_MODE == "embedded":
        manager = WorkerManager(settings)
        await manager.start()
    app.state.worker_manager = manager
    logger.info("Cacanode AI API started with worker mode %s", settings.WORKER_MODE)
    try:
        yield
    finally:
        if manager:
            await manager.stop()


def create_app() -> FastAPI:
    application = FastAPI(
        title="Cacanode AI API",
        description="Tenant-scoped chat, retrieval, graph, and ingestion service",
        version="1.0.0-scaffold",
        lifespan=lifespan,
    )
    application.add_exception_handler(ApiError, api_error_handler)
    application.add_middleware(RequestIdMiddleware)
    application.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    application.include_router(health_router)
    application.include_router(chat_router, prefix="/api/v1")
    application.include_router(ingestion_router, prefix="/api/v1")

    # Temporary compatibility routes for the original prototype.
    application.include_router(chat_router)
    application.include_router(ingestion_router)
    application.mount("/metrics", make_asgi_app())

    @application.get("/health", include_in_schema=False)
    async def legacy_health() -> dict[str, str]:
        return {"status": "healthy", "env": settings.APP_ENV, "version": "1.0.0-scaffold"}

    return application


app = create_app()
