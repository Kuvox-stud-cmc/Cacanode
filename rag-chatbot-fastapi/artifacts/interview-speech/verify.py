from __future__ import annotations

import asyncio
import json
import os
import time
from pathlib import Path
from statistics import median

from app.bootstrap.configuration import model_config
from app.bootstrap.settings import Settings
from app.modules.interview.internal.engine import InterviewModelEvaluator
from app.modules.model.api import AudioEncoding, AudioFrame
from app.modules.model.internal.cartesia_speech import (
    CartesiaStreamingSpeechToTextSession,
    CartesiaStreamingTextToSpeech,
    SdkCartesiaSpeechSocketFactory,
)
from app.modules.model.internal.chat import create_chat_model


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * fraction))]


async def verify() -> None:
    settings = Settings()
    required = (
        settings.CARTESIA_API_KEY,
        settings.CARTESIA_ENGLISH_VOICE_ID,
        settings.CARTESIA_VIETNAMESE_VOICE_ID,
    )
    if not all(required) or not settings.model_configured:
        raise SystemExit(
            "Credential-gated harness requires Cartesia and configured model credentials"
        )
    root = Path(__file__).resolve().parents[2] / "artifacts" / "interview-speech"
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    factory = SdkCartesiaSpeechSocketFactory(settings.CARTESIA_API_KEY)
    tts = CartesiaStreamingTextToSpeech(
        factory,
        english_voice_id=settings.CARTESIA_ENGLISH_VOICE_ID,
        vietnamese_voice_id=settings.CARTESIA_VIETNAMESE_VOICE_ID,
    )
    model_settings = settings.model_copy(
        update={"LLM_TEMPERATURE": 0, "LLM_TIMEOUT_SECONDS": 4, "LLM_MAX_OUTPUT_TOKENS": 384}
    )
    evaluator = InterviewModelEvaluator(
        create_chat_model(model_config(model_settings), enforce_reasoning_minimum=False),
        timeout_seconds=4,
        max_attempts=2,
    )
    latencies: dict[str, list[float]] = {"en-US": [], "vi-VN": [], "combined": []}
    for sample in manifest["samples"]:
        language = sample["languageTag"]
        audio = (root / sample["path"]).read_bytes()
        stt = CartesiaStreamingSpeechToTextSession(factory)
        await stt.start(language_tag=language)
        await stt.send(AudioFrame(audio, 0, 8000, 1, AudioEncoding.MULAW))
        speech_end = time.perf_counter()
        events = await stt.finish()
        await stt.close()
        transcript = " ".join(
            getattr(item, "text", "") for item in events if getattr(item, "is_final", False)
        ).strip()
        if not transcript:
            raise RuntimeError(f"No final transcript for {sample['path']}")
        action = await evaluator.evaluate(
            question={
                "prompt": "Describe a work example.",
                "competency": "communication",
                "rubric": "clear and relevant",
            },
            transcript=transcript,
            language_tag=language,
            english_screen=language == "en-US",
        )
        if action is None:
            raise RuntimeError(f"No valid action for {sample['path']}")
        outbound = bytearray()
        first_audio_latency: float | None = None
        acknowledgement = "Thank you." if language == "en-US" else "Cảm ơn."
        async for frame in tts.synthesize(acknowledgement, language_tag=language):
            if first_audio_latency is None:
                first_audio_latency = time.perf_counter() - speech_end
            outbound.extend(frame.data)
        if not outbound or first_audio_latency is None:
            raise RuntimeError(f"No outbound audio for {sample['path']}")
        latencies[language].append(first_audio_latency)
        latencies["combined"].append(first_audio_latency)
        del transcript, outbound
    thresholds = manifest["latencyThresholdSeconds"]
    for language, values in latencies.items():
        p50 = median(values)
        p95 = percentile(values, 0.95)
        if p50 >= thresholds["p50"] or p95 >= thresholds["p95"]:
            raise RuntimeError(
                f"Latency threshold failed for {language}: p50={p50:.3f}, p95={p95:.3f}"
            )
        print(json.dumps({"language": language, "count": len(values), "p50": p50, "p95": p95}))


if __name__ == "__main__":
    if os.getenv("INTERVIEW_CREDENTIAL_TESTS") != "1":
        raise SystemExit("Set INTERVIEW_CREDENTIAL_TESTS=1 to run provider release verification")
    asyncio.run(verify())
