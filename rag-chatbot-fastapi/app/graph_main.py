from __future__ import annotations

import asyncio
from functools import lru_cache

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel

from app.core.config import settings
from app.graph import GraphBatch, GraphSearchRequest, KuzuGraphRepository

app = FastAPI(title="Cacanode Graph Service", version="1.0.0")


@lru_cache
def repository() -> KuzuGraphRepository:
    return KuzuGraphRepository(settings.KUZU_DATABASE_PATH)


def authenticate(x_graph_token: str = Header(default="")) -> None:
    if not settings.GRAPH_INTERNAL_TOKEN or x_graph_token != settings.GRAPH_INTERNAL_TOKEN:
        raise HTTPException(status_code=401, detail="Invalid graph service credentials")


class DeleteSourceRequest(BaseModel):
    tenant_id: str


@app.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "live"}


@app.get("/health/ready")
async def ready() -> dict[str, str]:
    await asyncio.to_thread(repository)
    return {"status": "ready", "database_path": settings.KUZU_DATABASE_PATH}


@app.put("/internal/v1/sources/{source_id}", dependencies=[Depends(authenticate)])
async def replace_source(source_id: str, batch: GraphBatch) -> dict[str, str]:
    if batch.source_id != source_id:
        raise HTTPException(status_code=400, detail="Source identifier mismatch")
    await asyncio.to_thread(repository().replace_source, batch)
    return {"status": "replaced", "source_id": source_id}


@app.delete("/internal/v1/sources/{source_id}", dependencies=[Depends(authenticate)])
async def delete_source(source_id: str, request: DeleteSourceRequest) -> dict[str, str]:
    await asyncio.to_thread(repository().delete_source, request.tenant_id, source_id)
    return {"status": "deleted", "source_id": source_id}


@app.post("/internal/v1/search", dependencies=[Depends(authenticate)])
async def search(request: GraphSearchRequest) -> dict[str, object]:
    return {"results": await asyncio.to_thread(repository().search, request)}
