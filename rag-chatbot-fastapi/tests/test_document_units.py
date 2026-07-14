from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import jwt
import pytest
from fastapi.testclient import TestClient

from app.api.v1.documents import (
    DocumentUnitResponse,
    QdrantDocumentUnitStore,
    get_document_unit_store,
)
from app.core.config import settings
from app.main import app


def auth_headers(tenant_id: str = "tenant-1") -> dict[str, str]:
    token = jwt.encode(
        {
            "sub": "admin@cacanode.local",
            "tenantId": tenant_id,
            "userId": "user-1",
            "exp": datetime.now(UTC) + timedelta(minutes=15),
        },
        settings.TOKEN_KEY,
        algorithm="HS256",
    )
    return {"Authorization": f"Bearer {token}"}


class FakeQdrantClient:
    def __init__(self, pages: list[tuple[list[object], int | None]]) -> None:
        self.pages = pages
        self.calls: list[dict[str, object]] = []

    async def collection_exists(self, collection_name: str) -> bool:
        self.collection_name = collection_name
        return True

    async def scroll(self, **kwargs: object) -> tuple[list[object], int | None]:
        self.calls.append(kwargs)
        return self.pages[len(self.calls) - 1]


def record(payload: dict[str, object]) -> object:
    return SimpleNamespace(payload=payload)


@pytest.mark.asyncio
async def test_unit_store_scrolls_all_pages_orders_units_and_preserves_metadata() -> None:
    client = FakeQdrantClient(
        [
            (
                [
                    record(
                        {
                            "tenant_id": "tenant-1",
                            "document_id": "doc-1",
                            "chunk_index": 2,
                            "unit_id": "unit-c",
                            "text": "third",
                            "block_type": "row",
                            "modality": "spreadsheet",
                            "sheet_name": "Orders",
                            "cell_range": "A3:C3",
                            "table_id": "table-1",
                        }
                    )
                ],
                42,
            ),
            (
                [
                    record(
                        {
                            "tenant_id": "tenant-1",
                            "document_id": "doc-1",
                            "chunk_index": 0,
                            "unit_id": "unit-a",
                            "text": "first",
                            "source_name": "orders.xlsx",
                            "block_type": "heading",
                            "section_path": ["Overview"],
                            "page_number": 1,
                        }
                    ),
                    record(
                        {
                            "tenant_id": "other-tenant",
                            "document_id": "doc-1",
                            "chunk_index": 1,
                            "text": "must not leak",
                        }
                    ),
                ],
                None,
            ),
        ]
    )
    store = QdrantDocumentUnitStore(client=client)  # type: ignore[arg-type]

    units = await store.list_units(tenant_id="tenant-1", document_id="doc-1")

    assert [unit.chunk_index for unit in units] == [0, 2]
    assert units[0].section_path == ["Overview"]
    assert units[1].sheet_name == "Orders"
    assert units[1].cell_range == "A3:C3"
    assert len(client.calls) == 2
    assert client.calls[1]["offset"] == 42


class FakeDocumentUnitStore:
    def __init__(self, units: list[DocumentUnitResponse]) -> None:
        self.units = units

    async def list_units(self, *, tenant_id: str, document_id: str) -> list[DocumentUnitResponse]:
        self.request = {"tenant_id": tenant_id, "document_id": document_id}
        return self.units


def test_units_endpoint_is_authenticated_tenant_scoped_and_supports_legacy_units() -> None:
    store = FakeDocumentUnitStore(
        [
            DocumentUnitResponse(
                unit_id=None,
                chunk_index=4,
                text="legacy content",
                block_type="page",
                page_number=2,
            )
        ]
    )
    app.dependency_overrides[get_document_unit_store] = lambda: store
    try:
        with TestClient(app) as client:
            unauthenticated = client.get("/api/v1/documents/doc-1/units")
            response = client.get(
                "/api/v1/documents/doc-1/units", headers=auth_headers("tenant-77")
            )
    finally:
        app.dependency_overrides.clear()

    assert unauthenticated.status_code in {401, 403}
    assert response.status_code == 200
    assert response.json()[0]["unit_id"] is None
    assert response.json()[0]["chunk_index"] == 4
    assert store.request == {"tenant_id": "tenant-77", "document_id": "doc-1"}


def test_units_endpoint_returns_not_found_for_unknown_document() -> None:
    store = FakeDocumentUnitStore([])
    app.dependency_overrides[get_document_unit_store] = lambda: store
    try:
        with TestClient(app) as client:
            response = client.get(
                "/api/v1/documents/missing/units", headers=auth_headers()
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 404
    assert response.json()["detail"] == "Indexed document was not found"
