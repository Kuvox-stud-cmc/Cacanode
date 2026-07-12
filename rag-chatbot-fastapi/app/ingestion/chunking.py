from __future__ import annotations

import hashlib
from dataclasses import dataclass

from app.ingestion.extraction import ExtractedPage


@dataclass(frozen=True, slots=True)
class TextChunk:
    page_number: int
    chunk_index: int
    text: str
    content_hash: str


class DeterministicChunker:
    def __init__(self, chunk_size: int = 800, overlap: int = 120):
        if chunk_size <= 0:
            raise ValueError("chunk_size must be positive")
        if overlap < 0 or overlap >= chunk_size:
            raise ValueError("overlap must be non-negative and smaller than chunk_size")
        self._chunk_size = chunk_size
        self._overlap = overlap

    def chunk(self, pages: list[ExtractedPage]) -> list[TextChunk]:
        chunks: list[TextChunk] = []
        for page in pages:
            for text in self._split_page(page.text):
                chunk_index = len(chunks)
                chunks.append(
                    TextChunk(
                        page_number=page.page_number,
                        chunk_index=chunk_index,
                        text=text,
                        content_hash=hashlib.sha256(text.encode("utf-8")).hexdigest(),
                    )
                )
        return chunks

    def _split_page(self, text: str) -> list[str]:
        normalized = " ".join(text.split())
        if not normalized:
            return []
        if len(normalized) <= self._chunk_size:
            return [normalized]

        result: list[str] = []
        start = 0
        while start < len(normalized):
            hard_end = min(start + self._chunk_size, len(normalized))
            end = hard_end
            if hard_end < len(normalized):
                space = normalized.rfind(" ", start, hard_end + 1)
                if space > start:
                    end = space
            chunk = normalized[start:end].strip()
            if chunk:
                result.append(chunk)
            if end >= len(normalized):
                break
            next_start = max(end - self._overlap, start + 1)
            while next_start < len(normalized) and normalized[next_start] == " ":
                next_start += 1
            start = next_start
        return result
