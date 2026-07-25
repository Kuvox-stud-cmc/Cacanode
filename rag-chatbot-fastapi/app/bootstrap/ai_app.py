from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import redis.asyncio as redis
from fastapi import FastAPI
from prometheus_client import make_asgi_app

from app.bootstrap.configuration import model_config, storage_config
from app.bootstrap.generation import RagRuntime
from app.bootstrap.grpc import start_grpc_server
from app.bootstrap.health import router as health_router
from app.bootstrap.settings import settings
from app.bootstrap.workers import WorkerManager
from app.common.cache import RedisCacheStore, TtlJitter
from app.common.middleware import RequestIdMiddleware
from app.common.storage import SeaweedS3DocumentStore
from app.modules.ingestion.internal.content_extraction import DigitalContentExtractionAdapter
from app.modules.interview.internal.media import InterviewMediaRuntime
from app.modules.interview.internal.recovery import InterviewRecoveryWorker
from app.modules.interview.internal.redis_state import InterviewRedisState
from app.modules.interview.internal.resume_analysis import ResumeAnalysisProcessor
from app.modules.interview.transport.http import interview_router
from app.modules.interview.transport.rabbitmq import (
    ConfirmedInterviewPublisher,
    ResumeAnalysisWorker,
    connect_and_declare_interview_topology,
)
from app.modules.model.internal.cartesia_speech import (
    CartesiaStreamingSpeechToTextSession,
    CartesiaStreamingTextToSpeech,
    SdkCartesiaSpeechSocketFactory,
)
from app.modules.model.internal.chat import create_chat_model
from app.modules.model.internal.embedding import create_embedding_client

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
    embedding_client = create_embedding_client(model_config(settings), cache_store)
    runtime = RagRuntime.create(
        settings,
        embedder=embedding_client,
        cache_store=cache_store,
        redis_client=redis_client,
    )
    grpc_server = await start_grpc_server(settings, runtime, redis_client)
    rabbit_connection = None
    resume_worker: ResumeAnalysisWorker | None = None
    interview_recovery_worker: InterviewRecoveryWorker | None = None
    if settings.INTERVIEW_ENABLED and settings.INTERVIEW_MESSAGING_ENABLED:
        rabbit_connection = await connect_and_declare_interview_topology(settings.RABBITMQ_URL)
        if settings.INTERVIEW_CV_ANALYSIS_ENABLED:
            state = InterviewRedisState(redis_client, prefix=settings.CACHE_KEY_PREFIX)
            analysis_model_settings = settings.model_copy(
                update={"LLM_MAX_OUTPUT_TOKENS": max(settings.LLM_MAX_OUTPUT_TOKENS, 4096)}
            )
            resume_worker = ResumeAnalysisWorker(
                connection=rabbit_connection,
                state=state,
                store=SeaweedS3DocumentStore(storage_config(settings)),
                processor=ResumeAnalysisProcessor(
                    extractor=DigitalContentExtractionAdapter(
                        max_characters=settings.CV_ANALYSIS_MAX_EXTRACTED_CHARACTERS,
                        max_segments=settings.CV_ANALYSIS_MAX_EVIDENCE_SEGMENTS,
                    ),
                    model=create_chat_model(model_config(analysis_model_settings)),
                    max_evidence_segments=settings.CV_ANALYSIS_MAX_EVIDENCE_SEGMENTS,
                    max_personalized_questions=settings.CV_ANALYSIS_MAX_PERSONALIZED_QUESTIONS,
                ),
                policy_version=settings.CV_ANALYSIS_POLICY_VERSION,
                model_version=settings.CV_ANALYSIS_MODEL_VERSION,
                max_attempts=settings.CV_ANALYSIS_MAX_PROCESSING_ATTEMPTS,
                pending_outcome_ttl_seconds=settings.CV_ANALYSIS_PENDING_OUTCOME_TTL_SECONDS,
            )
            await resume_worker.start()
    manager: WorkerManager | None = None
    if settings.WORKER_MODE == "embedded":
        manager = WorkerManager(settings, embedder=embedding_client, redis_client=redis_client)
        await manager.start()
    app.state.redis_client = redis_client
    app.state.grpc_server = grpc_server
    app.state.worker_manager = manager
    app.state.interview_rabbit_connection = rabbit_connection
    app.state.resume_analysis_worker = resume_worker
    app.state.interview_media_runtime = None
    if settings.INTERVIEW_ENABLED and settings.INTERVIEW_MEDIA_STREAM_ENABLED:
        speech_factory = SdkCartesiaSpeechSocketFactory(settings.CARTESIA_API_KEY)
        interview_state = InterviewRedisState(
            redis_client, prefix=settings.CACHE_KEY_PREFIX
        )
        interview_model = None
        if settings.INTERVIEW_ENGINE_ENABLED:
            interview_model_settings = settings.model_copy(
                update={
                    "LLM_TEMPERATURE": 0,
                    "LLM_TIMEOUT_SECONDS": settings.INTERVIEW_MODEL_TIMEOUT_SECONDS,
                    "LLM_MAX_OUTPUT_TOKENS": settings.INTERVIEW_MODEL_MAX_OUTPUT_TOKENS,
                }
            )
            interview_model = create_chat_model(
                model_config(interview_model_settings), enforce_reasoning_minimum=False
            )
        app.state.interview_media_runtime = InterviewMediaRuntime(
            settings=settings,
            state=interview_state,
            tts=CartesiaStreamingTextToSpeech(
                speech_factory,
                english_voice_id=settings.CARTESIA_ENGLISH_VOICE_ID,
                vietnamese_voice_id=settings.CARTESIA_VIETNAMESE_VOICE_ID,
            ),
            stt_factory=lambda: CartesiaStreamingSpeechToTextSession(speech_factory),
            model=interview_model,
            publisher=(
                ConfirmedInterviewPublisher(rabbit_connection, interview_state)
                if settings.INTERVIEW_DURABLE_RESULTS_ENABLED
                and rabbit_connection is not None
                else None
            ),
        )
        if settings.INTERVIEW_DURABLE_RESULTS_ENABLED and rabbit_connection is not None:
            interview_recovery_worker = InterviewRecoveryWorker(
                settings=settings,
                state=interview_state,
                publisher=ConfirmedInterviewPublisher(rabbit_connection, interview_state),
            )
            await interview_recovery_worker.start()
    app.state.interview_recovery_worker = interview_recovery_worker
    logger.info("Cacanode stateless inference service started")
    try:
        yield
    finally:
        if manager is not None:
            await manager.stop()
        if resume_worker is not None:
            await resume_worker.stop()
        if interview_recovery_worker is not None:
            await interview_recovery_worker.stop()
        if rabbit_connection is not None:
            await rabbit_connection.close()
        await grpc_server.stop(grace=5)
        if runtime.semantic_answer_cache is not None:
            await runtime.semantic_answer_cache.close()
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
    application.include_router(interview_router(settings))
    application.mount("/metrics", make_asgi_app())

    @application.get("/health", include_in_schema=False)
    async def legacy_health() -> dict[str, str]:
        return {"status": "healthy", "env": settings.APP_ENV, "version": "1.0.0"}

    return application


app = create_app()
