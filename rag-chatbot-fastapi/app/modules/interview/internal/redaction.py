from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

from app.modules.ingestion.api import SourceSegment


@dataclass(frozen=True, slots=True)
class RedactedAnchor:
    anchor_id: str
    text: str
    source_location: str


_INLINE_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("EMAIL", re.compile(r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b")),
    ("URL", re.compile(r"(?i)\b(?:https?://|www\.)\S+|(?<!\w)@[A-Za-z0-9_]{2,30}\b")),
    ("PHONE", re.compile(r"(?<!\w)(?:\+?\d[\d .()\-]{7,}\d)(?!\w)")),
    (
        "GOVERNMENT_ID",
        re.compile(
            r"(?i)\b(?:(?:cccd|cmnd|passport|hộ chiếu)\s*[:#-]?\s*"
            r"[A-Z0-9]{6,20}|id\s*[:#-]\s*[A-Z0-9]{6,20})\b"
        ),
    ),
    (
        "DATE_OF_BIRTH",
        re.compile(
            r"(?i)\b(?:dob|date of birth|ngày sinh|sinh ngày)\s*[:\-]?\s*"
            r"\d{1,2}[\-/]\d{1,2}[\-/]\d{2,4}\b"
        ),
    ),
    (
        "AGE",
        re.compile(
            r"(?i)\b(?:age|tuổi)\s*[:\-]?\s*\d{1,3}\b|"
            r"\b\d{1,3}\s*(?:years? old|tuổi)\b"
        ),
    ),
    (
        "ADDRESS",
        re.compile(
            r"(?i)\b\d{1,5}\s+[\wÀ-ỹ .'-]{2,80}\s+"
            r"(?:street|st\.?|road|rd\.?|avenue|ave\.?|đường|phố)\b"
        ),
    ),
)

_SENSITIVE_LABEL = re.compile(
    r"(?i)^\s*(?:address|địa chỉ|gender|giới tính|sex|marital status|tình trạng hôn nhân|"
    r"family|gia đình|pregnan(?:t|cy)|mang thai|nationality|quốc tịch|ethnicity|dân tộc|race|"
    r"religion|tôn giáo|disability|khuyết tật|medical|sức khỏe|health|photo|photograph|ảnh)\s*[:\-]"
)

_PROTECTED_TERMS = re.compile(
    r"(?i)\b(?:male|female|nam giới|nữ giới|married|single|đã kết hôn|độc thân|pregnan(?:t|cy)|"
    r"mang thai|vietnamese nationality|quốc tịch|ethnicity|dân tộc|race|religion|tôn giáo|"
    r"disabled|disability|khuyết tật|medical condition|bệnh|date of birth|ngày sinh|"
    r"years? old|tuổi|photo|photograph|ảnh|family status|tình trạng gia đình)\b"
)


def redact_segments(segments: tuple[SourceSegment, ...]) -> tuple[RedactedAnchor, ...]:
    anchors: list[RedactedAnchor] = []
    for segment in segments:
        value = unicodedata.normalize("NFC", segment.text)
        lines: list[str] = []
        for line in value.splitlines():
            if _SENSITIVE_LABEL.search(line):
                lines.append("[REDACTED:PROTECTED_ATTRIBUTE]")
                continue
            redacted = line
            for label, pattern in _INLINE_PATTERNS:
                redacted = pattern.sub(f"[REDACTED:{label}]", redacted)
            redacted = _PROTECTED_TERMS.sub(
                "[REDACTED:PROTECTED_ATTRIBUTE]", redacted
            )
            lines.append(redacted)
        text = "\n".join(lines).strip()
        if text:
            anchors.append(RedactedAnchor(f"a-{segment.segment_id}", text, segment.source_location))
    return tuple(anchors)


def contains_protected_data(value: str) -> bool:
    if _PROTECTED_TERMS.search(value):
        return True
    return any(pattern.search(value) for _, pattern in _INLINE_PATTERNS)
