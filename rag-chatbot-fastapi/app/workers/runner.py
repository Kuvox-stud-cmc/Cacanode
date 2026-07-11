import argparse
import asyncio
import signal

from app.core.config import settings
from app.workers.manager import SUPPORTED_WORKERS, WorkerManager


async def run(kind: str) -> None:
    manager = WorkerManager(settings, kinds=(kind,))
    stopped = asyncio.Event()
    loop = asyncio.get_running_loop()
    for signum in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(signum, stopped.set)
        except NotImplementedError:
            pass
    await manager.start()
    try:
        await stopped.wait()
    finally:
        await manager.stop()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=SUPPORTED_WORKERS)
    args = parser.parse_args()
    asyncio.run(run(args.kind))


if __name__ == "__main__":
    main()
