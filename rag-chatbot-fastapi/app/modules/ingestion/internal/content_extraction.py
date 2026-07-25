from __future__ import annotations

import hashlib
import unicodedata

from app.modules.ingestion.api import (
    ContentExtractionCommand,
    ExtractedContent,
    PermanentIngestionFailure,
    SourceSegment,
)
from app.modules.ingestion.internal.extraction import DocumentTextExtractor


class DigitalContentExtractionAdapter:
    """Extraction-only adapter; it deliberately does not call ingestion or indexing."""

    def __init__(
        self,
        *,
        max_characters: int = 50_000,
        max_segments: int = 250,
        extractor: DocumentTextExtractor | None = None,
    ) -> None:
        self._max_characters = max_characters
        self._max_segments = max_segments
        self._extractor = extractor or DocumentTextExtractor()

    async def extract(self, command: ContentExtractionCommand) -> ExtractedContent:
        parsed = self._extractor.parse(
            command.file_bytes,
            content_type=command.content_type,
            file_name=command.file_name,
        )
        segments: list[SourceSegment] = []
        used = 0
        pages: set[int] = set()
        for ordinal, block in enumerate(parsed.blocks, start=1):
            if len(segments) >= self._max_segments or used >= self._max_characters:
                break
            text = _normalize(block.text)
            if not text:
                continue
            text = text[: self._max_characters - used]
            location = _location(block.page_number, block.section_path, ordinal)
            identity = hashlib.sha256(f"{ordinal}|{location}|{text}".encode()).hexdigest()[:24]
            segments.append(SourceSegment(identity, text, location))
            used += len(text)
            if block.page_number is not None:
                pages.add(block.page_number)
        if not segments:
            raise PermanentIngestionFailure("Document contains no extractable text")
        normalized = "\n\n".join(segment.text for segment in segments)
        return ExtractedContent(
            normalized_text=normalized,
            detected_content_type=command.content_type,
            character_count=len(normalized),
            page_count=max(pages) if pages else None,
            source_segments=tuple(segments),
        )


def _normalize(value: str) -> str:
    return unicodedata.normalize("NFC", value.replace("\r\n", "\n").replace("\r", "\n")).strip()


def _location(page: int | None, section_path: tuple[str, ...], ordinal: int) -> str:
    parts: list[str] = []
    if page is not None:
        parts.append(f"page {page}")
    if section_path:
        parts.append(" / ".join(section_path)[-80:])
    parts.append(f"segment {ordinal}")
    return ", ".join(parts)[:120]
