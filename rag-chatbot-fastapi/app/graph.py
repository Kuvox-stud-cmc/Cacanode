from __future__ import annotations

import json
import re
from collections.abc import Sequence
from pathlib import Path
from threading import RLock
from typing import Any

import httpx
import kuzu
from pydantic import BaseModel, Field, field_validator, model_validator

from app.core.config import Settings
from app.ingestion.chunking import TextChunk
from app.ingestion.errors import PermanentIngestionError, TransientIngestionError
from app.ingestion.events import DocumentIngestRequestedEvent


class EntityMention(BaseModel):
    name: str = Field(min_length=1, max_length=300)
    normalized_name: str = Field(min_length=1, max_length=300)
    entity_type: str = Field(min_length=1, max_length=80)
    aliases: tuple[str, ...] = ()
    evidence_unit_id: str

    @field_validator("normalized_name")
    @classmethod
    def normalize_name(cls, value: str) -> str:
        return " ".join(value.casefold().split())


class EvidenceRelation(BaseModel):
    subject_normalized_name: str
    predicate: str = Field(min_length=1, max_length=120)
    object_normalized_name: str
    evidence_unit_id: str

    @model_validator(mode="after")
    def normalize_entities(self) -> EvidenceRelation:
        self.subject_normalized_name = " ".join(self.subject_normalized_name.casefold().split())
        self.object_normalized_name = " ".join(self.object_normalized_name.casefold().split())
        return self


class GraphBatch(BaseModel):
    tenant_id: str
    knowledge_base_id: str
    source_id: str
    source_name: str
    units: tuple[dict[str, Any], ...]
    entities: tuple[EntityMention, ...] = ()
    relations: tuple[EvidenceRelation, ...] = ()
    extraction_version: str = "entity-relations-v1"

    @model_validator(mode="after")
    def validate_evidence(self) -> GraphBatch:
        unit_ids = {str(unit.get("unit_id")) for unit in self.units}
        entities = {entity.normalized_name for entity in self.entities}
        if any(entity.evidence_unit_id not in unit_ids for entity in self.entities):
            raise ValueError("Entity mention references an unknown evidence unit")
        if any(
            relation.evidence_unit_id not in unit_ids
            or relation.subject_normalized_name not in entities
            or relation.object_normalized_name not in entities
            for relation in self.relations
        ):
            raise ValueError("Relation is not grounded in known entities and evidence")
        return self


