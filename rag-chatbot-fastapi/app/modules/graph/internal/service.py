from __future__ import annotations

import hashlib
import json
import re
from dataclasses import asdict
from pathlib import Path
from threading import RLock
from typing import Any

import httpx
import kuzu

from app.modules.graph.api import (
    GraphBatch,
    GraphRejectedError,
    GraphSearchQuery,
    GraphSearchResult,
    GraphUnavailableError,
)
from app.modules.graph.internal.config import GraphConfig


class KuzuGraphRepository:
    """Single-process Kuzu owner used only by graph_main."""

    def __init__(self, database_path: str):
        path = Path(database_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self._database = kuzu.Database(str(path))
        self._connection = kuzu.Connection(self._database)
        self._lock = RLock()
        self._schema()

    def _schema(self) -> None:
        statements = (
            "CREATE NODE TABLE IF NOT EXISTS Source(id STRING, tenant_id STRING, "
            "knowledge_base_id STRING, name STRING, PRIMARY KEY(id))",
            "CREATE NODE TABLE IF NOT EXISTS KnowledgeUnit(id STRING, source_id STRING, "
            "text STRING, page_number INT64, section_path STRING, sheet_name STRING, "
            "cell_range STRING, PRIMARY KEY(id))",
            "CREATE NODE TABLE IF NOT EXISTS Entity(id STRING, tenant_id STRING, "
            "knowledge_base_id STRING, normalized_name STRING, name STRING, "
            "entity_type STRING, aliases STRING, PRIMARY KEY(id))",
            "CREATE REL TABLE IF NOT EXISTS CONTAINS(FROM Source TO KnowledgeUnit)",
            "CREATE REL TABLE IF NOT EXISTS MENTIONS(FROM KnowledgeUnit TO Entity, "
            "evidence_unit_id STRING)",
            "CREATE REL TABLE IF NOT EXISTS RELATED_TO(FROM Entity TO Entity, predicate STRING, "
            "evidence_unit_id STRING, source_id STRING)",
        )
        with self._lock:
            for statement in statements:
                self._connection.execute(statement)

    def replace_source(self, batch: GraphBatch) -> None:
        with self._lock:
            self._connection.execute("BEGIN TRANSACTION")
            try:
                self._delete_source(batch.tenant_id, batch.source_id)
                self._connection.execute(
                    "CREATE (:Source {id: $id, tenant_id: $tenant, "
                    "knowledge_base_id: $kb, name: $name})",
                    {
                        "id": batch.source_id,
                        "tenant": batch.tenant_id,
                        "kb": batch.knowledge_base_id,
                        "name": batch.source_name,
                    },
                )
                entities: dict[str, str] = {}
                for entity in batch.entities:
                    entity_id = _entity_id(
                        batch.tenant_id, batch.knowledge_base_id, entity.normalized_name
                    )
                    entities[entity.normalized_name] = entity_id
                    self._connection.execute(
                        "MERGE (e:Entity {id: $id}) SET e.tenant_id=$tenant, "
                        "e.knowledge_base_id=$kb, e.normalized_name=$normalized, e.name=$name, "
                        "e.entity_type=$type, e.aliases=$aliases",
                        {
                            "id": entity_id,
                            "tenant": batch.tenant_id,
                            "kb": batch.knowledge_base_id,
                            "normalized": entity.normalized_name,
                            "name": entity.name,
                            "type": entity.entity_type,
                            "aliases": json.dumps(entity.aliases),
                        },
                    )
                for unit in batch.units:
                    self._connection.execute(
                        "MATCH (s:Source {id: $source}) CREATE (u:KnowledgeUnit {id: $id, "
                        "source_id: $source, text: $text, page_number: $page, "
                        "section_path: $section, "
                        "sheet_name: $sheet, cell_range: $range}) CREATE (s)-[:CONTAINS]->(u)",
                        {
                            "source": batch.source_id,
                            "id": _unit_node_id(batch.source_id, unit.unit_id),
                            "text": unit.text,
                            "page": unit.page_number,
                            "section": json.dumps(unit.section_path),
                            "sheet": unit.sheet_name,
                            "range": unit.cell_range,
                        },
                    )
                for entity in batch.entities:
                    self._connection.execute(
                        "MATCH (u:KnowledgeUnit {id: $unit}), (e:Entity {id: $entity}) "
                        "CREATE (u)-[:MENTIONS {evidence_unit_id: $evidence_unit}]->(e)",
                        {
                            "unit": _unit_node_id(
                                batch.source_id, entity.evidence_unit_id
                            ),
                            "evidence_unit": entity.evidence_unit_id,
                            "entity": entities[entity.normalized_name],
                        },
                    )
                for relation in batch.relations:
                    self._connection.execute(
                        "MATCH (a:Entity {id: $subject}), (b:Entity {id: $object}) "
                        "CREATE (a)-[:RELATED_TO {predicate: $predicate, evidence_unit_id: $unit, "
                        "source_id: $source}]->(b)",
                        {
                            "subject": entities[relation.subject_normalized_name],
                            "object": entities[relation.object_normalized_name],
                            "predicate": relation.predicate,
                            "unit": relation.evidence_unit_id,
                            "source": batch.source_id,
                        },
                    )
                self._connection.execute("COMMIT")
            except Exception as exc:
                try:
                    self._connection.execute("ROLLBACK")
                except Exception as rollback_exc:
                    # Kuzu can automatically end a failed transaction after a constraint error.
                    # Preserve the original persistence failure instead of replacing it with
                    # "No active transaction for ROLLBACK".
                    exc.add_note(f"Graph rollback also failed: {rollback_exc}")
                raise

    def delete_source(self, tenant_id: str, source_id: str) -> None:
        with self._lock:
            self._delete_source(tenant_id, source_id)

    def _delete_source(self, tenant_id: str, source_id: str) -> None:
        # Tenant predicate prevents an internal caller from deleting another tenant's source.
        self._connection.execute(
            "MATCH (s:Source {id: $source, tenant_id: $tenant})-[:CONTAINS]->(u:KnowledgeUnit) "
            "DETACH DELETE u",
            {"source": source_id, "tenant": tenant_id},
        )
        self._connection.execute(
            "MATCH (s:Source {id: $source, tenant_id: $tenant}) DETACH DELETE s",
            {"source": source_id, "tenant": tenant_id},
        )

    def search(self, request: GraphSearchQuery) -> list[GraphSearchResult]:
        tokens = sorted(set(re.findall(r"[\w-]{2,}", request.query.casefold())))
        if not tokens:
            return []
        with self._lock:
            result: Any = self._connection.execute(
                "MATCH (s:Source)-[:CONTAINS]->(u:KnowledgeUnit)"
                "-[m:MENTIONS]->(e:Entity) "
                "WHERE e.tenant_id=$tenant AND e.knowledge_base_id=$kb "
                "RETURN e.normalized_name, e.aliases, m.evidence_unit_id, u.source_id, "
                "s.name, u.text, "
                "u.page_number, u.section_path, u.sheet_name, u.cell_range",
                {"tenant": request.tenant_id, "kb": request.knowledge_base_id},
            )
            rows: list[GraphSearchResult] = []
            while result.has_next():
                row = result.get_next()
                haystack = f"{row[0]} {row[1]}".casefold()
                score = sum(token in haystack for token in tokens)
                if score:
                    rows.append(
                        GraphSearchResult(
                            entity=str(row[0]),
                            unit_id=str(row[2]),
                            document_id=str(row[3]),
                            source_name=str(row[4]),
                            text=str(row[5]),
                            page_number=int(row[6]) if row[6] is not None else None,
                            section_path=tuple(json.loads(row[7] or "[]")),
                            sheet_name=str(row[8]) if row[8] is not None else None,
                            cell_range=str(row[9]) if row[9] is not None else None,
                            chunk_index=0,
                            score=float(score),
                        )
                    )
            return sorted(rows, key=lambda item: (-item.score, item.unit_id))[: request.limit]


class GraphServiceClient:
    def __init__(self, settings: GraphConfig):
        self._url = settings.GRAPH_SERVICE_URL.rstrip("/")
        self._token = settings.GRAPH_INTERNAL_TOKEN
        self._timeout = settings.GRAPH_TIMEOUT_SECONDS

    async def replace_source(self, batch: GraphBatch) -> None:
        await self._request(
            "PUT",
            f"/internal/v1/sources/{batch.source_id}",
            {
                "tenant_id": batch.tenant_id,
                "knowledge_base_id": batch.knowledge_base_id,
                "source_id": batch.source_id,
                "source_name": batch.source_name,
                "units": [
                    {
                        "unit_id": unit.unit_id,
                        "text": unit.text,
                        "page_number": unit.page_number,
                        "section_path": list(unit.section_path),
                        "sheet_name": unit.sheet_name,
                        "cell_range": unit.cell_range,
                    }
                    for unit in batch.units
                ],
                "entities": [asdict(item) for item in batch.entities],
                "relations": [asdict(item) for item in batch.relations],
                "extraction_version": batch.extraction_version,
            },
        )

    async def delete_source(self, tenant_id: str, source_id: str) -> None:
        await self._request("DELETE", f"/internal/v1/sources/{source_id}", {"tenant_id": tenant_id})

    async def search(self, request: GraphSearchQuery) -> list[GraphSearchResult]:
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.post(
                    f"{self._url}/internal/v1/search",
                    json=asdict(request),
                    headers={"X-Graph-Token": self._token},
                )
                response.raise_for_status()
                return [GraphSearchResult(**item) for item in response.json().get("results", [])]
        except httpx.HTTPError as exc:
            raise GraphUnavailableError("Graph service is unavailable") from exc

    async def _request(self, method: str, path: str, payload: dict[str, Any]) -> None:
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.request(
                    method,
                    f"{self._url}{path}",
                    json=payload,
                    headers={"X-Graph-Token": self._token},
                )
                response.raise_for_status()
        except httpx.HTTPStatusError as exc:
            if 400 <= exc.response.status_code < 500:
                raise GraphRejectedError("Graph service rejected grounded extraction") from exc
            raise GraphUnavailableError(
                f"Graph service request failed with HTTP {exc.response.status_code}"
            ) from exc
        except httpx.HTTPError as exc:
            raise GraphUnavailableError("Graph service is unavailable") from exc


def _entity_id(tenant_id: str, knowledge_base_id: str, normalized_name: str) -> str:
    return hashlib.sha256(
        f"{tenant_id}:{knowledge_base_id}:{normalized_name}".encode()
    ).hexdigest()[:32]


def _unit_node_id(source_id: str, unit_id: str) -> str:
    """Return the source-scoped identity used only for the internal Kuzu node key."""
    identity = json.dumps([source_id, unit_id], ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(identity.encode("utf-8")).hexdigest()[:32]


_EXTRACTION_PROMPT = """You extract only facts explicitly supported by the supplied knowledge units.
Return strict JSON: {"entities":[{"name":"","normalized_name":"","entity_type":"",
"aliases":[],"evidence_unit_id":""}],"relations":[{"subject_normalized_name":"",
"predicate":"","object_normalized_name":"","evidence_unit_id":""}]}.
Every item must cite one supplied unit_id. Do not infer unsupported facts.
Every relation subject and object must exactly match an entity normalized_name in the response.
Use empty arrays when none."""
