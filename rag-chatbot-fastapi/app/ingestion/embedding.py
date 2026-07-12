from __future__ import annotations

from collections.abc import Sequence
from typing import Any

import httpx

from app.core.config import Settings
from app.ingestion.errors import PermanentIngestionError, TransientIngestionError


class OllamaEmbeddingClient:
    def __init__(self, settings: Settings):
        self._base_url = settings.TEXT_EMBEDDING_BASE_URL.rstrip("/")
        self._model = settings.TEXT_EMBEDDING_MODEL_ID
        self._batch_size = settings.TEXT_EMBEDDING_BATCH_SIZE
        self._expected_dimension = settings.TEXT_EMBEDDING_DIMENSION

    async def embed_documents(self, texts: Sequence[str]) -> list[list[float]]:
        embeddings: list[list[float]] = []
        for start in range(0, len(texts), self._batch_size):
            batch = texts[start : start + self._batch_size]
            embeddings.extend(await self._embed_batch(batch))
        return embeddings

    async def embed_query(self, text: str) -> list[float]:
        embeddings = await self._embed_batch([text])
        return embeddings[0]

    async def _embed_batch(self, texts: Sequence[str]) -> list[list[float]]:
        try:
            async with httpx.AsyncClient(timeout=60) as client:
                response = await client.post(
                    f"{self._base_url}/api/embed",
                    json={"model": self._model, "input": list(texts)},
                )
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPStatusError as exc:
            raise TransientIngestionError(
                f"Ollama embedding request failed with status {exc.response.status_code}"
            ) from exc
        except (httpx.HTTPError, ValueError) as exc:
            raise TransientIngestionError(f"Ollama embedding request failed: {exc}") from exc

        if "error" in payload:
            raise TransientIngestionError(f"Ollama embedding model error: {payload['error']}")

        parsed = self._parse_embeddings(payload)
        if len(parsed) != len(texts):
            raise TransientIngestionError("Ollama returned an unexpected embedding count")
        for vector in parsed:
            if len(vector) != self._expected_dimension:
                raise PermanentIngestionError(
                    "Ollama returned embedding dimension "
                    f"{len(vector)} but expected {self._expected_dimension}"
                )
        return parsed

    def _parse_embeddings(self, payload: dict[str, Any]) -> list[list[float]]:
        raw = payload.get("embeddings")
        if raw is None and "embedding" in payload:
            raw = [payload["embedding"]]
        if not isinstance(raw, list):
            raise TransientIngestionError("Ollama response did not include embeddings")
        parsed: list[list[float]] = []
        for item in raw:
            if not isinstance(item, list):
                raise TransientIngestionError("Ollama embedding item is malformed")
            parsed.append([float(value) for value in item])
        return parsed
