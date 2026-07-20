from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.core.config import Settings


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


def test_mtls_requires_complete_server_material() -> None:
    with pytest.raises(ValidationError, match="mTLS material"):
        Settings(_env_file=(), GRPC_PLAINTEXT=False)
