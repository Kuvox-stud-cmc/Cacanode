# Phase 8 deterministic interview engine and English screen

Phase 8 replaces the Phase 7 runtime-not-ready media path with a deterministic, in-memory interview
engine. It does not add persistence, recordings, recruiter results, RabbitMQ interview events, Redis
turn checkpoints, public endpoints, database migrations, or protobuf fields.

## Runtime behavior

Deterministic code owns section and question order, the single active question, fixed section language,
interaction limits, time limits, transitions, and closing. The model may only return `ANSWER`,
`REPEAT`, `CLARIFY`, `FOLLOW_UP`, or `STOP` in strict JSON. Invalid, timed-out, unsafe, or structurally
unexpected output is retried once and then causes a deterministic scoreless advance. Three consecutive
model failures end the session safely.

Every listening turn opens a fresh Cartesia STT session in the current section's configured language.
Audio remains raw 8-kHz mono mu-law in both directions. Candidate media is ignored during AI audio,
and listening starts only after Twilio acknowledges a unique mark for the complete audio batch. Redis
lease heartbeats run independently of inbound media and clean completion terminalizes the runtime
session, invalidates its token, and removes concurrency membership.

Candidate transcripts, per-question rubric scores, English dimension scores, and the advisory English
band exist only in process memory and are cleared during media-session cleanup. They must never be
written to Redis, RabbitMQ, application logs, metrics labels, or Java persistence.

## Scoring

Accepted general answers receive an in-memory rubric score from 1 through 5. Accepted English-screen
answers additionally receive comprehension, fluency, vocabulary, grammar, and pronunciation scores.
The arithmetic mean is calculated per dimension and then across the five unrounded dimension means:

- below 2: `BASIC`
- below 3: `CONVERSATIONAL`
- below 4: `WORKING_PROFICIENCY`
- otherwise: `PROFESSIONAL`

No English band is produced when no English response was successfully evaluated.

## Configuration and activation

All recruitment calling and interview engine flags remain off by default. Media streaming requires
exactly one runtime mode:

```dotenv
INTERVIEW_ENGINE_ENABLED=false
INTERVIEW_TRANSPORT_SMOKE_MODE=false
INTERVIEW_MODEL_TIMEOUT_SECONDS=4
INTERVIEW_MODEL_MAX_OUTPUT_TOKENS=384
INTERVIEW_MODEL_MAX_ATTEMPTS=2
INTERVIEW_ENGINE_MAX_CONSECUTIVE_FAILURES=3
INTERVIEW_UTTERANCE_MAX_SECONDS=90
INTERVIEW_MIN_QUESTION_WINDOW_SECONDS=10
INTERVIEW_CLOSING_RESERVE_SECONDS=10
INTERVIEW_SPEECH_ENERGY_THRESHOLD=500
```

Smoke mode remains non-production. Production calling requires engine mode, smoke mode off, secure
Twilio callback/media URLs, complete Twilio and Cartesia credentials, the runtime-token secret, and a
configured OpenAI or Ollama chat model. The interview-specific model copy always uses temperature 0,
a four-second timeout, 384 output tokens, and the two-attempt policy.

## Quota settlement

Signed terminal Twilio status callbacks require a nonnegative integer `CallDuration`. Connected calls
settle `min(CallDuration, quotaReservedSeconds)` even when the callback is late, out of order, or the
attempt/interview is already terminal. Settlement never reopens business state. Exact duration replay
uses billing idempotency; a conflicting duration returns conflict. Busy, no-answer, and cancelled calls
that never connected retain the reservation-release path. Only the existing callback payload hash is
stored.

## Release verification

Run Python Ruff, mypy, and interview tests; Java recruitment and billing tests; the real-Redis interview
integration test; disabled and enabled Compose validation; and `git diff --check`. Credential-gated
bilingual release testing must validate final transcripts, strict actions, outbound audio, and p50 below
1.5 seconds / p95 below 2.5 seconds per language and combined before configuration-only activation.
The privacy-safe corpus can be reproduced with
`bash rag-chatbot-fastapi/artifacts/interview-speech/generate_samples.sh`; run the provider gate from
`rag-chatbot-fastapi` with
`INTERVIEW_CREDENTIAL_TESTS=1 PYTHONPATH=. .venv/bin/python artifacts/interview-speech/verify.py`.
