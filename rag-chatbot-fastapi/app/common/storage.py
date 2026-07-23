from __future__ import annotations

import asyncio
from typing import Protocol

import boto3
from botocore import UNSIGNED
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError

from app.common.config import StorageConfig
from app.common.errors import StorageUnavailableError


class ObjectStorageReader(Protocol):
    async def download(self, storage_key: str) -> bytes: ...


class SeaweedS3DocumentStore:
    def __init__(self, settings: StorageConfig):
        self._bucket = settings.SEAWEEDFS_BUCKET
        has_credentials = bool(settings.SEAWEEDFS_ACCESS_KEY and settings.SEAWEEDFS_SECRET_KEY)
        self._client = boto3.client(
            "s3",
            endpoint_url=settings.SEAWEEDFS_S3_ENDPOINT,
            aws_access_key_id=settings.SEAWEEDFS_ACCESS_KEY or None,
            aws_secret_access_key=settings.SEAWEEDFS_SECRET_KEY or None,
            config=Config(
                signature_version=None if has_credentials else UNSIGNED,
                connect_timeout=settings.SEAWEEDFS_CONNECT_TIMEOUT_SECONDS,
                read_timeout=settings.SEAWEEDFS_READ_TIMEOUT_SECONDS,
                retries={"mode": "standard", "total_max_attempts": settings.SEAWEEDFS_MAX_ATTEMPTS},
                s3={"addressing_style": "path"},
            ),
        )

    async def download(self, storage_key: str) -> bytes:
        try:
            return await asyncio.to_thread(self._download_sync, storage_key)
        except (BotoCoreError, ClientError, OSError) as exc:
            raise StorageUnavailableError(
                f"Unable to download document from SeaweedFS: {exc}"
            ) from exc

    def _download_sync(self, storage_key: str) -> bytes:
        response = self._client.get_object(Bucket=self._bucket, Key=storage_key)
        body = response["Body"]
        try:
            return body.read()
        finally:
            close = getattr(body, "close", None)
            if close:
                close()
