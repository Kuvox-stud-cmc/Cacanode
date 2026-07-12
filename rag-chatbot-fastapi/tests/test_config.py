from app.core.config import DEFAULT_POSTGRES_PASSWORD, Settings


def test_postgres_url_uses_component_password_when_url_has_placeholder() -> None:
    settings = Settings(
        _env_file=(),
        POSTGRES_URL=f"postgresql://cacanode:{DEFAULT_POSTGRES_PASSWORD}@localhost:5432/cacanode",
        POSTGRES_PASSWORD="real-secret",
    )

    assert settings.POSTGRES_URL == "postgresql://cacanode:real-secret@localhost:5432/cacanode"


def test_postgres_url_keeps_explicit_non_placeholder_password() -> None:
    settings = Settings(
        _env_file=(),
        POSTGRES_URL="postgresql://cacanode:explicit@localhost:5432/cacanode",
        POSTGRES_PASSWORD="real-secret",
    )

    assert settings.POSTGRES_URL == "postgresql://cacanode:explicit@localhost:5432/cacanode"


def test_llm_provider_defaults_to_ollama_with_openai_fields_available() -> None:
    settings = Settings(_env_file=())

    assert settings.LLM_PROVIDER == "ollama"
    assert settings.OPENAI_API_KEY == ""
    assert settings.OPENAI_MODEL == "o4-mini"


def test_model_configured_uses_ollama_settings_for_ollama_provider() -> None:
    settings = Settings(_env_file=(), LLM_PROVIDER="ollama", LLM_MODEL_ID="gemma4:12b")

    assert settings.model_configured is True


def test_model_configured_uses_openai_settings_for_openai_provider() -> None:
    configured = Settings(
        _env_file=(),
        LLM_PROVIDER="openai",
        LLM_MODEL_ID="",
        OPENAI_API_KEY="test-key",
        OPENAI_MODEL="gpt-test",
    )
    missing_key = Settings(
        _env_file=(),
        LLM_PROVIDER="openai",
        LLM_MODEL_ID="gemma4:12b",
        OPENAI_API_KEY="",
        OPENAI_MODEL="gpt-test",
    )

    assert configured.model_configured is True
    assert missing_key.model_configured is False
