# Phase 9 durable interview results and recordings

Phase 9 makes the Python interview runtime recoverable and makes Java the authoritative store for
transcripts, advisory scores, provider usage, and recordings. The existing `1.0` contracts remain
accepted; durable runtime publication uses the new `1.1` contracts.

## Runtime durability

Set `INTERVIEW_DURABLE_RESULTS_ENABLED=true` only with interview messaging and engine mode enabled.
Production media startup requires durable results. Every turn, aggregated measured provider-usage
fact, and terminal result is staged in a versioned Redis checkpoint, published with RabbitMQ
publisher confirms, marked confirmed, and then committed with checkpoint CAS. Pending publication is
recovered with the same event ID and payload after a crash or same-Call-SID reconnect.

The checkpoint stores deterministic engine position, elapsed time anchors, question limits,
evidence-linked evaluations, consecutive failures, terminal reason, next 1-based turn sequence,
measured usage, and pending AI audio. Unexpected media loss preserves partial STT text as an
interrupted candidate turn and adds the session to the recovery index. The embedded recovery worker
terminalizes abandoned sessions with a partial failed result.

The fixed recovery settings are:

```dotenv
INTERVIEW_DURABLE_RESULTS_ENABLED=false
INTERVIEW_PUBLISH_CONFIRM_MAX_ATTEMPTS=3
INTERVIEW_RECOVERY_MAX_ATTEMPTS=3
INTERVIEW_RECOVERY_POLL_SECONDS=5
INTERVIEW_RECOVERY_BATCH_SIZE=100
```

## Authoritative results

`V31__durable_interview_results_and_recordings.sql` adds the event inbox, ordered transcript,
terminal/section/question/evaluation results, provider usage, recordings, and recording-operation
jobs. Java accepts exact replays, rejects conflicting replays or a second terminal result, and
supports terminal-before-turn delivery with `PENDING_TURNS` reconciliation.

Question scores are accepted-evaluation means. The overall advisory score is the equal mean of
scored `CORE` questions multiplied by 20 and rounded half-up to two decimal places. English-screen
questions do not affect the overall score. The workplace English band uses unrounded dimension
means and is explicitly not IELTS or formal CEFR certification. No result handler shortlists or
rejects a candidate.

Authenticated tenant-scoped read endpoints return `Cache-Control: no-store`:

- `GET /api/v1/recruitment/interviews/{id}/transcript`
- `GET /api/v1/recruitment/interviews/{id}/result`
- `GET /api/v1/recruitment/interviews/{id}/recordings`
- `GET /api/v1/recruitment/interviews/{id}/recordings/{recordingId}/playback`
- `GET /api/v1/recruitment/interviews/{id}/recordings/{recordingId}/download`

## Recordings

`RECRUITMENT_RECORDING_ENABLED` remains off by default. Tenant admins can enable recording only when
the deployment flag and plan entitlement allow it. Call creation remains `record=false`; accepted
DTMF consent transactionally enqueues a dual-channel recording start. The operation worker
publishes each database-backed operation to a Java-local durable RabbitMQ queue with a five-second
publisher confirm. Malformed or exhausted deliveries enter a dedicated recording-operation DLQ,
while the database schedule remains the recovery source and resets the confirmed marker after
operational backoff. The worker reconciles uncertain starts, derives Twilio media URLs from validated SIDs, copies bounded MP3 data
to SeaweedFS, verifies size and SHA-256 using ranged reads, accounts for recruitment-storage quota,
deletes and confirms deletion at Twilio, and only then marks the recording ready.

Retention starts at the completed recording callback. Expired recordings and recordings belonging
to withdrawn applications are queued for storage/provider deletion. Playback and download are
proxied through Java; storage keys, provider URLs, and Twilio SIDs are never returned.
