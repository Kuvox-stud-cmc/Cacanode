# Cacanode Recruitment and AI Phone Interview Implementation Plan

**Status:** Phased implementation plan

**Date:** July 23, 2026

**Initial interview languages:** Vietnamese and English

**Stable media WebSocket:** <code>/ws/v1/interviews/twilio/media?token=...</code>

**Recording ownership:** Java recruitment owns Twilio recording callbacks, verified
SeaweedFS copies, and the durable <code>recruitment.recording.ready</code> fact. Python
never owns recording readiness.

This document replaces the earlier greenfield seven-day MVP draft. The work must extend the existing Cacanode Spring Boot modular monolith, FastAPI capability modules, shared protobuf service, RabbitMQ contracts, Redis runtime, SeaweedFS storage, billing system, Next.js frontend, and deployment topology without weakening their current boundaries.

## 1. Product outcome

The target product adds recruitment and AI-assisted phone interviews to Cacanode:

- Authenticated employees with either the <code>USER</code> or <code>TENANT_ADMIN</code> role can create, manage, and publish jobs.
- Candidates can discover jobs through a global public job board or a tenant career page, search and filter vacancies, and apply without authentication.
- Each job configures one CV policy: <code>REQUIRED</code>, <code>OPTIONAL</code>, or <code>DISABLED</code>. An application may contain at most one private PDF or DOCX CV.
- Candidates schedule an interview through a scoped email link. The interview itself is a Twilio phone call and requires neither a smartphone nor a Cacanode account.
- A Vietnamese interview may contain one optional short English-speaking section with two to five questions.
- The English section is a quick workplace-language screen. It must not be marketed as IELTS, CEFR certification, or another formal language qualification.
- CV summarization and question personalization are configurable per tenant and per job, require candidate disclosure, and may add no more than two evidence-grounded questions.
- Automatic interviews use tenant defaults with explicit per-job overrides.
- Raw audio recording is tenant-admin opt-in, disabled by default, and starts only after the candidate explicitly consents during the call.
- The first release may call only Vietnamese <code>+84</code> destinations.
- AI-generated scores and summaries are advisory. The system never automatically hires or rejects a candidate.

Email is the only candidate invitation and reminder channel in the first release. Candidates never receive Cacanode user accounts; public actions use scoped, expiring, opaque tokens whose stored forms are hashed.

## 2. Repository-aware architecture

### 2.1 Java module ownership

Add one Java business module named <code>recruitment</code>. It owns:

- Jobs and their lifecycle.
- Interview templates and immutable template revisions.
- Candidates, applications, screening answers, and CV metadata.
- Recruitment-specific tenant settings and scheduling availability.
- Invitations, interview sessions, call attempts, transcripts, results, and recording metadata.
- Java-side inboxes for interview runtime events.
- SeaweedFS object keys under <code>recruitment/{tenant_id}/...</code>.

Recruitment-specific settings remain in recruitment-owned tables even when their controls appear inside the tenant-settings UI.

No recruitment class may import another business module's repository, entity, service, query, or DTO. Every cross-module command, result, enum, event, and exception belongs to the publishing module's API package.

### 2.2 Python module ownership

Add one Python capability module at <code>app/modules/interview</code> with the existing capability structure:

~~~text
interview/
  api/
  internal/
  transport/
~~~

The module owns:

- Live interview orchestration and deterministic question progression.
- Twilio Media Stream transport.
- Runtime Redis checkpoints, leases, and event publication.
- CV analysis and interview evaluation workflows.

The Python runtime must not connect to PostgreSQL or read Java business tables. Java provides a prepared, immutable session snapshot through an internal boundary before the call begins.

### 2.3 Dependency graph

The allowed new dependencies are:

~~~text
Java:
recruitment -> tenant.api, billing.api, ai.api, common
notification -> recruitment.api.event
analytics    -> recruitment.api.event, recruitment analytics export API
integration  -> recruitment.api.event
bootstrap    -> recruitment, wiring only

Python:
interview -> ingestion.api, model.api, common
ingestion -> existing dependencies
model     -> common
bootstrap -> interview, wiring only
~~~

Extend the architecture tests with zero allowlist debt: recruitment cannot access another module's implementation packages, Python cross-module imports must use <code>api</code> packages, and the module graphs must remain acyclic.

### 2.4 Reusable Python capabilities

