from __future__ import annotations

from pathlib import Path

from app.main import create_app

ROOT = Path(__file__).resolve().parents[1]


def test_fastapi_runtime_has_no_postgres_dependency_or_business_sql() -> None:
    source = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "app").rglob("*.py")
        if "generated" not in path.parts
    )
    project = (ROOT / "pyproject.toml").read_text(encoding="utf-8")
    forbidden = ("psycopg", "POSTGRES_URL", "PostgresChatSessionStore", "SELECT search_revision")
    assert all(value not in source for value in forbidden)
    assert "psycopg" not in project


def test_http_surface_is_health_and_metrics_only() -> None:
    paths = {route.path for route in create_app().routes}
    assert paths == {"/health", "/health/live", "/health/ready", "/metrics"}
