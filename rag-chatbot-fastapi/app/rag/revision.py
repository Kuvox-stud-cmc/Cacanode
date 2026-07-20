from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True, slots=True)
class SuppliedRevision:
    tenant_id: str
    knowledge_base_id: str
    revision: int


_current_revision: ContextVar[SuppliedRevision | None] = ContextVar(
    "cacanode_authoritative_revision", default=None
)


class KnowledgeBaseRevisionStore(Protocol):
    async def current_revision(self, tenant_id: str, knowledge_base_id: str) -> int: ...


class SuppliedKnowledgeBaseRevisionStore:
    async def current_revision(self, tenant_id: str, knowledge_base_id: str) -> int:
        supplied = _current_revision.get()
        if supplied is None:
            raise LookupError("Spring did not supply a knowledge-base revision")
        if supplied.tenant_id != tenant_id or supplied.knowledge_base_id != knowledge_base_id:
            raise LookupError("Supplied revision does not match the generation scope")
        if supplied.revision < 0:
            raise ValueError("Knowledge-base revision must not be negative")
        return supplied.revision


@contextmanager
def authoritative_revision(tenant_id: str, knowledge_base_id: str, revision: int) -> Iterator[None]:
    token = _current_revision.set(SuppliedRevision(tenant_id, knowledge_base_id, revision))
    try:
        yield
    finally:
        _current_revision.reset(token)
