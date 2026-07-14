from __future__ import annotations

import asyncio
import json
import logging
from typing import Any

import aio_pika
from aio_pika.abc import AbstractIncomingMessage

from app.core.config import Settings
from app.ingestion.errors import PermanentIngestionError, TransientIngestionError
from app.ingestion.events import DocumentIngestRequestedEvent, partial_status_ids, status_event
from app.ingestion.factory import create_document_ingestion_pipeline
from app.ingestion.pipeline import DocumentIngestionPipeline

logger = logging.getLogger(__name__)

INGESTION_EXCHANGE = "cacanode.ingestion.v1"
DEAD_LETTER_EXCHANGE = "cacanode.dlx.v1"
INGESTION_QUEUE = "cacanode.document.ingestion.v1"
INGEST_REQUESTED = "document.ingest.requested"
INGEST_PROCESSING = "document.ingest.processing"
INGEST_COMPLETED = "document.ingest.completed"
INGEST_FAILED = "document.ingest.failed"
MAX_TRANSIENT_RETRIES = 3
RETRY_HEADER = "x-retry-count"


class DocumentWorker:
    def __init__(
        self,
        settings: Settings,
        pipeline: DocumentIngestionPipeline | None = None,
        connection: Any | None = None,
    ):
        self._settings = settings
        self._pipeline = pipeline or create_document_ingestion_pipeline(settings)
        self._connection: Any | None = connection
        self._owns_connection = connection is None
        self._channel: Any | None = None
        self._exchange: Any | None = None
        self._queue: Any | None = None

    async def run(self, stop: asyncio.Event) -> None:
        await self.start()
        try:
            assert self._queue is not None
            consumer_tag = await self._queue.consume(self.handle_message, no_ack=False)
            await stop.wait()
            await self._queue.cancel(consumer_tag)
        finally:
            await self.stop()

    async def start(self) -> None:
        if self._connection is None:
            self._connection = await aio_pika.connect_robust(self._settings.RABBITMQ_URL)
        connection = self._connection
        channel = await connection.channel()
        self._channel = channel
        await channel.set_qos(prefetch_count=1)
        self._exchange = await channel.declare_exchange(
            INGESTION_EXCHANGE,
            aio_pika.ExchangeType.TOPIC,
            durable=True,
        )
        await channel.declare_exchange(
            DEAD_LETTER_EXCHANGE,
            aio_pika.ExchangeType.TOPIC,
            durable=True,
        )
        self._queue = await channel.declare_queue(
            INGESTION_QUEUE,
            durable=True,
            arguments={"x-dead-letter-exchange": DEAD_LETTER_EXCHANGE},
        )
        await self._queue.bind(self._exchange, routing_key=INGEST_REQUESTED)

    async def stop(self) -> None:
        if self._channel is not None:
            await self._channel.close()
            self._channel = None
        if self._connection is not None and self._owns_connection:
            await self._connection.close()
            self._connection = None

    async def handle_message(self, message: AbstractIncomingMessage) -> None:
        event: DocumentIngestRequestedEvent | None = None
        try:
            event = DocumentIngestRequestedEvent.parse_payload(message.body)
            await self.publish_status(event, "PROCESSING")
            chunk_count = await self._pipeline.ingest(event)
            await self.publish_status(event, "COMPLETED", chunk_count=chunk_count)
            await message.ack()
        except PermanentIngestionError as exc:
            logger.warning("Permanent document ingestion failure: %s", exc)
            try:
                await self._publish_failed_for(message.body, event, str(exc))
            except Exception as publish_exc:
                await self._retry_or_dead_letter(message, event, str(publish_exc))
            else:
                await message.reject(requeue=False)
        except TransientIngestionError as exc:
            logger.warning("Transient document ingestion failure: %s", exc)
            await self._retry_or_dead_letter(message, event, str(exc))
        except Exception as exc:
            logger.exception("Unexpected document ingestion failure")
            await self._retry_or_dead_letter(message, event, str(exc))

    async def publish_status(
        self,
        event: DocumentIngestRequestedEvent,
        status: str,
        *,
        chunk_count: int | None = None,
        error_message: str | None = None,
    ) -> None:
        payload = status_event(
            schema_version=event.schema_version,
            job_id=event.job_id,
            tenant_id=event.tenant_id,
            document_id=event.document_id,
            status=status,
            chunk_count=chunk_count,
            error_message=error_message,
        )
        routing_key = {
            "PROCESSING": INGEST_PROCESSING,
            "COMPLETED": INGEST_COMPLETED,
            "FAILED": INGEST_FAILED,
        }[status]
        await self.publish_json(routing_key, payload)

    async def publish_json(
        self,
        routing_key: str,
        payload: dict[str, Any],
        *,
        headers: dict[str, Any] | None = None,
    ) -> None:
        if self._exchange is None:
            raise TransientIngestionError("RabbitMQ exchange is not initialized")
        body = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
        await self._exchange.publish(
            aio_pika.Message(
                body,
                headers=headers,
                content_type="application/json",
                content_encoding="utf-8",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                message_id=str(payload.get("event_id") or ""),
                correlation_id=str(payload.get("job_id") or ""),
            ),
            routing_key=routing_key,
            mandatory=True,
        )

    async def _publish_failed_for(
        self,
        body: bytes,
        event: DocumentIngestRequestedEvent | None,
        error_message: str,
    ) -> None:
        if event is not None:
            await self.publish_status(event, "FAILED", error_message=error_message)
            return

        ids = partial_status_ids(body)
        await self.publish_json(
            INGEST_FAILED,
            status_event(
                schema_version=str(ids.get("schema_version") or "1.0"),
                job_id=ids.get("job_id"),
                tenant_id=ids.get("tenant_id"),
                document_id=ids.get("document_id"),
                status="FAILED",
                error_message=error_message,
            ),
        )

    async def _retry_or_dead_letter(
        self,
        message: AbstractIncomingMessage,
        event: DocumentIngestRequestedEvent | None,
        error_message: str,
    ) -> None:
        retry_count = self._retry_count(message)
        if retry_count < MAX_TRANSIENT_RETRIES:
            await self._republish_retry(message, retry_count + 1)
            await message.ack()
            return

        try:
            await self._publish_failed_for(message.body, event, error_message)
        finally:
            await message.reject(requeue=False)

    async def _republish_retry(self, message: AbstractIncomingMessage, retry_count: int) -> None:
        if self._exchange is None:
            raise TransientIngestionError("RabbitMQ exchange is not initialized")
        headers = dict(message.headers or {})
        headers[RETRY_HEADER] = retry_count
        await self._exchange.publish(
            aio_pika.Message(
                message.body,
                headers=headers,
                content_type=message.content_type or "application/json",
                content_encoding=message.content_encoding or "utf-8",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                message_id=message.message_id,
                correlation_id=message.correlation_id,
            ),
            routing_key=INGEST_REQUESTED,
            mandatory=True,
        )

    def _retry_count(self, message: AbstractIncomingMessage) -> int:
        raw = (message.headers or {}).get(RETRY_HEADER, 0)
        if isinstance(raw, bytes | bytearray):
            raw = raw.decode("utf-8")
        if not isinstance(raw, int | float | str):
            return 0
        try:
            return int(raw)
        except (TypeError, ValueError):
            return 0
