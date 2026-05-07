"""FastAPI application entry point.

Initializes the FastAPI application with all routers, middleware, and event handlers.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.services.ingestion.router import router as ingestion_router
from app.services.chat.router import router as chat_router
from app.services.llm.router import router as llm_router

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan handler for startup and shutdown events.

    Logs all registered routes on startup for debugging purposes.
    """
    # Startup
    logger.info("=" * 50)
    logger.info("FastAPI application starting up...")
    logger.info("Environment: %s", settings.APP_ENV)
    logger.info("Registered routes:")
    for route in app.routes:
        if hasattr(route, "methods"):
            methods = ", ".join(route.methods)
            logger.info("  [%s] %s", methods, route.path)
    logger.info("=" * 50)
    yield
    # Shutdown
    logger.info("FastAPI application shutting down...")


# Initialize FastAPI application
app = FastAPI(
    title="GraphRAG Chatbot API",
    description="Multi-tenant SaaS GraphRAG chatbot platform with LLM gateway",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers with prefixes and tags
app.include_router(
    ingestion_router,
    prefix="/ingest",
    tags=["ingestion"],
)
app.include_router(
    chat_router,
    prefix="/chat",
    tags=["chat"],
)
app.include_router(
    llm_router,
    prefix="/llm",
    tags=["llm"],
)


@app.get("/health", tags=["health"])
async def health_check():
    """Health check endpoint for monitoring and load balancers.

    Returns:
        Status information including application environment.
    """
    return {
        "status": "healthy",
        "env": settings.APP_ENV,
        "version": "1.0.0",
    }