Extend <code>model.api</code> with provider-neutral protocols and normalized events for:

- Streaming speech-to-text.
- Streaming text-to-speech.
- Turn detection and utterance finalization.

Expose PDF and DOCX text extraction as a pure <code>ingestion.api.ContentExtractionApi</code>. The interview module may call this API but may not import ingestion internals, ingestion transports, generated clients, or storage implementations.

Provider-specific Cartesia, Twilio, RabbitMQ, Redis, and generated protobuf imports remain in the owning module's <code>internal</code> or <code>transport</code> packages.

## 3. Public and internal contracts

### 3.1 Java capability APIs

Add focused, provider-neutral Java APIs:

- <code>TenantPublicProfileApi</code> publishes safe company-branding snapshots for public job pages.
- <code>HiringQuotaApi</code> handles active-job capacity, verified applications, CV analyses, recruitment storage, and interview-second reservations.
- <code>InterviewInferenceApi</code> under <code>ai.api</code> prepares and cancels Python voice sessions.
- <code>RecruitmentAnalyticsExportApi</code> exports status/count facts so analytics projections can be rebuilt without reading recruitment tables.

Each API owns its commands, results, enums, and boundary exceptions. Implementations translate internal models to these published types.

### 3.2 Protobuf

Add the following RPCs to the existing protobuf service:

~~~text
PrepareInterviewSession
CancelInterviewSession
~~~

The change must be additive. Existing RPC numbers, message field numbers, field meanings, and response behavior remain unchanged. Descriptor tests must assert compatibility.

The prepare request carries only the immutable runtime data needed for the call, including question snapshots, language-section boundaries, disclosure text, limits, runtime capabilities, and opaque identifiers. It must not make Python query Java state.

### 3.3 RabbitMQ and JSON Schema contracts

Add versioned schemas and cross-language fixtures under:

~~~text
contracts/ai-interview/v1/
~~~

Cover at least:

- Resume-analysis requests.
- Resume-analysis outcomes.
- Finalized interview turns.
- Interview-completed events.
- Interview-failed events.
- Provider-usage events.
- Recording-ready events.

Python publishes interview runtime events through an interview-owned RabbitMQ exchange using publisher confirms. Requirements:

- Stable UUIDv5 event IDs derived from the owning aggregate and semantic event identity.
- Versioned routing keys and schemas.
- Durable queues and dead-letter queues.
- Idempotent Java consumption through a recruitment-owned inbox.
- Redis checkpoints that advance only after confirmed publication.
- Checkpointing and event delivery remain active independently of <code>CACHE_ENABLED</code>.

Queue names, routing keys, Redis key prefixes, TTLs, ownership, retry policy, and dead-letter recovery procedures must be documented and architecture-tested.

## 4. End-to-end workflow

~~~text
public application
  -> email verification/management token
  -> ApplicationSubmitted durable event
  -> automatic policy creates invitation
  -> candidate selects slot through email link
  -> scheduler reserves interview quota
  -> Java prepares Python runtime over gRPC
  -> Java starts Twilio call
  -> DTMF consent
  -> Twilio Media Stream connects to Python
  -> Python publishes durable turns and terminal result
  -> Java stores authoritative transcript/result
  -> billing settles actual connected seconds
~~~

The scheduling and call flow must tolerate retries and out-of-order provider callbacks. Business state is authoritative in Java; Redis contains recoverable live-runtime state; Twilio is a transport provider rather than a business source of truth.

### 4.1 Automation modes

Jobs use one of three automation modes:

- <code>MANUAL</code>
- <code>AUTO_INVITE_ALL</code>
- <code>AUTO_INVITE_MATCHING</code>

<code>AUTO_INVITE_MATCHING</code> may evaluate only explicit deterministic screening rules configured for the job. An AI score, CV summary, interview score, or inferred protected attribute must never automatically reject or suppress a candidate.

## 5. Domain lifecycle

### 5.1 Job states

~~~text
DRAFT -> PUBLISHED <-> PAUSED -> CLOSED -> ARCHIVED
~~~

Only published, non-expired jobs are visible publicly. Publishing freezes the public company-branding snapshot used by recruitment queries, while later safe tenant-profile events may refresh that snapshot without a runtime join to tenant-owned tables.

### 5.2 Application states

