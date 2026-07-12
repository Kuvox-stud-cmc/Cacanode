from __future__ import annotations

import asyncio

import boto3
from botocore.exceptions import BotoCoreError, ClientError

from app.core.config import Settings
from app.ingestion.errors import TransientIngestionError


class SeaweedS3DocumentStore:
    def __init__(self, settings: Settings):
        self._bucket = settings.SEAWEEDFS_BUCKET
        self._client = boto3.client(
            "s3",
            endpoint_url=settings.SEAWEEDFS_S3_ENDPOINT,
            aws_access_key_id=settings.SEAWEEDFS_ACCESS_KEY or None,
            aws_secret_access_key=settings.SEAWEEDFS_SECRET_KEY or None,
        )

    async def download(self, storage_key: str) -> bytes:
        try:
            return await asyncio.to_thread(self._download_sync, storage_key)
        except (BotoCoreError, ClientError, OSError) as exc:
            raise TransientIngestionError(
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
