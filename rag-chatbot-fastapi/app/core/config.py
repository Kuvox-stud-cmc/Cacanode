"""Application configuration using pydantic-settings.

All configuration is loaded from environment variables with sensible defaults.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables.

    Uses pydantic-settings to validate and parse environment variables.
    Configuration is read from .env file if present.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ============================================
    # Application Configuration
    # ============================================
    APP_ENV: str = "development"
    APP_PORT: int = 8000
    CORS_ORIGINS: str = "http://localhost:3000"

    # ============================================
    # LLM Configuration (Groq - default provider)
    # ============================================
    GROQ_API_KEY: str = ""
    LLM_MODEL: str = "llama-3.3-70b-versatile"

    # ============================================
    # Embedding Configuration (Voyage AI - default)
    # ============================================
    VOYAGE_API_KEY: str = ""
    EMBED_MODEL: str = "voyage-3"

    # ============================================
    # Vector Store (Qdrant)
    # ============================================
    QDRANT_HOST: str = "localhost"
    QDRANT_PORT: int = 6333

    # ============================================
    # Graph Database (Kuzu - embedded)
    # ============================================
    KUZU_DATA_PATH: str = "./data/kuzu"

    # ============================================
    # Cache (Redis)
    # ============================================
    REDIS_URL: str = "redis://localhost:6379/0"

    # ============================================
    # Message Queue (RabbitMQ)
    # ============================================
    RABBITMQ_URL: str = "amqp://guest:guest@localhost:5672/"

    # ============================================
    # Object Storage (SeaweedFS - S3-compatible)
    # ============================================
    STORAGE_ENDPOINT: str = "http://localhost:8333"
    STORAGE_ACCESS_KEY: str = ""
    STORAGE_SECRET_KEY: str = ""
    STORAGE_BUCKET: str = "documents"

    # ============================================
    # JWT Authentication (Shared with Spring Boot)
    # ============================================
    JWT_SECRET: str = ""
    JWT_ALGORITHM: str = "HS256"

    # ============================================
    # Spring Boot API Integration
    # ============================================
    SPRING_BASE_URL: str = "http://localhost:8080"

    @property
    def cors_origins_list(self) -> list[str]:
        """Parse CORS_ORIGINS string into a list of origins.

        Returns:
            List of allowed CORS origin URLs.
        """
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",")]


# Global settings instance
settings = Settings()