~~~text
SUBMITTED_UNVERIFIED -> SUBMITTED -> INTERVIEW_INVITED
-> INTERVIEW_SCHEDULED -> INTERVIEW_COMPLETED -> UNDER_REVIEW
-> SHORTLISTED | REJECTED | WITHDRAWN
~~~

Transitions are explicit and idempotent. Human recruiters own <code>SHORTLISTED</code> and <code>REJECTED</code> decisions.

### 5.3 Call and interview states

~~~text
INVITED
SCHEDULED
PREPARING
CALLING
RINGING
CONSENT_PENDING
IN_PROGRESS
COMPLETED
NO_ANSWER
DECLINED
FAILED
CANCELLED
EXPIRED
~~~

Call attempts are separate records so retries do not overwrite history. Provider callbacks use monotonic business transition rules and cannot move a terminal attempt back into an active state.

## 6. HTTP APIs and query behavior

### 6.1 Public recruitment routes

~~~http
GET  /api/v1/public/jobs
GET  /api/v1/public/careers/{tenantSlug}/jobs
GET  /api/v1/public/jobs/{publicId}
POST /api/v1/public/jobs/{publicId}/applications

GET  /api/v1/public/interview-invitations/{token}
GET  /api/v1/public/interview-invitations/{token}/slots
POST /api/v1/public/interview-invitations/{token}/schedule
POST /api/v1/public/interview-invitations/{token}/reschedule
POST /api/v1/public/interview-invitations/{token}/withdraw
~~~

Authenticated routes for jobs, applications, candidates, templates, settings, invitations, interviews, recordings, and results live under:

