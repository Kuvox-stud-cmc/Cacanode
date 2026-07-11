from dataclasses import dataclass, field
from typing import Any, Literal

Modality = Literal["text", "table", "image", "audio", "video_segment", "ocr_text", "transcript"]


@dataclass(frozen=True, slots=True)
class KnowledgeUnit:
    tenant_id: str
    knowledge_base_id: str
    source_id: str
    unit_id: str
    modality: Modality
    text: str | None = None
    storage_uri: str | None = None
    page_number: int | None = None
    section_path: str | None = None
    sheet_name: str | None = None
    cell_range: str | None = None
    start_ms: int | None = None
    end_ms: int | None = None
    content_hash: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)
