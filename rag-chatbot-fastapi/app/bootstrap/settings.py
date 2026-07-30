from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

PROJECT_ROOT = Path(__file__).resolve().parents[3]
SERVICE_ROOT = Path(__file__).resolve().parents[2]


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
    READINESS_DIAGNOSTICS_TIMEOUT_SECONDS: float = 0.5
    READINESS_INGESTION_SCAN_LIMIT: int = 200

    REDIS_URL: str = "redis://localhost:16379/0"
    REDIS_CONNECT_TIMEOUT_SECONDS: float = 1.0
    REDIS_OPERATION_TIMEOUT_SECONDS: float = 1.0
    RABBITMQ_URL: str = "amqp://rag_user:rag_password@localhost:15673/"
    INGESTION_CHECKPOINT_RETENTION_SECONDS: int = 30 * 24 * 60 * 60
    INGESTION_LEASE_SECONDS: int = 300
    INGESTION_HEARTBEAT_SECONDS: int = 30

    INTERVIEW_ENABLED: bool = False
    INTERVIEW_MESSAGING_ENABLED: bool = False
    INTERVIEW_MEDIA_STREAM_ENABLED: bool = False
    INTERVIEW_CV_ANALYSIS_ENABLED: bool = False
    INTERVIEW_TRANSPORT_SMOKE_MODE: bool = False
    INTERVIEW_ENGINE_ENABLED: bool = False
    INTERVIEW_DURABLE_RESULTS_ENABLED: bool = False
    INTERVIEW_PUBLISH_CONFIRM_MAX_ATTEMPTS: int = 3
    INTERVIEW_RECOVERY_MAX_ATTEMPTS: int = 3
    INTERVIEW_RECOVERY_POLL_SECONDS: int = 5
    INTERVIEW_RECOVERY_BATCH_SIZE: int = 100
    INTERVIEW_MODEL_TIMEOUT_SECONDS: float = 8.0
    INTERVIEW_MODEL_MAX_OUTPUT_TOKENS: int = 1024
    INTERVIEW_MODEL_MAX_ATTEMPTS: int = 2
    INTERVIEW_ENGINE_MAX_CONSECUTIVE_FAILURES: int = 3
    INTERVIEW_UTTERANCE_MAX_SECONDS: int = 90
    INTERVIEW_MIN_QUESTION_WINDOW_SECONDS: int = 10
    INTERVIEW_CLOSING_RESERVE_SECONDS: int = 10
    INTERVIEW_SPEECH_ENERGY_THRESHOLD: int = 500
    INTERVIEW_RUNTIME_TOKEN_SECRET: str = ""
    INTERVIEW_RUNTIME_TOKEN_TTL_SECONDS: int = 900
    INTERVIEW_GLOBAL_CONCURRENCY: int = 10
    INTERVIEW_TENANT_CONCURRENCY: int = 2
    INTERVIEW_SESSION_LEASE_SECONDS: int = 30
    INTERVIEW_SESSION_HEARTBEAT_SECONDS: int = 10
    TWILIO_ACCOUNT_SID: str = ""
    TWILIO_AUTH_TOKEN: str = ""
    TWILIO_MEDIA_STREAM_WSS_URL: str = ""
    CARTESIA_API_KEY: str = ""
    CARTESIA_STT_MODEL: str = "ink-whisper-2025-06-04"
    CARTESIA_TTS_MODEL: str = "sonic-3.5-2026-05-04"
    CARTESIA_API_VERSION: str = "2026-03-01"
    CARTESIA_ENGLISH_VOICE_ID: str = ""
    CARTESIA_VIETNAMESE_VOICE_ID: str = ""
    INTERVIEW_END_OF_UTTERANCE_SILENCE_MS: int = 3000
    INTERVIEW_TTS_OUTPUT_GAIN_DB: float = 0.0
    INTERVIEW_SMOKE_UTTERANCE_MAX_SECONDS: int = 15
    INTERVIEW_MEDIA_MAX_PAYLOAD_BYTES: int = 65536
    CV_ANALYSIS_POLICY_VERSION: str = "cv-redaction-fit-v2"
    CV_ANALYSIS_MODEL_VERSION: str = "resume-analysis-v2"
    CV_ANALYSIS_MAX_EXTRACTED_CHARACTERS: int = 50_000
    CV_ANALYSIS_MAX_EVIDENCE_SEGMENTS: int = 250
    CV_ANALYSIS_MAX_PERSONALIZED_QUESTIONS: int = 2
    CV_ANALYSIS_MAX_PROCESSING_ATTEMPTS: int = 3
    CV_ANALYSIS_PENDING_OUTCOME_TTL_SECONDS: int = 900

    CACHE_ENABLED: bool = False
    CACHE_KEY_PREFIX: str = "ccn:v1"
    CACHE_TTL_JITTER_PERCENT: int = 10
    EMBEDDING_CACHE_ENABLED: bool = False
    EMBEDDING_CACHE_TTL_SECONDS: int = 86400
    RETRIEVAL_CACHE_ENABLED: bool = False
    RETRIEVAL_CACHE_TTL_SECONDS: int = 120
    SEMANTIC_ANSWER_CACHE_MODE: Literal["off", "shadow", "serve"] = "off"
    SEMANTIC_ANSWER_CACHE_TTL_SECONDS: int = 3600
    SEMANTIC_ANSWER_CACHE_SIMILARITY_THRESHOLD: float = 0.97
    SEMANTIC_ANSWER_CACHE_COLLECTION: str = "semantic_answer_cache_v1"
    SEMANTIC_ANSWER_CACHE_VECTOR_NAME: str = "query_v1"
    SEMANTIC_ANSWER_CACHE_CANDIDATE_LIMIT: int = 5
    SEMANTIC_ANSWER_CACHE_CLEANUP_BATCH_SIZE: int = 1000
    GENERATION_RESULT_CACHE_TTL_SECONDS: int = 600

    GRPC_HOST: str = "0.0.0.0"
    GRPC_PORT: int = 50051
    GRPC_PLAINTEXT: bool = True
    GRPC_SERVER_CERTIFICATE: str = ""
    GRPC_SERVER_KEY: str = ""
    GRPC_CLIENT_CA_CERTIFICATE: str = ""
    GRPC_MAX_MESSAGE_BYTES: int = 16 * 1024 * 1024

    SEAWEEDFS_MASTER_URL: str = "http://localhost:19334"
    SEAWEEDFS_FILER_URL: str = "http://localhost:18888"
    SEAWEEDFS_S3_ENDPOINT: str = "http://localhost:18333"
    SEAWEEDFS_ACCESS_KEY: str = ""
    SEAWEEDFS_SECRET_KEY: str = ""
    SEAWEEDFS_BUCKET: str = "cacanode"
    SEAWEEDFS_CONNECT_TIMEOUT_SECONDS: float = 3.0
    SEAWEEDFS_READ_TIMEOUT_SECONDS: float = 30.0
    SEAWEEDFS_MAX_ATTEMPTS: int = 3

    QDRANT_URL: str = "http://localhost:16333"
    QDRANT_API_KEY: str = ""
    QDRANT_COLLECTION: str = "knowledge_units_v2"
    QDRANT_DENSE_VECTOR_NAME: str = "text_embeddinggemma_v1"
    QDRANT_SPARSE_VECTOR_NAME: str = "text_bm25_v1"
    QDRANT_TENANT_FIELD: str = "tenant_id"
    QDRANT_KNOWLEDGE_BASE_FIELD: str = "knowledge_base_id"
    KUZU_DATABASE_PATH: str = "./data/kuzu/cacanode.kuzu"
    GRAPH_SERVICE_URL: str = "http://localhost:8010"
    GRAPH_INTERNAL_TOKEN: str = "development-graph-token"
    GRAPH_TIMEOUT_SECONDS: float = 30.0
    GRAPH_EXTRACTION_BATCH_SIZE: int = 4
    GRAPH_EXTRACTION_MAX_OUTPUT_TOKENS: int = 25_000
    GRAPH_EXTRACTION_REASONING_EFFORT: Literal["low", "medium", "high"] = "low"
    PARSER_VERSION: str = "digital-v1"
    CHUNKER_VERSION: str = "structural-v2"
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
    TEXT_EMBEDDING_TIMEOUT_SECONDS: float = 120.0
    SPARSE_MODEL_ID: str = "Qdrant/bm25"
    SPARSE_MODEL_CACHE_DIR: str = "./data/models/fastembed"

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

    IMAGE_TOP_K: int = 12
    AUDIO_TOP_K: int = 12
    GRAPH_MAX_HOPS: int = 3
    DENSE_CANDIDATE_COUNT: int = 40
    SPARSE_CANDIDATE_COUNT: int = 40
    GRAPH_CANDIDATE_COUNT: int = 20
    FUSION_CANDIDATE_COUNT: int = 30
    RRF_K: int = 30
    SEMANTIC_DENSE_WEIGHT: float = 0.55
    SEMANTIC_SPARSE_WEIGHT: float = 0.30
    SEMANTIC_GRAPH_WEIGHT: float = 0.15
    EXACT_DENSE_WEIGHT: float = 0.25
    EXACT_SPARSE_WEIGHT: float = 0.60
    EXACT_GRAPH_WEIGHT: float = 0.15
    RELATIONAL_DENSE_WEIGHT: float = 0.30
    RELATIONAL_SPARSE_WEIGHT: float = 0.15
    RELATIONAL_GRAPH_WEIGHT: float = 0.55
    CALCULATION_DENSE_WEIGHT: float = 0.35
    CALCULATION_SPARSE_WEIGHT: float = 0.50
    CALCULATION_GRAPH_WEIGHT: float = 0.15
    PRIMARY_CONTEXT_TOP_K: int = 5
    FINAL_CONTEXT_TOP_K: int = 8
    CONTEXT_DOCUMENT_SOFT_LIMIT: int = 2
    NEIGHBOR_EXPANSION_LIMIT: int = 3
    RERANKER_ENABLED: bool = False
    RERANKER_URL: str = "http://localhost:8082"
    RERANKER_MODEL_ID: str = "BAAI/bge-reranker-v2-m3"
    RERANKER_TIMEOUT_SECONDS: float = 10.0
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
    def validate_settings(self) -> "Settings":
        retrieval_counts = (
            self.DENSE_CANDIDATE_COUNT,
            self.SPARSE_CANDIDATE_COUNT,
            self.GRAPH_CANDIDATE_COUNT,
            self.FUSION_CANDIDATE_COUNT,
            self.RRF_K,
            self.PRIMARY_CONTEXT_TOP_K,
            self.FINAL_CONTEXT_TOP_K,
        )
        if any(value <= 0 for value in retrieval_counts):
            raise ValueError("Retrieval candidate and context limits must be positive")
        if not 0 <= self.GRAPH_MAX_HOPS <= 3:
            raise ValueError("GRAPH_MAX_HOPS must be between 0 and 3")
        if self.PRIMARY_CONTEXT_TOP_K > self.FINAL_CONTEXT_TOP_K:
            raise ValueError("PRIMARY_CONTEXT_TOP_K cannot exceed FINAL_CONTEXT_TOP_K")
        if self.RERANKER_TIMEOUT_SECONDS <= 0:
            raise ValueError("RERANKER_TIMEOUT_SECONDS must be positive")
        if self.REDIS_CONNECT_TIMEOUT_SECONDS <= 0 or self.REDIS_OPERATION_TIMEOUT_SECONDS <= 0:
            raise ValueError("Redis timeouts must be positive")
        if not 0 < self.READINESS_DIAGNOSTICS_TIMEOUT_SECONDS <= 2:
            raise ValueError("Readiness diagnostics timeout must be between 0 and 2 seconds")
        if not 1 <= self.READINESS_INGESTION_SCAN_LIMIT <= 1000:
            raise ValueError("Readiness ingestion scan limit must be between 1 and 1000")
        if (
            min(
                self.INGESTION_CHECKPOINT_RETENTION_SECONDS,
                self.INGESTION_LEASE_SECONDS,
                self.INGESTION_HEARTBEAT_SECONDS,
            )
            <= 0
        ):
            raise ValueError("Ingestion checkpoint and lease durations must be positive")
        if self.INGESTION_HEARTBEAT_SECONDS >= self.INGESTION_LEASE_SECONDS:
            raise ValueError("Ingestion heartbeat must be shorter than the lease")
        if not self.INTERVIEW_ENABLED and any(
            (
                self.INTERVIEW_MESSAGING_ENABLED,
                self.INTERVIEW_MEDIA_STREAM_ENABLED,
                self.INTERVIEW_CV_ANALYSIS_ENABLED,
            )
        ):
            raise ValueError("Interview child flags require INTERVIEW_ENABLED")
        if (
            self.INTERVIEW_MEDIA_STREAM_ENABLED or self.INTERVIEW_CV_ANALYSIS_ENABLED
        ) and not self.INTERVIEW_MESSAGING_ENABLED:
            raise ValueError("Interview media and CV analysis require messaging")
        if self.INTERVIEW_MEDIA_STREAM_ENABLED and not all(
            value.strip()
            for value in (
                self.INTERVIEW_RUNTIME_TOKEN_SECRET,
                self.TWILIO_ACCOUNT_SID,
                self.TWILIO_AUTH_TOKEN,
                self.TWILIO_MEDIA_STREAM_WSS_URL,
                self.CARTESIA_API_KEY,
                self.CARTESIA_ENGLISH_VOICE_ID,
                self.CARTESIA_VIETNAMESE_VOICE_ID,
            )
        ):
            raise ValueError(
                "Interview media requires complete Twilio, Cartesia, and token settings"
            )
        if self.INTERVIEW_MEDIA_STREAM_ENABLED and not self.TWILIO_MEDIA_STREAM_WSS_URL.startswith(
            "wss://"
        ):
            raise ValueError("Twilio media stream URL must use WSS")
        if self.INTERVIEW_TRANSPORT_SMOKE_MODE and self.APP_ENV == "production":
            raise ValueError("Interview transport smoke mode is forbidden in production")
        if self.INTERVIEW_TRANSPORT_SMOKE_MODE and not self.INTERVIEW_MEDIA_STREAM_ENABLED:
            raise ValueError("Interview transport smoke mode requires media streaming")
        if self.INTERVIEW_ENGINE_ENABLED and not self.INTERVIEW_MEDIA_STREAM_ENABLED:
            raise ValueError("Interview engine requires media streaming")
        if self.INTERVIEW_MEDIA_STREAM_ENABLED and (
            self.INTERVIEW_ENGINE_ENABLED == self.INTERVIEW_TRANSPORT_SMOKE_MODE
        ):
            raise ValueError("Interview media requires exactly one of engine mode or smoke mode")
        if self.APP_ENV == "production" and self.INTERVIEW_MEDIA_STREAM_ENABLED and not (
            self.INTERVIEW_ENGINE_ENABLED
            and self.INTERVIEW_DURABLE_RESULTS_ENABLED
            and self.model_configured
        ):
            raise ValueError(
                "Production interview media requires engine mode, durable results, and a "
                "configured model"
            )
        if self.INTERVIEW_DURABLE_RESULTS_ENABLED and not (
            self.INTERVIEW_ENGINE_ENABLED and self.INTERVIEW_MESSAGING_ENABLED
        ):
            raise ValueError("Durable interview results require engine mode and messaging")
        if min(
            self.INTERVIEW_PUBLISH_CONFIRM_MAX_ATTEMPTS,
            self.INTERVIEW_RECOVERY_MAX_ATTEMPTS,
        ) < 1:
            raise ValueError("Interview durability retry limits must be positive")
        if self.INTERVIEW_RECOVERY_POLL_SECONDS != 5:
            raise ValueError("Interview recovery polling must be five seconds")
        if self.INTERVIEW_RECOVERY_BATCH_SIZE != 100:
            raise ValueError("Interview recovery batch size must be 100")
        if not 1 <= self.INTERVIEW_RUNTIME_TOKEN_TTL_SECONDS <= 900:
            raise ValueError("Interview runtime token TTL must be at most 900 seconds")
        if not 1 <= self.INTERVIEW_TENANT_CONCURRENCY <= self.INTERVIEW_GLOBAL_CONCURRENCY:
            raise ValueError("Interview concurrency limits are invalid")
        if self.INTERVIEW_SESSION_HEARTBEAT_SECONDS >= self.INTERVIEW_SESSION_LEASE_SECONDS:
            raise ValueError("Interview heartbeat must be shorter than its session lease")
        if not 1500 <= self.INTERVIEW_END_OF_UTTERANCE_SILENCE_MS <= 5000:
            raise ValueError("Interview end-of-utterance silence must be between 1500 and 5000 ms")
        if not 0 <= self.INTERVIEW_TTS_OUTPUT_GAIN_DB <= 12:
            raise ValueError("Interview TTS output gain must be between 0 and 12 dB")
        if self.INTERVIEW_SMOKE_UTTERANCE_MAX_SECONDS != 15:
            raise ValueError("Phase 7 smoke utterance maximum must be 15 seconds")
        if self.INTERVIEW_MODEL_TIMEOUT_SECONDS <= 0:
            raise ValueError("Interview model timeout must be positive")
        if self.INTERVIEW_MODEL_MAX_OUTPUT_TOKENS != 1024:
            raise ValueError("Interview model output maximum must be 1024 tokens")
        if self.INTERVIEW_MODEL_MAX_ATTEMPTS != 2:
            raise ValueError("Interview model attempts must be exactly 2")
        if self.INTERVIEW_ENGINE_MAX_CONSECUTIVE_FAILURES != 3:
            raise ValueError("Interview model failure limit must be exactly 3")
        if self.INTERVIEW_UTTERANCE_MAX_SECONDS != 90:
            raise ValueError("Interview utterance maximum must be 90 seconds")
        if self.INTERVIEW_MIN_QUESTION_WINDOW_SECONDS != 10:
            raise ValueError("Interview minimum question window must be 10 seconds")
        if self.INTERVIEW_CLOSING_RESERVE_SECONDS != 10:
            raise ValueError("Interview closing reserve must be 10 seconds")
        if self.INTERVIEW_SPEECH_ENERGY_THRESHOLD != 500:
            raise ValueError("Interview speech energy threshold must be 500")
        if self.CARTESIA_STT_MODEL != "ink-whisper-2025-06-04":
            raise ValueError("Unsupported Phase 7 Cartesia STT model")
        if self.CARTESIA_TTS_MODEL != "sonic-3.5-2026-05-04":
            raise ValueError("Unsupported Phase 7 Cartesia TTS model")
        if self.CARTESIA_API_VERSION != "2026-03-01":
            raise ValueError("Unsupported Phase 7 Cartesia API version")
        if (
            not self.CV_ANALYSIS_POLICY_VERSION.strip()
            or not self.CV_ANALYSIS_MODEL_VERSION.strip()
        ):
            raise ValueError("CV analysis policy and model versions must not be blank")
        if not 1 <= self.CV_ANALYSIS_MAX_EXTRACTED_CHARACTERS <= 50_000:
            raise ValueError("CV analysis extracted-character limit must be at most 50000")
        if not 1 <= self.CV_ANALYSIS_MAX_EVIDENCE_SEGMENTS <= 250:
            raise ValueError("CV analysis evidence limit must be at most 250")
        if self.CV_ANALYSIS_MAX_PERSONALIZED_QUESTIONS != 2:
            raise ValueError("CV analysis question limit must be exactly 2")
        if self.CV_ANALYSIS_MAX_PROCESSING_ATTEMPTS != 3:
            raise ValueError("CV analysis processing attempts must be exactly 3")
        if not 1 <= self.CV_ANALYSIS_PENDING_OUTCOME_TTL_SECONDS <= 900:
            raise ValueError("CV analysis pending outcome TTL must be at most 15 minutes")
        if not 0 <= self.CACHE_TTL_JITTER_PERCENT <= 100:
            raise ValueError("CACHE_TTL_JITTER_PERCENT must be between 0 and 100")
        if self.EMBEDDING_CACHE_TTL_SECONDS <= 0:
            raise ValueError("EMBEDDING_CACHE_TTL_SECONDS must be positive")
        if self.RETRIEVAL_CACHE_TTL_SECONDS <= 0:
            raise ValueError("RETRIEVAL_CACHE_TTL_SECONDS must be positive")
        if self.SEMANTIC_ANSWER_CACHE_TTL_SECONDS <= 0:
            raise ValueError("SEMANTIC_ANSWER_CACHE_TTL_SECONDS must be positive")
        if not 0 < self.SEMANTIC_ANSWER_CACHE_SIMILARITY_THRESHOLD <= 1:
            raise ValueError("SEMANTIC_ANSWER_CACHE_SIMILARITY_THRESHOLD must be in (0, 1]")
        if self.SEMANTIC_ANSWER_CACHE_CANDIDATE_LIMIT <= 0:
            raise ValueError("SEMANTIC_ANSWER_CACHE_CANDIDATE_LIMIT must be positive")
        if self.SEMANTIC_ANSWER_CACHE_CLEANUP_BATCH_SIZE <= 0:
            raise ValueError("SEMANTIC_ANSWER_CACHE_CLEANUP_BATCH_SIZE must be positive")
        if not self.SEMANTIC_ANSWER_CACHE_COLLECTION.strip():
            raise ValueError("SEMANTIC_ANSWER_CACHE_COLLECTION must not be blank")
        if not self.SEMANTIC_ANSWER_CACHE_VECTOR_NAME.strip():
            raise ValueError("SEMANTIC_ANSWER_CACHE_VECTOR_NAME must not be blank")
        if self.GENERATION_RESULT_CACHE_TTL_SECONDS <= 0:
            raise ValueError("GENERATION_RESULT_CACHE_TTL_SECONDS must be positive")
        if self.GRPC_PORT <= 0 or self.GRPC_MAX_MESSAGE_BYTES <= 0:
            raise ValueError("gRPC port and message size must be positive")
        if not self.GRPC_PLAINTEXT and not all(
            value.strip()
            for value in (
                self.GRPC_SERVER_CERTIFICATE,
                self.GRPC_SERVER_KEY,
                self.GRPC_CLIENT_CA_CERTIFICATE,
            )
        ):
            raise ValueError("Production gRPC mTLS material is incomplete")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