class GraphSearchRequest(BaseModel):
    tenant_id: str
    knowledge_base_id: str
    query: str = Field(min_length=1, max_length=2000)
    limit: int = Field(default=10, ge=1, le=100)


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
                            "id": str(unit["unit_id"]),
                            "text": str(unit.get("text", "")),
                            "page": unit.get("page_number"),
                            "section": json.dumps(unit.get("section_path", [])),
                            "sheet": unit.get("sheet_name"),
                            "range": unit.get("cell_range"),
                        },
                    )
                for entity in batch.entities:
                    self._connection.execute(
                        "MATCH (u:KnowledgeUnit {id: $unit}), (e:Entity {id: $entity}) "
                        "CREATE (u)-[:MENTIONS {evidence_unit_id: $unit}]->(e)",
                        {
                            "unit": entity.evidence_unit_id,
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
            except Exception:
                self._connection.execute("ROLLBACK")
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

    def search(self, request: GraphSearchRequest) -> list[dict[str, Any]]:
        tokens = sorted(set(re.findall(r"[\w-]{2,}", request.query.casefold())))
        if not tokens:
            return []
        with self._lock:
            result: Any = self._connection.execute(
                "MATCH (s:Source)-[:CONTAINS]->(u:KnowledgeUnit)-[:MENTIONS]->(e:Entity) "
                "WHERE e.tenant_id=$tenant AND e.knowledge_base_id=$kb "
                "RETURN e.normalized_name, e.aliases, u.id, u.source_id, s.name, u.text, "
                "u.page_number, u.section_path, u.sheet_name, u.cell_range",
                {"tenant": request.tenant_id, "kb": request.knowledge_base_id},
            )
            rows: list[dict[str, Any]] = []
            while result.has_next():
                row = result.get_next()
                haystack = f"{row[0]} {row[1]}".casefold()
                score = sum(token in haystack for token in tokens)
                if score:
                    rows.append(
                        {
                            "entity": row[0],
                            "unit_id": row[2],
                            "document_id": row[3],
                            "source_name": row[4],
                            "text": row[5],
                            "page_number": row[6],
                            "section_path": json.loads(row[7] or "[]"),
                            "sheet_name": row[8],
                            "cell_range": row[9],
                            "score": score,
                        }
                    )
            return sorted(rows, key=lambda item: (-item["score"], item["unit_id"]))[: request.limit]


class GraphServiceClient:
    def __init__(self, settings: Settings):
        self._url = settings.GRAPH_SERVICE_URL.rstrip("/")
        self._token = settings.GRAPH_INTERNAL_TOKEN
        self._timeout = settings.GRAPH_TIMEOUT_SECONDS

    async def replace_source(self, batch: GraphBatch) -> None:
        await self._request("PUT", f"/internal/v1/sources/{batch.source_id}", batch.model_dump())

    async def delete_source(self, tenant_id: str, source_id: str) -> None:
        await self._request("DELETE", f"/internal/v1/sources/{source_id}", {"tenant_id": tenant_id})

    async def search(self, request: GraphSearchRequest) -> list[dict[str, Any]]:
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.post(
                    f"{self._url}/internal/v1/search",
                    json=request.model_dump(),
                    headers={"X-Graph-Token": self._token},
                )
                response.raise_for_status()
                return list(response.json().get("results", []))
        except httpx.HTTPError as exc:
            raise TransientIngestionError("Graph service is unavailable") from exc

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
                raise PermanentIngestionError("Graph service rejected grounded extraction") from exc
            raise TransientIngestionError("Graph service is unavailable") from exc
        except httpx.HTTPError as exc:
            raise TransientIngestionError("Graph service is unavailable") from exc


class EntityRelationExtractor:
    def __init__(self, model: Any, *, batch_size: int = 12):
        self._model = model
        self._batch_size = batch_size

    async def extract(
        self, event: DocumentIngestRequestedEvent, chunks: Sequence[TextChunk]
    ) -> GraphBatch:
        units = tuple(_unit_payload(chunk) for chunk in chunks)
        entities: list[EntityMention] = []
        relations: list[EvidenceRelation] = []
        for start in range(0, len(chunks), self._batch_size):
            selected = chunks[start : start + self._batch_size]
            raw = await self._model.complete(
                [
                    {"role": "system", "content": _EXTRACTION_PROMPT},
                    {
                        "role": "user",
                        "content": json.dumps(
                            [_unit_payload(item) for item in selected], ensure_ascii=False
                        ),
                    },
                ]
            )
            try:
                payload = json.loads(re.sub(r"^```(?:json)?\s*|\s*```$", "", raw.strip()))
                entities.extend(
                    EntityMention.model_validate(item) for item in payload.get("entities", [])
                )
                relations.extend(
                    EvidenceRelation.model_validate(item) for item in payload.get("relations", [])
                )
            except (ValueError, TypeError, json.JSONDecodeError) as exc:
                raise PermanentIngestionError(
                    "Entity extraction returned invalid structured JSON"
                ) from exc
        return GraphBatch(
            tenant_id=str(event.tenant_id),
            knowledge_base_id=str(event.knowledge_base_id),
            source_id=str(event.document_id),
            source_name=event.file_name,
            units=units,
            entities=tuple(entities),
            relations=tuple(relations),
        )


def _unit_payload(chunk: TextChunk) -> dict[str, Any]:
    return {
        "unit_id": chunk.unit_id,
        "text": chunk.text,
        "page_number": chunk.page_number,
        "section_path": list(chunk.section_path),
        "sheet_name": chunk.sheet_name,
        "cell_range": chunk.cell_range,
    }


def _entity_id(tenant_id: str, knowledge_base_id: str, normalized_name: str) -> str:
    import hashlib

    return hashlib.sha256(
        f"{tenant_id}:{knowledge_base_id}:{normalized_name}".encode()
    ).hexdigest()[:32]


_EXTRACTION_PROMPT = """You extract only facts explicitly supported by the supplied knowledge units.
Return strict JSON: {"entities":[{"name":"","normalized_name":"","entity_type":"",
"aliases":[],"evidence_unit_id":""}],"relations":[{"subject_normalized_name":"",
"predicate":"","object_normalized_name":"","evidence_unit_id":""}]}.
Every item must cite one supplied unit_id. Do not infer unsupported facts.
Use empty arrays when none."""
