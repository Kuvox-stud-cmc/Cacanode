from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True, slots=True)
class InterviewDiagnostic:
    active_session_count: int
    recovery_due_count: int


class InterviewDiagnostics(Protocol):
    async def inspect(self) -> InterviewDiagnostic: ...
