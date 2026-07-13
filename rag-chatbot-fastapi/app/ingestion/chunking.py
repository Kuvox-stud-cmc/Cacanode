from __future__ import annotations

import hashlib
from dataclasses import dataclass

from app.ingestion.extraction import ExtractedPage, KnowledgeBlock, ParsedDocument


@dataclass(frozen=True, slots=True)
class TextChunk:
    page_number: int | None
    chunk_index: int
    text: str
    content_hash: str
    unit_id: str = ""
    modality: str = "document"
    section_path: tuple[str, ...] = ()
    block_type: str = "paragraph"
    heading_context: str | None = None
    sheet_name: str | None = None
    cell_range: str | None = None
    table_id: str | None = None
    source_start: int | None = None
    source_end: int | None = None
    parser_version: str = "digital-v1"
    chunker_version: str = "structural-v1"


class DeterministicChunker:
    def __init__(self, chunk_size: int = 800, overlap: int = 120):
        if chunk_size <= 0:
            raise ValueError("chunk_size must be positive")
        if overlap < 0 or overlap >= chunk_size:
            raise ValueError("overlap must be non-negative and smaller than chunk_size")
        self._chunk_size = chunk_size
        self._overlap = overlap

    def chunk(self, source: ParsedDocument | list[ExtractedPage]) -> list[TextChunk]:
        if isinstance(source, ParsedDocument):
            return self._chunk_blocks(source)
        blocks = tuple(
            KnowledgeBlock(
                unit_id=hashlib.sha256(f"page:{page.page_number}".encode()).hexdigest()[:32],
                block_type="page",
                text=page.text,
                page_number=page.page_number,
                content_hash=hashlib.sha256(page.text.encode()).hexdigest(),
            )
            for page in source
        )
        return self._chunk_blocks(ParsedDocument("document", blocks))

    def _chunk_blocks(self, document: ParsedDocument) -> list[TextChunk]:
        chunks: list[TextChunk] = []
        for block in document.blocks:
            pieces = self._split_block(block.text)
            for piece_index, text in enumerate(pieces):
                unit_id = block.unit_id if len(pieces) == 1 else f"{block.unit_id}:{piece_index}"
                chunks.append(
                    TextChunk(
                        page_number=block.page_number,
                        chunk_index=len(chunks),
                        text=text,
                        content_hash=hashlib.sha256(text.encode("utf-8")).hexdigest(),
                        unit_id=unit_id,
                        modality=document.modality,
                        section_path=block.section_path,
                        block_type=block.block_type,
                        heading_context=block.heading_context,
                        sheet_name=block.sheet_name,
                        cell_range=block.cell_range,
                        table_id=block.table_id,
                        source_start=block.source_start,
                        source_end=block.source_end,
                        parser_version=block.parser_version,
                    )
                )
        return chunks

    def _split_block(self, text: str) -> list[str]:
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
                boundary = max(
                    normalized.rfind(". ", start, hard_end),
                    normalized.rfind(" ", start, hard_end + 1),
                )
                if boundary > start:
                    end = boundary + (1 if normalized[boundary] == "." else 0)
            value = normalized[start:end].strip()
            if value:
                result.append(value)
            if end >= len(normalized):
                break
            start = max(end - self._overlap, start + 1)
            while start < len(normalized) and normalized[start] == " ":
                start += 1
        return result
