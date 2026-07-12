from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO

from app.ingestion.errors import PermanentIngestionError


@dataclass(frozen=True, slots=True)
class ExtractedPage:
    page_number: int
    text: str


class DocumentTextExtractor:
    def extract(self, data: bytes, *, content_type: str, file_name: str) -> list[ExtractedPage]:
        normalized = content_type.lower().split(";", 1)[0].strip()
        lower_name = file_name.lower()
        if normalized == "text/plain" or lower_name.endswith(".txt"):
            return self._extract_txt(data)
        if normalized == "application/pdf" or lower_name.endswith(".pdf"):
            return self._extract_pdf(data)
        raise PermanentIngestionError(f"Unsupported document content type: {content_type}")

    def _extract_txt(self, data: bytes) -> list[ExtractedPage]:
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise PermanentIngestionError("TXT document is not valid UTF-8") from exc
        if not text.strip():
            raise PermanentIngestionError("Document contains no extractable text")
        return [ExtractedPage(page_number=1, text=text)]

    def _extract_pdf(self, data: bytes) -> list[ExtractedPage]:
        try:
            from pypdf import PdfReader
        except ImportError as exc:
            raise PermanentIngestionError(
                "PDF extraction dependency pypdf is not installed"
            ) from exc

        try:
            reader = PdfReader(BytesIO(data))
        except Exception as exc:
            raise PermanentIngestionError("PDF document could not be parsed") from exc

        pages: list[ExtractedPage] = []
        for index, page in enumerate(reader.pages, start=1):
            text = page.extract_text() or ""
            if text.strip():
                pages.append(ExtractedPage(page_number=index, text=text))

        if not pages:
            raise PermanentIngestionError("PDF document contains no extractable text")
        return pages
