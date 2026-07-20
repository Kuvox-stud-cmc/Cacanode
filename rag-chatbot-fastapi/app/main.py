from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import redis.asyncio as redis
from fastapi import FastAPI
from prometheus_client import make_asgi_app

from app.api.v1.health import router as health_router
from app.core.cache import RedisCacheStore, TtlJitter
from app.core.config import settings
from app.core.middleware import RequestIdMiddleware
from app.grpc_service import start_grpc_server
from app.ingestion.embedding import create_embedding_client
from app.rag.revision import SuppliedKnowledgeBaseRevisionStore
from app.rag.runtime import RagRuntime
from app.rag.semantic_answer_cache import SemanticAnswerCache
from app.workers.manager import WorkerManager

logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    redis_client = redis.from_url(
        settings.REDIS_URL,
        decode_responses=False,
        socket_connect_timeout=settings.REDIS_CONNECT_TIMEOUT_SECONDS,
        socket_timeout=settings.REDIS_OPERATION_TIMEOUT_SECONDS,
    )
    cache_store = RedisCacheStore(
        redis_client,
        enabled=settings.CACHE_ENABLED,
        ttl_jitter=TtlJitter(settings.CACHE_TTL_JITTER_PERCENT),
    )
    embedding_client = create_embedding_client(settings, cache_store)
    revision_store = SuppliedKnowledgeBaseRevisionStore()
    semantic_answer_cache: SemanticAnswerCache | None = None
    if settings.CACHE_ENABLED and settings.SEMANTIC_ANSWER_CACHE_MODE in {"shadow", "serve"}:
        semantic_answer_cache = SemanticAnswerCache(
            settings, redis_client=redis_client, revision_store=revision_store
        )
    runtime = RagRuntime.create(
        settings,
        embedder=embedding_client,
        cache_store=cache_store,
        revision_store=revision_store,
        semantic_answer_cache=semantic_answer_cache,
    )
    grpc_server = await start_grpc_server(settings, runtime, redis_client)
    manager: WorkerManager | None = None
    if settings.WORKER_MODE == "embedded":
        manager = WorkerManager(settings, embedder=embedding_client)
        await manager.start()
    app.state.redis_client = redis_client
    app.state.grpc_server = grpc_server
    app.state.worker_manager = manager
    logger.info("Cacanode stateless inference service started")
    try:
        yield
    finally:
        if manager is not None:
            await manager.stop()
        await grpc_server.stop(grace=5)
        if semantic_answer_cache is not None:
            await semantic_answer_cache.close()
        await redis_client.aclose()


def create_app() -> FastAPI:
    application = FastAPI(
        title="Cacanode Internal Inference Service",
        description="Health and metrics for the internal stateless gRPC inference service",
        version="1.0.0",
        lifespan=lifespan,
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    application.add_middleware(RequestIdMiddleware)
    application.include_router(health_router)
    application.mount("/metrics", make_asgi_app())

    @application.get("/health", include_in_schema=False)
    async def legacy_health() -> dict[str, str]:
        return {"status": "healthy", "env": settings.APP_ENV, "version": "1.0.0"}

    return application


app = create_app()
