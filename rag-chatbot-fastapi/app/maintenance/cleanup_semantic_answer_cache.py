from __future__ import annotations

import argparse
import asyncio
import json
from collections.abc import Sequence

from qdrant_client import AsyncQdrantClient

from app.core.config import Settings
from app.rag.semantic_answer_cache import cleanup_expired_semantic_points


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Bounded cleanup for expired semantic answer-cache Qdrant points."
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Delete expired points. Without this flag the command is a dry run.",
    )
    parser.add_argument("--max-batches", type=int, default=10)
    return parser


async def run(settings: Settings, *, apply: bool, max_batches: int) -> dict[str, int | bool]:
    client = AsyncQdrantClient(
        url=settings.QDRANT_URL,
        api_key=settings.QDRANT_API_KEY or None,
        check_compatibility=False,
    )
    try:
        return await cleanup_expired_semantic_points(
            client,
            collection=settings.SEMANTIC_ANSWER_CACHE_COLLECTION,
            batch_size=settings.SEMANTIC_ANSWER_CACHE_CLEANUP_BATCH_SIZE,
            max_batches=min(max(max_batches, 1), 10),
            apply=apply,
        )
    finally:
        await client.close()


def main(argv: Sequence[str] | None = None) -> None:
    args = build_parser().parse_args(argv)
    summary = asyncio.run(
        run(Settings(), apply=bool(args.apply), max_batches=int(args.max_batches))
    )
    print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
