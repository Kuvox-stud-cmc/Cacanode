from __future__ import annotations

import json
from pathlib import Path
from uuid import UUID

from jsonschema import Draft202012Validator, FormatChecker

from app.contracts.ai_interview_v1 import (
    interview_event_id,
    interview_runtime_event_id,
    interview_runtime_turn_id,
    parse_interview_event,
)

ROOT = Path(__file__).resolve().parents[2]
CONTRACTS = ROOT / "contracts" / "ai-interview" / "v1"

SEMANTIC_KEYS = {
    "interview.resume-analysis.requested": "requested:v1",
    "interview.resume-analysis.outcome": "completed:v1",
    "interview.turn.finalized": "turn:1",
    "interview.session.completed": "completed:v1",
    "interview.session.failed": "failed:provider-timeout",
    "interview.provider.usage": "cartesia:tts:1",
    "recruitment.recording.ready": "recording:1",
}


def test_all_ai_interview_fixtures_validate_parse_and_use_uuidv5() -> None:
    fixtures = sorted(CONTRACTS.glob("*.fixture.json"))
    assert len(fixtures) == 17
    for fixture_path in fixtures:
        schema_path = fixture_path.with_name(
            fixture_path.name.replace(".fixture.json", ".schema.json")
        )
        payload = json.loads(fixture_path.read_text(encoding="utf-8"))
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        Draft202012Validator(schema, format_checker=FormatChecker()).validate(payload)
        event = parse_interview_event(fixture_path.read_bytes())
        semantic_key = SEMANTIC_KEYS[event.event_type]
        if event.schema_version in {"1.1", "1.2"}:
            if event.event_type == "interview.turn.finalized":
                semantic_key = f"turn:{payload['sequence']}:v{event.schema_version}"
            elif event.event_type == "interview.provider.usage":
                semantic_key = (
                    f"{payload['provider'].lower()}:{payload['capability'].lower()}:"
                    f"v{event.schema_version}"
                )
            else:
                semantic_key = {
                    "interview.resume-analysis.requested": "requested:v1.1",
                    "interview.resume-analysis.outcome": "outcome:v1.1",
                    "interview.session.completed": f"completed:v{event.schema_version}",
                    "interview.session.failed": f"failed:v{event.schema_version}",
                }[event.event_type]
        expected = (
            interview_runtime_event_id(
                event.event_type,
                event.aggregate_id,
                event.call_attempt_id,
                semantic_key,
            )
            if event.schema_version == "1.2"
            else interview_event_id(event.event_type, event.aggregate_id, semantic_key)
        )
        assert event.event_id == expected
        required = schema.get("required", schema.get("$defs", {}).get("event", {}).get("required"))
        assert set(payload) == set(required)


def test_schemas_reject_unknown_fields() -> None:
    fixture_path = CONTRACTS / "finalized-turn.fixture.json"
    schema = json.loads((CONTRACTS / "finalized-turn.schema.json").read_text(encoding="utf-8"))
    payload = json.loads(fixture_path.read_text(encoding="utf-8"))
    payload["candidate_email"] = "not-allowed@example.com"
    errors = list(Draft202012Validator(schema).iter_errors(payload))
    assert any("Additional properties" in error.message for error in errors)


def test_v12_runtime_identity_is_stable_within_attempt_and_scoped_across_attempts() -> None:
    session = UUID("22222222-2222-4222-8222-222222222222")
    first = UUID("55555555-5555-4555-8555-555555555555")
    second = UUID("66666666-6666-4666-8666-666666666666")
    event = "interview.turn.finalized"
    semantic = "turn:1:v1.2"

    assert interview_runtime_event_id(event, session, first, semantic) == UUID(
        "75214588-6495-5f61-a066-653c46c16fe5"
    )
    assert interview_runtime_event_id(event, session, first, semantic) != (
        interview_runtime_event_id(event, session, second, semantic)
    )
    assert interview_runtime_turn_id(session, first, 1) != interview_runtime_turn_id(
        session, second, 1
    )
