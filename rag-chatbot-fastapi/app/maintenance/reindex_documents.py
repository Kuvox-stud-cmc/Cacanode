from __future__ import annotations

import argparse
import asyncio
import json
from collections.abc import Sequence
from datetime import UTC, datetime
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid4, uuid5

import psycopg
from psycopg.rows import dict_row
from qdrant_client import AsyncQdrantClient

from app.core.config import Settings
from app.ingestion.events import DocumentIngestRequestedEvent
from app.ingestion.factory import create_document_ingestion_pipeline

CONTENT_TYPES = {
    "PDF": "application/pdf",
    "DOCX": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "TXT": "text/plain",
    "MARKDOWN": "text/markdown",
    "HTML": "text/html",
    "XLSX": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "CSV": "text/csv",
}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Reindex completed documents into Qdrant")
    parser.add_argument("--target-collection", default="knowledge_units_v2")
    parser.add_argument("--batch-size", type=int, default=50)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--tenant-id")
    parser.add_argument("--knowledge-base-id")
    parser.add_argument("--after-id")
    parser.add_argument("--updated-since")
    return parser


def load_documents(settings: Settings, args: argparse.Namespace) -> list[dict[str, Any]]:
    clauses = ["status = 'COMPLETED'"]
    values: list[Any] = []
    for column, value in (
        ("tenant_id", args.tenant_id),
        ("knowledge_base_id", args.knowledge_base_id),
    ):
        if value:
            clauses.append(f"{column} = %s")
            values.append(value)
    if args.after_id:
        clauses.append("id > %s")
        values.append(args.after_id)
    if args.updated_since:
        clauses.append("updated_at >= %s")
        values.append(datetime.fromisoformat(args.updated_since.replace("Z", "+00:00")))
    query = f"""
        SELECT id, tenant_id, knowledge_base_id, uploaded_by, file_name, file_type,
               file_size_bytes, storage_path, job_id, updated_at
        FROM documents
        WHERE {" AND ".join(clauses)}
        ORDER BY id
    """
    with psycopg.connect(settings.POSTGRES_URL) as connection:
        with connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(query, values)
            return list(cursor.fetchall())


async def reindex(settings: Settings, args: argparse.Namespace) -> dict[str, Any]:
    documents = await asyncio.to_thread(load_documents, settings, args)
    summary: dict[str, Any] = {
        "target_collection": args.target_collection,
        "matched": len(documents),
        "completed": 0,
        "indexed_units": 0,
        "failed": 0,
        "failures": [],
        "dry_run": args.dry_run,
    }
    if args.dry_run:
        return summary
    target_settings = settings.model_copy(update={"QDRANT_COLLECTION": args.target_collection})
    pipeline = create_document_ingestion_pipeline(target_settings)
    for start in range(0, len(documents), args.batch_size):
        for row in documents[start : start + args.batch_size]:
            try:
                summary["indexed_units"] += await pipeline.ingest(_event_from_row(row))
                summary["completed"] += 1
            except Exception as exc:
                summary["failed"] += 1
                summary["failures"].append({"document_id": str(row["id"]), "error": str(exc)[:500]})
    client = AsyncQdrantClient(
        url=target_settings.QDRANT_URL,
        api_key=target_settings.QDRANT_API_KEY or None,
        check_compatibility=False,
    )
    try:
        if await client.collection_exists(args.target_collection):
            count = await client.count(
                collection_name=args.target_collection,
                exact=True,
            )
            point_count = int(count.count)
        else:
            point_count = 0
    finally:
        await client.close()
    summary["target_point_count"] = point_count
    summary["point_count_valid"] = point_count >= summary["indexed_units"]
    return summary


def _event_from_row(row: dict[str, Any]) -> DocumentIngestRequestedEvent:
    job_id = _uuid(row.get("job_id"), f"reindex-job:{row['id']}")
    return DocumentIngestRequestedEvent(
        schema_version="1.0",
        event_id=uuid4(),
        job_id=job_id,
        tenant_id=UUID(str(row["tenant_id"])),
        knowledge_base_id=UUID(str(row["knowledge_base_id"])),
        document_id=UUID(str(row["id"])),
        uploader_id=UUID(str(row["uploaded_by"])),
        storage_key=str(row["storage_path"]),
        file_name=str(row["file_name"]),
        content_type=CONTENT_TYPES.get(str(row["file_type"]), "application/octet-stream"),
        file_size_bytes=int(row["file_size_bytes"]),
        occurred_at=row.get("updated_at") or datetime.now(UTC),
    )


def _uuid(value: object, fallback: str) -> UUID:
    try:
        return UUID(str(value))
    except (TypeError, ValueError):
        return uuid5(NAMESPACE_URL, fallback)


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.batch_size < 1:
        raise SystemExit("--batch-size must be positive")
    summary = asyncio.run(reindex(Settings(), args))
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True))
    return 1 if summary["failed"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
