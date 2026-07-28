from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.bootstrap.settings import Settings


def test_ai_settings_have_no_postgres_or_business_auth_fields() -> None:
    configured = Settings(_env_file=())
    assert not any(name.startswith("POSTGRES_") for name in type(configured).model_fields)
    assert "TOKEN_KEY" not in type(configured).model_fields
    assert "INTEGRATION_TOKEN_PEPPER" not in type(configured).model_fields


def test_grpc_defaults_are_plaintext_for_local_development() -> None:
    configured = Settings(_env_file=())
    assert configured.GRPC_PLAINTEXT is True
    assert configured.GRPC_PORT == 50051
    assert configured.GENERATION_RESULT_CACHE_TTL_SECONDS == 600
    assert configured.READINESS_DIAGNOSTICS_TIMEOUT_SECONDS == 0.5
    assert configured.READINESS_INGESTION_SCAN_LIMIT == 200


def test_readiness_diagnostic_bounds_are_validated() -> None:
    with pytest.raises(ValidationError, match="diagnostics timeout"):
        Settings(_env_file=(), READINESS_DIAGNOSTICS_TIMEOUT_SECONDS=2.1)
    with pytest.raises(ValidationError, match="scan limit"):
        Settings(_env_file=(), READINESS_INGESTION_SCAN_LIMIT=0)


def test_mtls_requires_complete_server_material() -> None:
    with pytest.raises(ValidationError, match="mTLS material"):
        Settings(_env_file=(), GRPC_PLAINTEXT=False)


def test_interview_flags_default_off_and_validate_dependencies() -> None:
    configured = Settings(_env_file=())
    assert configured.INTERVIEW_ENABLED is False
    assert configured.INTERVIEW_MESSAGING_ENABLED is False
    assert configured.INTERVIEW_MEDIA_STREAM_ENABLED is False
    assert configured.INTERVIEW_CV_ANALYSIS_ENABLED is False
    assert configured.INTERVIEW_ENGINE_ENABLED is False
    assert configured.INTERVIEW_TRANSPORT_SMOKE_MODE is False
    assert configured.INTERVIEW_DURABLE_RESULTS_ENABLED is False

    with pytest.raises(ValidationError, match="child flags"):
        Settings(_env_file=(), INTERVIEW_MESSAGING_ENABLED=True)
    with pytest.raises(ValidationError, match="require messaging"):
        Settings(
            _env_file=(),
            INTERVIEW_ENABLED=True,
            INTERVIEW_MEDIA_STREAM_ENABLED=True,
        )


def test_interview_media_requires_exactly_one_runtime_mode() -> None:
    base = {
        "INTERVIEW_ENABLED": True,
        "INTERVIEW_MESSAGING_ENABLED": True,
        "INTERVIEW_MEDIA_STREAM_ENABLED": True,
        "INTERVIEW_RUNTIME_TOKEN_SECRET": "runtime-secret",
        "TWILIO_ACCOUNT_SID": "AC" + "1" * 32,
        "TWILIO_AUTH_TOKEN": "auth-token",
        "TWILIO_MEDIA_STREAM_WSS_URL": "wss://example.test/media",
        "CARTESIA_API_KEY": "cartesia-key",
        "CARTESIA_ENGLISH_VOICE_ID": "voice-en",
        "CARTESIA_VIETNAMESE_VOICE_ID": "voice-vi",
    }
    with pytest.raises(ValidationError, match="exactly one"):
        Settings(_env_file=(), **base)
    with pytest.raises(ValidationError, match="exactly one"):
        Settings(
            _env_file=(),
            **base,
            INTERVIEW_ENGINE_ENABLED=True,
            INTERVIEW_TRANSPORT_SMOKE_MODE=True,
        )
    assert Settings(_env_file=(), **base, INTERVIEW_ENGINE_ENABLED=True).INTERVIEW_ENGINE_ENABLED