~~~text
/api/v1/recruitment/**
~~~

Both <code>USER</code> and <code>TENANT_ADMIN</code> can manage jobs and candidates. Only <code>TENANT_ADMIN</code> can change tenant-wide automation defaults, interview defaults, recording enablement, or retention settings.

### 6.2 Public search

Public job search uses opaque keyset cursors:

- Default limit: 20.
- Maximum limit: 50.
- Cursor includes the normalized sort tuple and ID tie-breaker but exposes no internal ID or tenant data.
- Invalid, expired, or sort-incompatible cursors return a stable client error.

Supported filters:

- Company.
- Location.
- Department.
- Employment type.
- Workplace type.
- Experience level.
- Interview language.
- Closing date.

Supported sorts:

- <code>relevance</code>
- <code>newest</code>
- <code>closing_soon</code>

Use PostgreSQL full-text and trigram indexes. Public queries read only recruitment-owned projections and snapshots; they never join tenant-owned tables at request time.

### 6.3 Authenticated lists

Authenticated recruitment lists use:

- Zero-based <code>page</code>.
- Default <code>size=20</code>.
- Maximum <code>size=100</code>.
- <code>X-Total-Count</code> on list responses.
- Explicit sort whitelists with an ID tie-breaker.

Application and interview filters include:

- Status.
- Job.
- Date range.
- CV presence.
- CV-analysis status.
- Interview status.
- Score range.
- English band.
- Candidate search.

Every query enforces tenant scope and stable sorting.

## 7. Applications, candidate tokens, and CV storage

### 7.1 Public application behavior

Applications are unauthenticated and duplicate-safe. The service:

- Applies Turnstile and rate limits before expensive work.
- Uses normalized email and job identity for duplicate handling.
- Returns a generic <code>202 Accepted</code> response for both new and duplicate submissions.
- Resends the scoped management/verification email without revealing whether an applicant already exists.
- Uses opaque verification and management tokens with expiry, purpose, rotation, and hashed-at-rest storage.
- Prevents tokens from being replayed for a different job, application, or action.

Candidates can use the management link to verify, schedule, reschedule, withdraw, and access only the minimum public status needed for those actions.

### 7.2 CV requirements

Each job selects:

- <code>REQUIRED</code>
- <code>OPTIONAL</code>
- <code>DISABLED</code>

Accept:

- One file per application.
- PDF or DOCX only.
- Maximum size of 5 MB.

The upload flow is:

~~~text
upload to quarantine
  -> verify extension, declared type, and magic bytes
  -> scan for malware
  -> reject or promote to recruitment-owned storage
~~~

Employee download is authorized, tenant-scoped, audited, and delivered through a short-lived response or signed object-storage operation. Public tokens never grant direct object-storage access.

CVs and derived text must never be added to:

- The Java <code>documents</code> domain.
- General knowledge ingestion.
- Qdrant.
- Kuzu.
- Search indexes outside the recruitment-owned public/application projections.
- Analytics payloads.

Deletion and retention workflows remove quarantined files, promoted objects, derived analysis artifacts, and metadata consistently.

### 7.3 AI CV modes

Tenant settings define a default and each job may override it:

- <code>OFF</code>
- <code>SUMMARY_ONLY</code>
- <code>PERSONALIZED_QUESTIONS</code>

Before model processing:

- The application discloses the configured use to the candidate.
- Contact details are redacted.
- Protected and sensitive attributes are removed or excluded.
- The CV is treated as untrusted input and cannot modify system instructions.

CV analysis may produce a structured summary and skill evidence. Personalization may add at most two questions, and every generated question must cite specific CV evidence. Approved questions are frozen into the interview-session snapshot.

Analysis failure, unsupported content, provider failure, or quota exhaustion falls back to the template-only interview and never blocks scheduling or calling.

Use a content hash plus policy/model version for idempotency so duplicate delivery does not consume quota or repeat work.

## 8. Interview templates and English screen

Interview templates are revisioned. Editing a template creates a new revision rather than mutating a revision used by an invitation or session. Every scheduled session receives an immutable snapshot containing:

- Introduction and consent disclosure.
- Ordered sections and questions.
- Rubrics and score limits.
- Follow-up and repetition limits.
- Language pipeline choices.
- Maximum section and call duration.
- Frozen CV-personalized questions and evidence, when enabled.

A Vietnamese template may contain one <code>ENGLISH_SCREEN</code> section:

- Two to five English questions.
- No more than five minutes.
- An explicit spoken transition from Vietnamese to English.
- An explicit spoken transition back to Vietnamese when more Vietnamese content follows.
- STT/TTS pipeline switching only at section boundaries.
- No language auto-detection.

Score the English screen separately from the main interview:

- Comprehension: 1–5.
- Fluency: 1–5.
- Vocabulary: 1–5.
- Grammar: 1–5.
- Pronunciation: 1–5.

Map the result to a non-standard workplace band:

- <code>BASIC</code>
- <code>CONVERSATIONAL</code>
- <code>WORKING_PROFICIENCY</code>
- <code>PROFESSIONAL</code>

Every recruiter-facing result must state that this is a quick workplace-language screen, not IELTS or formal CEFR certification.

## 9. Twilio call, consent, and recording rules

### 9.1 Call preparation

Do not dial until:

- The candidate selected a valid slot.
- The job and invitation remain active.
- The phone number is a normalized Vietnamese <code>+84</code> destination.
- Maximum configured call seconds have been reserved successfully.
- Python accepted the immutable session snapshot and returned a short-lived runtime token.

Java creates the Twilio call and stores the Call SID against a call attempt. Public webhook and media URLs carry only opaque tokens.

### 9.2 Webhook and stream security

- Validate Twilio signatures before processing voice, status, or recording webhooks.
- Bind short-lived media tokens to one call attempt and consume them idempotently.
- Reject invalid, expired, replayed, or mismatched tokens.
- Apply callback deduplication and explicit out-of-order transition rules.
- Redact phone numbers, tokens, transcripts, CV content, and provider credentials from normal logs.
- Mount the interview WebSocket in <code>app.main:app</code>; do not add a fourth runtime role.

Twilio Media Streams use bidirectional 8-kHz mu-law audio. Provider-neutral speech APIs hide Cartesia-specific payloads from the interview engine.

### 9.3 DTMF consent

Every call begins in <code>CONSENT_PENDING</code> with a spoken disclosure identifying:

- The hiring company.
- That the interviewer is AI-assisted.
- The purpose of the call.
- Whether CV information was used for personalization.
- Whether recording is enabled.

The candidate gives explicit DTMF consent before the live interview begins. Declining or failing the configured consent flow ends the call without starting the interview or recording.

### 9.4 Optional recording

Raw audio recording:

- Is disabled by default.
- Can be enabled only by a <code>TENANT_ADMIN</code> whose plan permits it.
- Starts only after explicit call consent.
- Is copied from Twilio to <code>recruitment/{tenant_id}/...</code> in SeaweedFS.
- Is verified before becoming available to employees.
- Is removed from Twilio after verified copy.
- Has authenticated, tenant-scoped playback/download and audit events.

Maximum retention:

- Pro: 30 days.
- Business: 90 days.
- Enterprise: contractual.

Retention jobs must delete the recruitment object and confirm provider deletion. A failed copy or deletion remains visible to operations and retries through a bounded dead-letter workflow.

## 10. Interview engine and durable results

The Python interview engine enforces business constraints in deterministic code:

- Ordered question and section progression.
- One active question at a time.
- Rubric and score bounds.
- Maximum clarification, repetition, and follow-up counts.
- Section and total time budgets.
- Silence handling.
- Candidate stop requests.
- Structured model actions rather than free-form control.

The model may help phrase acknowledgements, clarification, and allowed follow-ups, but it cannot skip required sections, exceed limits, change languages outside a boundary, promise employment, or make a hiring decision.

Finalized turns are checkpointed in interview-owned Redis and durably published before the checkpoint advances. Java:

- Stores authoritative ordered transcript turns.
- Deduplicates events through the recruitment inbox.
- Handles duplicate and out-of-order delivery.
- Creates a useful partial result after interruption.
- Stores evidence-grounded interview and English scores.
- Marks all AI output as advisory.

Billing reserves the configured maximum before dialing and settles the actual connected seconds on a terminal status. Unused reserved seconds are released; retries are idempotent.

## 11. Pricing, entitlements, and cost controls

Use hard limits for the MVP. Do not implement automatic overage billing. Internally meter interviews in connected seconds and reserve the configured maximum before each call.

| Plan | Price | Active jobs | Verified applications | Interview allowance | AI CV analyses | Recruitment storage | Hiring features |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Starter | Free | 1 | 25/month | 0 | 0 | 50 MB | Public jobs, applications, manual CV review |
| Trial | 14 days | 1 | 25 total | 20 minutes | 5 | 100 MB | Auto invitation and CV personalization for evaluation |
| Pro | 1,199,000 VND/month or 11,990,000/year | 3 | 150/month | 60 minutes/month | 100/month | 1 GB | Automation, English section, CV personalization, optional recording with 30-day retention |
| Business | 3,499,000 VND/month or 34,990,000/year | 10 | 1,000/month | 300 minutes/month | 500/month | 10 GB | Pro features, 15 seats, 90-day recording retention |
| Enterprise | Contracted | Contracted | Contracted | Contracted, never implicit unlimited | Contracted | Contracted | Custom limits, retention, support, and future dedicated numbers |

Business also receives:

- 50,000 support-chat messages.
- 250 knowledge documents.
- 15 team members.
- 50 GB of existing platform storage.

Existing Starter and Pro support-chat entitlements remain unchanged.

### 11.1 Pricing basis

Provider pricing referenced on July 23, 2026:

- [Twilio Vietnam Voice pricing](https://www.twilio.com/en-us/voice/pricing/vn): approximately $0.1777/minute to Vietnamese mobiles and $0.0044/minute for Media Streams.
- [Cartesia pricing](https://cartesia.ai/pricing): current speech-credit and voice-agent benchmarks.
- [OpenAI API pricing](https://developers.openai.com/api/docs/pricing): current configured <code>o4-mini</code> benchmark of $1.10/M input and $4.40/M output tokens.
- [Willo pricing](https://www.willo.video/pricing-2026): approximately $279/month for Growth and $409/month for Scale.

Use conservative internal cost ceilings of:

- 6,000 VND per connected interview minute.
- 700 VND per successful CV analysis.

Revalidate all provider prices before Phase 2 implementation and again before production activation.

## 12. Delivery phases

Phases are sequential at their gates, although work inside a phase may proceed in parallel when contracts are already frozen. All feature flags default off until staged activation.

### Phase 1 — Architecture and contract foundation

- Add dormant Java <code>recruitment</code> and Python <code>interview</code> module boundaries.
- Add API packages, feature flags, and typed configuration objects.
- Update architecture guidance and zero-allowlist enforcement.
- Add additive protobuf definitions and descriptor assertions.
- Add RabbitMQ JSON Schemas, fixtures, queue ownership, routing-key ownership, Redis-key ownership, and recovery rules.
- Update the Python HTTP-surface test to allow only the new Twilio interview WebSocket in addition to existing health and metrics routes.

**Gate:** Java and Python architecture tests, protobuf compatibility tests, schema fixtures, and <code>git diff --check</code> pass with every recruitment feature flag disabled.

### Phase 2 — Billing and quota foundation

- Add <code>BUSINESS</code> throughout billing and tenant enums, checkout/catalog responses, PayOS handling, frontend types, and database migrations.
- Extend entitlement snapshots and usage metrics with hiring limits.
- Implement idempotent application and CV-analysis consumption.
- Implement recruitment-storage reservation and release.
- Implement interview-second reserve, settle, and release APIs.
- Update public pricing and billing settings.

**Gate:** Concurrent reservations cannot exceed limits; retries do not double-charge; downgrades, renewals, trials, annual billing, and quota resets are tested.

### Phase 3 — Recruitment domain and employee APIs

- Implement recruitment-owned tables, state machines, repositories, and indexes.
- Implement template revisions and English-section validation.
- Implement recruitment tenant settings and authenticated CRUD.
- Allow both <code>USER</code> and <code>TENANT_ADMIN</code> to create and manage jobs.
- Restrict tenant-wide interview defaults, recording, retention, and automation defaults to <code>TENANT_ADMIN</code>.
- Add paginated job, template, application, candidate, and interview queries with the specified filters, search, stable sorting, and tenant scope.

**Gate:** Tenant isolation, role checks, lifecycle transitions, template-snapshot immutability, and query/filter tests pass.

### Phase 4 — Public job board, applications, and CV storage

- Build the global job board, tenant career pages, job details, and application pages.
- Add JobPosting JSON-LD, sitemap entries, and canonical URLs.
- Implement public API cursor pagination, search, filters, and sorts.
- Add duplicate-safe application submission and opaque email-management tokens.
- Add Turnstile and public rate limits.
- Add CV quarantine, validation, malware scanning, promotion, employee download, withdrawal, retention, and deletion.
- Return generic <code>202 Accepted</code> responses for duplicate public submissions and resend the management email without revealing applicant existence.

**Gate:** An unauthenticated application works end to end; malformed files, malware, replayed tokens, cross-tenant access, spam limits, expired jobs, and cursor stability are covered.

### Phase 5 — Automatic invitation and scheduling

- Publish <code>ApplicationSubmittedEvent</code> durably.
- Consume it idempotently for <code>AUTO_INVITE_ALL</code> and deterministic <code>AUTO_INVITE_MATCHING</code>.
- Generate email-only invitations.
- Implement timezone-aware availability and slots.
- Implement conflict-safe booking, rescheduling, cancellation, expiry, and reminders.
- Do not initiate a call until the candidate selects a slot and interview quota can be reserved.

**Gate:** Duplicate events create one invitation; concurrent slot selection cannot double-book; DST/timezone handling, expired tokens, exhausted quota, and job closure are tested.

### Phase 6 — Configurable CV AI processing

- Add durable resume-analysis request and result flows between Java and Python.
- Expose <code>ContentExtractionApi</code> from ingestion.
- Implement interview-owned redaction, structured summaries, skill evidence, and up to two grounded personalized questions.
- Enforce tenant and job modes, candidate disclosure, plan quotas, and content-hash idempotency.
- Fall back to template-only interviews on any analysis failure.

**Gate:** PDF/DOCX fixtures, redaction, evidence grounding, protected-attribute exclusion, duplicate delivery, provider failure, and quota exhaustion pass without writing to knowledge indexes.

### Phase 7 — Speech capability and Twilio transport

- Add model-owned Cartesia STT/TTS adapters and normalized speech APIs.
- Implement prepare/cancel gRPC handling and short-lived runtime tokens.
- Add Twilio REST call creation.
- Add signature validation, DTMF consent, bidirectional 8-kHz mu-law streaming, silence handling, and concurrency limits.
- Mount the WebSocket in <code>app.main:app</code> without adding a fourth runtime role.

**Gate:** Real English and Vietnamese phone smoke calls complete using the shared Twilio number, and invalid signatures or tokens are rejected.

### Phase 8 — Interview engine and English screen

- Implement deterministic question order, rubric limits, clarification, repetition, maximum follow-ups, time budgets, stop requests, and structured model actions.
- Implement Vietnamese-to-English section switching and separate workplace-language scoring.
- Reserve maximum call seconds before dialing and settle actual connected duration on terminal status.

**Gate:** At least 20 English and 20 Vietnamese 8-kHz samples pass; candidate-speech-end to AI-audio-start remains below 1.5 seconds p50 and 2.5 seconds p95.

### Phase 9 — Durable transcripts, results, and recordings

- Checkpoint turns in interview-owned Redis.
- Publish stable turn and terminal events with confirms before advancing checkpoints.
- Persist authoritative turns and results in Java.
- Deduplicate through a recruitment-owned inbox.
- Support partial transcripts and partial results after interruption.
- Add optional recording copy, authenticated playback/download, retention cleanup, audit events, and Twilio deletion confirmation.
- Store evidence-grounded interview and English scores without automatic hiring decisions.

**Gate:** Crashes around every checkpoint/publication boundary, duplicate events, out-of-order Twilio callbacks, partial transcripts, recording deletion, and retry exhaustion are tested.

### Phase 10 — Recruiter UX, analytics, integrations, and documentation

- Add dashboard navigation and responsive pages for jobs, candidates, applications, templates, schedules, interviews, transcripts, recordings, results, and hiring usage.
- Add recruitment-owned export APIs and analytics projections containing statuses and counts only.
- Never export CV text, transcript text, or recordings to analytics.
- Add webhook facts such as <code>job.published</code>, <code>application.submitted</code>, and <code>interview.completed</code>.
- Add bilingual frontend documentation for jobs and applications, AI interview setup, Vietnamese English sections, scheduling, CV personalization, consent and recording, results, quotas, privacy, and troubleshooting.

**Gate:** Frontend lint, typecheck, production build, i18n parity, accessibility checks, mobile layouts, and analytics rebuild tests pass.

### Phase 11 — Security, operations, and staged activation

- Add provider-health, queue-lag, Redis-checkpoint, call-concurrency, quota-reservation, cost, latency, no-answer, failure, recording-retention, and cross-tenant metrics.
- Review public uploads, token leakage, SSRF, webhook replay, CV prompt injection, phone fraud, log redaction, and deletion requests.
- Validate Redis AOF and <code>noeviction</code>, RabbitMQ DLQs, Nginx WebSocket upgrades, Compose, backups, rollback, and operational recovery.
- Activate first for one internal tenant, then selected Pro and Business tenants, then public job discovery.
- Keep per-tenant kill switches for automation, CV AI, calling, and recording.

**Gate:** Full Java, Python, and frontend suites; real Redis/RabbitMQ integration; Twilio sandbox and real-call tests; Compose validation; deployment smoke tests; and legal/privacy approval pass before general availability.

## 13. Acceptance criteria

The implementation is ready for general availability only when:

- Two tenants can publish jobs and conduct interviews through the same Twilio number without data leakage.
- An unauthenticated candidate can search, filter, apply, optionally or mandatorily upload a CV, schedule through email, and complete the interview on a basic phone.
- A Vietnamese interview can include and separately score a short English section.
- CV personalization is evidence-grounded, configurable, disclosed, consented, quota-limited, and safely disabled on failure.
- Template edits cannot change a scheduled or active interview.
- Duplicate public submissions, durable events, provider callbacks, and settlement retries do not create duplicate business effects.
- Interview usage is reserved before calling and settled in actual connected seconds.
- Recording remains off by default, starts only after explicit call consent, follows plan retention, and is deleted from Twilio after verified copy.
- Recruiters see evidence and advisory limitations before using AI scores.
- No workflow automatically hires or rejects a candidate.
- Existing chat, document ingestion, RabbitMQ, Qdrant, Kuzu, Redis, protobuf, health, billing, and deployment contracts remain compatible unless this plan explicitly extends them.

## 14. Assumptions and release constraints

- Candidates do not receive Cacanode accounts.
- Email is the only invitation and reminder channel in the MVP.
- Candidate actions use scoped, expiring, opaque tokens stored only as hashes.
- Only Vietnamese <code>+84</code> destinations are callable in the first release.
- All tenants initially share the configured Twilio number.
- Audio recording defaults off and always requires explicit call consent.
- Python remains free of PostgreSQL and Java business-table access.
- CV files and CV-derived content remain outside documents, knowledge ingestion, Qdrant, Kuzu, and analytics.
- Enterprise limits and retention are explicit contractual values, never implicit unlimited values.
- Provider prices and telephone-audio quality must be revalidated before implementation and production activation.
- Legal review for Vietnamese recruitment, automated calls, AI disclosure, recording, retention, and candidate-data processing is mandatory before production.
