from __future__ import annotations

from datetime import UTC, datetime
from types import SimpleNamespace
from uuid import uuid4

import pytest

from app.core.config import Settings
from app.maintenance.evaluate_retrieval import evaluate
from app.maintenance.reindex_documents import _event_from_row, build_parser, reindex


@pytest.mark.asyncio
async def test_reindex_dry_run_reports_filters_without_building_pipeline(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    document = {"id": uuid4()}
    monkeypatch.setattr(
        "app.maintenance.reindex_documents.load_documents",
        lambda settings, args: [document],
    )
    args = build_parser().parse_args(
        [
            "--target-collection",
            "knowledge_units_v2",
            "--dry-run",
            "--tenant-id",
            str(uuid4()),
            "--after-id",
            str(uuid4()),
            "--updated-since",
            "2026-07-01T00:00:00Z",
        ]
    )

    summary = await reindex(Settings(), args)

    assert summary == {
        "target_collection": "knowledge_units_v2",
        "matched": 1,
        "completed": 0,
        "indexed_units": 0,
        "failed": 0,
        "failures": [],
        "dry_run": True,
    }


def test_reindex_reconstructs_original_ingestion_event() -> None:
    row = {
        "id": uuid4(),
        "tenant_id": uuid4(),
        "knowledge_base_id": uuid4(),
        "uploaded_by": uuid4(),
        "file_name": "pricing.xlsx",
        "file_type": "XLSX",
        "file_size_bytes": 123,
        "storage_path": "tenants/t/documents/d/pricing.xlsx",
        "job_id": "legacy-non-uuid-job",
        "updated_at": datetime.now(UTC),
    }

    event = _event_from_row(row)

    assert event.document_id == row["id"]
    assert event.content_type.endswith("spreadsheetml.sheet")
    assert event.storage_key == row["storage_path"]


def test_retrieval_evaluation_reports_required_metrics() -> None:
    dataset = [
        {"id": "q1", "relevant_unit_ids": ["u1"]},
        {"id": "q2", "relevant_unit_ids": []},
    ]
    results = {
        "q1": {
            "unit_ids": ["u2", "u1"],
            "channels": ["dense", "sparse"],
            "latency_ms": 20,
        },
        "q2": {"unit_ids": [], "channels": [], "latency_ms": 10},
    }

    report = evaluate(dataset, results)

    assert report["recall_at_5"] == 1.0
    assert report["mrr"] == 0.5
    assert report["ndcg_at_10"] > 0
    assert report["no_answer_precision"] == 1.0
    assert report["channel_contribution"] == {"dense": 1, "sparse": 1}
    assert report["p95_latency_ms"] == 20
    assert SimpleNamespace(value=report["query_count"]).value == 2
