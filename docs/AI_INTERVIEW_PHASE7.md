# Phase 7 speech and Twilio transport

Phase 7 is dormant unless the coordinated Java and Python interview flags are enabled. The production
runtime is implemented in [Phase 8](AI_INTERVIEW_PHASE8.md); this document retains the transport-smoke
contract. The Phase 7 transport smoke mode is rejected when `APP_ENV=production`.

The Java API owns scheduled/admin dialing, immutable prepared-session snapshots, quota release for
never-answered calls, Twilio REST creation, Gather consent, signed callbacks, callback ordering, and
durable attempt history. The Python runtime owns hash-only Redis token lookup, Call SID claiming,
concurrency leases, Twilio Media Stream validation, Cartesia speech adapters, and the non-production
one-utterance smoke flow.

Required secrets are deliberately separate:

- `TWILIO_API_KEY_SID` and `TWILIO_API_KEY_SECRET` authenticate REST call creation.
- `TWILIO_AUTH_TOKEN` validates callbacks and WebSocket upgrade signatures.
- `INTERVIEW_RUNTIME_TOKEN_SECRET` derives opaque media tokens; plaintext tokens are not persisted.
- `CARTESIA_API_KEY`, `CARTESIA_ENGLISH_VOICE_ID`, and `CARTESIA_VIETNAMESE_VOICE_ID` configure speech.

Public callback URLs must use HTTPS and the media URL must use WSS. Access logs are disabled for the
token-bearing voice, consent, and media routes. Phase 7 always sends `record=false` and startup rejects
recruitment recording enablement.

## Credential-gated smoke gate

Outside production, set the coordinated recruitment/interview flags and
`INTERVIEW_TRANSPORT_SMOKE_MODE=true`. Place one English and one Vietnamese call through the shared
Twilio number. Each call must speak the localized disclosure, require DTMF `1`, synthesize one prompt,
transcribe one short response without logging or persistence, speak the closing, and close cleanly.
Repeat with invalid callback signatures, an invalid media signature, an expired token, and a token
claimed by another Call SID; all must be rejected. These real-provider tests are release gates and are
not normal CI jobs.
