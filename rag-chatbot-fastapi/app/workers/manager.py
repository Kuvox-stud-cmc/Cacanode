import asyncio
import logging
from dataclasses import dataclass

from app.core.config import Settings

logger = logging.getLogger(__name__)

SUPPORTED_WORKERS = ("document", "ocr", "asr", "vision", "audio", "video")


@dataclass(slots=True)
class WorkerState:
    kind: str
    running: bool = False
    capability: str = "scaffolded"


class WorkerManager:
    """Runs worker lifecycles without acknowledging unimplemented jobs."""

    def __init__(self, settings: Settings, kinds: tuple[str, ...] | None = None):
        selected = kinds or settings.worker_kinds
        unknown = sorted(set(selected) - set(SUPPORTED_WORKERS))
        if unknown:
            raise ValueError(f"Unsupported worker kinds: {', '.join(unknown)}")
        self._settings = settings
        self._states = {kind: WorkerState(kind=kind) for kind in selected}
        self._tasks: list[asyncio.Task[None]] = []
        self._stop = asyncio.Event()

    @property
    def states(self) -> dict[str, WorkerState]:
        return self._states

    async def start(self) -> None:
        if self._tasks:
            return
        self._stop.clear()
        for state in self._states.values():
            state.running = True
            self._tasks.append(asyncio.create_task(self._run(state), name=f"worker-{state.kind}"))

    async def stop(self) -> None:
        self._stop.set()
        if self._tasks:
            await asyncio.gather(*self._tasks, return_exceptions=True)
        self._tasks.clear()
        for state in self._states.values():
            state.running = False

    async def _run(self, state: WorkerState) -> None:
        logger.info("Worker %s started in scaffold mode", state.kind)
        while not self._stop.is_set():
            try:
                await asyncio.wait_for(
                    self._stop.wait(),
                    timeout=self._settings.WORKER_POLL_INTERVAL_SECONDS,
                )
            except TimeoutError:
                continue
        logger.info("Worker %s stopped", state.kind)
