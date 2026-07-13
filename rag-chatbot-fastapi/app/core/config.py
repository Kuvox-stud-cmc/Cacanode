from functools import lru_cache
from pathlib import Path
from typing import Literal
from urllib.parse import quote, unquote, urlsplit, urlunsplit

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

PROJECT_ROOT = Path(__file__).resolve().parents[3]
SERVICE_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_POSTGRES_PASSWORD = "change-me"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(PROJECT_ROOT / ".env", SERVICE_ROOT / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    APP_ENV: Literal["development", "test", "staging", "production"] = "development"
    APP_HOST: str = "0.0.0.0"
    APP_PORT: int = 8000
    LOG_LEVEL: str = "INFO"
    CORS_ORIGINS: str = "http://localhost:3000"
    DEFAULT_LOCALE: str = "vi-VN"
    READINESS_REQUIRE_MODELS: bool = False

    TOKEN_KEY: str = "development-only-secret"
    BUSINESS_API_BASE_URL: str = "http://localhost:8080"
    INTEGRATION_TOKEN_PEPPER: str = "development-integration-token-pepper"

    POSTGRES_HOST: str = "localhost"
    POSTGRES_PORT: int = 5432
    POSTGRES_DB: str = "cacanode"
    POSTGRES_USER: str = "cacanode"
    POSTGRES_PASSWORD: str = DEFAULT_POSTGRES_PASSWORD
    POSTGRES_URL: str = f"postgresql://cacanode:{DEFAULT_POSTGRES_PASSWORD}@localhost:5432/cacanode"
    REDIS_URL: str = "redis://localhost:6379/0"
    RABBITMQ_URL: str = "amqp://rag_user:rag_password@localhost:5672/"

    SEAWEEDFS_MASTER_URL: str = "http://localhost:9333"
    SEAWEEDFS_FILER_URL: str = "http://localhost:8888"
    SEAWEEDFS_S3_ENDPOINT: str = "http://localhost:8333"
    SEAWEEDFS_ACCESS_KEY: str = ""
    SEAWEEDFS_SECRET_KEY: str = ""
    SEAWEEDFS_BUCKET: str = "cacanode"

    QDRANT_URL: str = "http://localhost:6333"
    QDRANT_API_KEY: str = ""
    QDRANT_COLLECTION: str = "knowledge_units_v1"
    QDRANT_TENANT_FIELD: str = "tenant_id"
    QDRANT_KNOWLEDGE_BASE_FIELD: str = "knowledge_base_id"
    KUZU_DATABASE_PATH: str = "./data/kuzu/cacanode.kuzu"
    GRAPH_SERVICE_URL: str = "http://localhost:8010"
    GRAPH_INTERNAL_TOKEN: str = "development-graph-token"
    INGESTION_INTERNAL_TOKEN: str = "development-ingestion-token"
    GRAPH_TIMEOUT_SECONDS: float = 30.0
    GRAPH_EXTRACTION_BATCH_SIZE: int = 12
    PARSER_VERSION: str = "digital-v1"
    CHUNKER_VERSION: str = "structural-v1"
    SPREADSHEET_MAX_ROWS: int = 250_000
    SPREADSHEET_MAX_COLUMNS: int = 2_000

    LLM_PROVIDER: Literal["ollama", "openai"] = "ollama"
    LLM_BASE_URL: str = "http://localhost:8001/v1"
    LLM_INTERNAL_API_KEY: str = "development-only-key"
    LLM_MODEL_ID: str = ""
    LLM_ADAPTER_ID: str = ""
    LLM_TEMPERATURE: float = 0.2
    LLM_MAX_OUTPUT_TOKENS: int = 1024
    LLM_TIMEOUT_SECONDS: float = 90.0
    LLM_USE_OLLAMA_NATIVE_CHAT: bool = True
    LLM_DISABLE_THINKING: bool = True
    OPENAI_API_KEY: str = ""
    OPENAI_MODEL: str = "o4-mini"

    TEXT_EMBEDDING_BASE_URL: str = "http://localhost:8081"
    TEXT_EMBEDDING_MODEL_ID: str = "google/embeddinggemma-300m"
    TEXT_EMBEDDING_DIMENSION: int = 768
    TEXT_EMBEDDING_BATCH_SIZE: int = 32

    WORKER_MODE: Literal["embedded", "dedicated", "disabled"] = "embedded"
    WORKER_KINDS: str = "document,ocr,asr,vision,audio,video"
    WORKER_POLL_INTERVAL_SECONDS: float = 2.0

    CLIP_MODEL_ID: str = ""
    CLAP_MODEL_ID: str = ""
    WHISPER_MODEL_ID: str = ""
    OCR_ENGINE: str = "paddleocr"
    OCR_LANGUAGE: str = "vi"
    FFMPEG_BINARY: str = "ffmpeg"
    MODEL_REGISTRY_TOKEN: str = ""
    MODEL_CACHE_DIR: str = "./data/models"
    AI_DEVICE: str = "cpu"
    AI_DTYPE: str = "auto"

    MAX_DOCUMENT_MB: int = 20
    MAX_IMAGE_MB: int = 20
    MAX_AUDIO_MB: int = 200
    MAX_VIDEO_MB: int = 500
    MALWARE_SCAN_ENABLED: bool = False

    TEXT_TOP_K: int = 20
    IMAGE_TOP_K: int = 12
    AUDIO_TOP_K: int = 12
    GRAPH_MAX_HOPS: int = 3
    FINAL_CONTEXT_TOP_K: int = 8
    MIN_RETRIEVAL_CONFIDENCE: float = 0.35
    ENABLE_RERANKER: bool = True
    ENABLE_GENERAL_KNOWLEDGE: bool = False

    SSE_HEARTBEAT_SECONDS: int = 15
    IDEMPOTENCY_TTL_HOURS: int = 24
    PUBLIC_RATE_LIMIT_PER_MINUTE: int = 120
    MAX_CONCURRENT_STREAMS_PER_TENANT: int = 50

    OTEL_EXPORTER_OTLP_ENDPOINT: str = ""
    DISABLE_EXTERNAL_TELEMETRY: bool = True

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",") if origin.strip()]

    @property
    def worker_kinds(self) -> tuple[str, ...]:
        return tuple(kind.strip() for kind in self.WORKER_KINDS.split(",") if kind.strip())

    @property
    def model_configured(self) -> bool:
        if self.LLM_PROVIDER == "openai":
            return bool(self.OPENAI_API_KEY and self.OPENAI_MODEL)
        return bool(self.LLM_MODEL_ID and self.LLM_BASE_URL)

    @model_validator(mode="after")
    def sync_postgres_url_password(self) -> "Settings":
        parts = urlsplit(self.POSTGRES_URL)
        if (
            parts.scheme.startswith("postgres")
            and parts.password == DEFAULT_POSTGRES_PASSWORD
            and self.POSTGRES_PASSWORD
            and self.POSTGRES_PASSWORD != DEFAULT_POSTGRES_PASSWORD
        ):
            username = quote(unquote(parts.username or self.POSTGRES_USER), safe="")
            password = quote(self.POSTGRES_PASSWORD, safe="")
            host = parts.hostname or self.POSTGRES_HOST
            if ":" in host and not host.startswith("["):
                host = f"[{host}]"
            netloc = f"{username}:{password}@{host}"
            if parts.port is not None:
                netloc = f"{netloc}:{parts.port}"
            self.POSTGRES_URL = urlunsplit(
                (parts.scheme, netloc, parts.path, parts.query, parts.fragment)
            )
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
