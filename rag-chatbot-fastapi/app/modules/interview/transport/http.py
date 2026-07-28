from __future__ import annotations

from typing import Protocol

from fastapi import APIRouter, WebSocket


class InterviewHttpSettings(Protocol):
    INTERVIEW_ENABLED: bool
    INTERVIEW_MEDIA_STREAM_ENABLED: bool


def interview_router(settings: InterviewHttpSettings) -> APIRouter:
    router = APIRouter()

    @router.websocket("/ws/v1/interviews/twilio/media")
    async def twilio_media(websocket: WebSocket) -> None:
        if not (settings.INTERVIEW_ENABLED and settings.INTERVIEW_MEDIA_STREAM_ENABLED):
            await websocket.accept()
            await websocket.close(code=1008, reason="INTERVIEW_DISABLED")
            return
        runtime = getattr(websocket.app.state, "interview_media_runtime", None)
        if runtime is None:
            await websocket.accept()
            await websocket.close(code=1013, reason="INTERVIEW_RUNTIME_NOT_READY")
            return
        await runtime.run(websocket)

    return router
