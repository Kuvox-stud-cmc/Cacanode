# Java API modular-monolith guide

This guide explains how the Spring Boot API under `com.cacanode.api` is organized and how to add
features without breaking its module boundaries.

The API is a modular monolith: one repository, one JVM, one deployable application, and one
database, split into business modules with explicit contracts and exclusive data ownership. The
boundaries described here are the current architecture, not a future migration target. ArchUnit
and table-ownership tests enforce them on every test run.

External compatibility remains a first-class constraint. Refactoring an internal module must not
silently change REST paths, JSON contracts, protobuf contracts, authentication behavior, database
table names, or customer-visible behavior.

## The four non-negotiable rules

### Rule 1: cross modules only through `api` or `api.event`

A business module may not import another module's controller, service, query, repository, entity,
internal DTO, configuration, or infrastructure implementation.

Allowed cross-module imports are:

- synchronous contracts and their API-owned value types under `<module>.api`;
- producer-owned event contracts under `<module>.api.event`;
- business-neutral technical infrastructure from `common`;
- generated external transport contracts, such as protobuf classes.

```java
// Wrong: auth reaches into tenant persistence.
import com.cacanode.api.tenant.repository.UserRepository;

// Wrong: support calls a chat implementation.
import com.cacanode.api.chat.query.ChatControlPlaneService;

// Correct: auth uses a tenant capability.
import com.cacanode.api.tenant.api.TenantIdentityApi;

// Correct: support validates a conversation through the chat boundary.
import com.cacanode.api.chat.api.ChatApi;
```

Controllers belong to one module and call that module's application layer. A controller must not
become a composition layer for another module's implementation.

### Rule 2: synchronous boundaries are capability-focused interfaces

When a caller needs an immediate answer or an atomic decision, the owning module exposes an
interface under `<module>.api`.

The API package owns every command, result record, enum, and exception that crosses the boundary.
An API contract must not expose JPA entities, repositories, servlet types, internal DTOs, query
objects, or implementation services.

```java
// tenant.api
public interface TenantIdentityApi {
    UserSnapshot requireUser(UUID tenantId, UUID userId);
}

// auth depends on the interface and its API-owned snapshot.
UserSnapshot user = tenantIdentityApi.requireUser(tenantId, userId);
```

Prefer small interfaces named after a capability. Do not create an umbrella interface merely to
expose an internal service, an empty marker API, or a concrete class named `*ModuleApi`.

Not every module needs a synchronous API. Terminal modules such as `notification`, and modules
that react only to published facts, may expose no callable business boundary.

### Rule 3: one module owns each database table

Only the owning module may access a table through JPA, JDBC, native SQL, `EntityManager`, or any
other runtime persistence mechanism.

A foreign key may point to another module's table, but it does not transfer ownership. The Java
side represents cross-module references as scalar IDs when the referenced entity is owned
elsewhere. For example, support stores tenant, chatbot, token, conversation, and assignee UUIDs; it
does not map those modules' entities.

If another module needs data, it must:

1. call an owner API for a synchronous use case;
2. consume an owner event for an independent reaction; or
3. read its own projection, populated from owner events or owner export APIs.

Cross-module reporting joins are not allowed in runtime application code. `analytics` owns its
reporting tables and never queries the operational tables owned by tenant, document, chat, or
support.

### Rule 4: independent reactions use producer-owned durable events

If a producer does not need an immediate return value, it publishes a fact instead of calling the
consumer's service.

Business event contracts live under the producer's `<module>.api.event` package. They are
immutable and carry enough data for consumers without exposing the producer's entities.

Business events that drive notifications, webhooks, token revocation, billing setup, or analytics
must use the durable module-event outbox. Do not implement a new durable consumer with
`@TransactionalEventListener(AFTER_COMMIT)` or `@Async`; the relay must invoke consumers
synchronously so it can detect failure and retry safely.

Direct Spring application events remain appropriate for technical, in-process concerns such as
audit recording and cache invalidation.

## Package model

Every first-level business package is a module:

```text
com.cacanode.api.<module>
```

A typical module looks like this:

```text
<module>/
  api/                 supported synchronous contracts and boundary value types
    event/             immutable facts owned by this producer
  controller/          REST endpoints owned by the module
  service/ or query/   application logic and owner-specific queries
  model/               persistence/domain types private to the module
  repository/          persistence access private to the module
  ...                  other module-private implementation packages
```

Everything outside `api` and `api.event` is private to the module, even when Java visibility is
`public`. Public visibility is sometimes required by Spring; it is not permission for another
module to import the type.

Two first-level packages have special roles:

- `common` is the technical shared kernel. It contains business-neutral infrastructure such as
  durable event storage, storage abstractions, cache infrastructure, filters, audit support, and
  generic errors. It must not depend on any business module.
- `bootstrap` is the composition root. It may wire all modules, register event types, configure
  security, run reconciliation commands, and expose operational health. It must not contain
  business decisions or become a shortcut for cross-module orchestration.

## Allowed dependency graph

The business dependency graph is intentionally acyclic:

```text
tenant       -> ai.api
auth         -> tenant.api
billing      -> tenant.api, document.api
document     -> tenant.api, ai.api
chat         -> tenant.api, document.api, billing.api, ai.api
recruitment  -> ai.api, billing.api, tenant.api
platform     -> tenant.api, recruitment.api, common
support      -> tenant.api, chat.api
integration  -> tenant.api and producer api.event packages
notification -> producer api.event packages
analytics    -> producer api.event packages and projection-export APIs
common       -> no business module
bootstrap    -> all modules, wiring only
```

Before adding a dependency, check this graph. If the new edge reverses an existing direction or
creates a cycle, redesign the interaction as an owner API, a producer event, or an owned
projection.

## Module catalog

| Module | Responsibility | Supported synchronous boundaries | Owned tables / database objects |
| --- | --- | --- | --- |
| `ai` | Model configuration and the gRPC inference/index boundary | `AiInferenceApi`, `ModelConfigurationApi`; API-owned AI requests, results, citations, units, and `AiInferenceException` | `model_config_versions` |
| `tenant` | Tenants, users, invitations, entitlements, workspaces, knowledge bases, chatbots, widgets, and integration access tokens | `TenantIdentityApi`, `TenantEntitlementApi`, `TenantWorkspaceApi`, `IntegrationAccessApi`, `TenantAnalyticsExportApi` | `tenants`, `users`, `invitations`, `knowledge_bases`, `chatbots`, `widget_configs`, `integration_tokens` |
| `auth` | Registration/login flows, JWTs, refresh tokens, verification, login 2FA, and authentication abuse controls | Primarily REST-facing; calls `tenant.api` for identity changes | `refresh_tokens`, `login_2fa_state`, `user_suspension_state`, `verification_resend_state` |
| `billing` | Plans, subscriptions, payment orders, PayOS webhooks, entitlements, quotas, and usage counters | `BillingModuleApi`, `BillingQuotaApi`, `HiringQuotaApi`, API-owned billing DTOs and quota exceptions | `usage_metrics`, `billing_subscriptions`, `billing_payment_orders`, `billing_webhook_events`, `billing_order_code_seq`, `hiring_quota_consumptions`, `hiring_quota_reservations` |
| `document` | Document metadata, object storage, ingestion, indexing cleanup, visibility, citations, evidence links, and usage export | `DocumentApi` | `documents`, `internal_event_outbox`, `internal_event_inbox` |
| `chat` | Employee and external conversations, messages, turns, idempotency, quota coordination, and inference orchestration | `ChatApi`, including support validation and analytics snapshot export | `chat_sessions`, `chat_messages`, `chat_turns` |
| `recruitment` | Jobs, candidates, applications, scheduling, AI interview calling, durable interview results, recordings, candidate communications, privacy deletion, activation, and recruitment analytics export | `RecruitmentApplicationCommandApi`, `RecruitmentInterviewCommandApi`, `RecruitmentEmailDeliveryCallbackApi`, `RecruitmentAnalyticsExportApi`, `ResumeAnalysisPublisher`; recruitment-owned events and identity helpers | `recruitment_tenant_settings`, `recruitment_jobs`, `recruitment_interview_templates`, `recruitment_interview_template_revisions`, `recruitment_candidates`, `recruitment_applications`, `recruitment_interviews`, `recruitment_interview_call_attempts`, `recruitment_twilio_callback_inbox`, `recruitment_public_jobs`, `recruitment_application_email_tokens`, `recruitment_candidate_sessions`, `recruitment_application_cvs`, `recruitment_cv_analyses`, `recruitment_cv_analysis_inbox`, `recruitment_availability_windows`, `recruitment_availability_exceptions`, `recruitment_interview_invitation_tokens`, `recruitment_candidate_email_deliveries`, `recruitment_interview_event_inbox`, `recruitment_interview_transcript_turns`, `recruitment_interview_results`, `recruitment_interview_section_results`, `recruitment_interview_question_results`, `recruitment_interview_score_evaluations`, `recruitment_interview_provider_usage`, `recruitment_interview_recordings`, `recruitment_recording_operations`, `recruitment_tenant_activation`, `recruitment_privacy_deletion_requests` |
| `platform` | Internal CacaNode administration HTTP surface and operator seed command | Calls only owner APIs such as `PlatformIdentityApi`, `PlatformStaffApi`, `TenantKindApi`, and `RecruitmentPlatformAdministrationApi` | No owned tables; no direct JPA, JDBC, Redis, object-storage, or Docker access |
| `support` | Customer support tickets, ticket notes, assignment, priority, and status | `SupportAnalyticsExportApi`; support REST controllers remain module-owned | `tickets`, `ticket_notes` |
| `integration` | Webhook endpoints, encrypted secrets, webhook outbox creation, dispatch, attempts, and retries | No general synchronous business API; consumes producer events | `webhook_endpoints`, `webhook_outbox`, `webhook_deliveries` |
| `notification` | In-app notifications and transactional email reactions | No general synchronous business API; consumes producer events | `notifications` |
| `analytics` | Dashboard and 7/30/90-day analytics from event-built projections | `AnalyticsReadApi`, `AnalyticsProjectionRebuildApi` | `analytics_tenant_projection`, `analytics_user_projection`, `analytics_invitation_projection`, `analytics_document_projection`, `analytics_conversation_projection`, `analytics_message_projection`, `analytics_ticket_projection`, `analytics_recruitment_job_projection`, `analytics_recruitment_application_projection`, `analytics_recruitment_interview_projection` |
| `common` | Shared technical infrastructure only | Not a business API | `audit_logs`, `module_event_outbox`, `module_event_inbox` |
| `bootstrap` | Application composition, security wiring, event registry, startup commands, and readiness | Not a business API | No business tables |

The document ingestion outbox/inbox is owned by `document` and handles its ingestion transport.
The module event outbox/inbox is shared technical infrastructure owned by `common` and handles
durable reactions inside the modular monolith.

## Choosing an API or an event

Use a synchronous API when the caller needs the answer before it can continue:

- authenticate an integration token;
- validate an external chat before creating a ticket;
- consume message quota before accepting a chat turn;
- validate citations before returning an answer;
- obtain usage totals for a billing response.

Use a durable event when the producer is announcing a completed fact and consumers can react
independently:

- tenant, user, invitation, or entitlement changed;
- billing activated or a quota threshold was reached;
- document state changed or a document was deleted;
- conversation started or closed, or a message was recorded;
- ticket was created or its status changed;
- notification email should be sent;
- webhook outbox entries or analytics projections should be updated;
- refresh tokens should be revoked after user deactivation.

A useful test is: "Would the producer transaction need the consumer's return value?" If yes, use
an API. If no, publish a fact. Do not use events to hide a synchronous request/response call, and
do not use a synchronous consumer call for an independent side effect.

## Durable module events

### Producer workflow

A producer publishes inside the same transaction as its business mutation:

```java
@Transactional
public void changeStatus(...) {
    // Mutate the producer-owned aggregate.
    durableEventPublisher.publish(
            "support.ticket.status-changed.v1",
            1,
            new TicketStatusChangedEvent(...));
}
```

The stable type is an externalized persistence contract. Once deployed, do not rename it or reuse
it for a different payload. Add a new version when the serialized contract changes incompatibly,
and register the type/version in `bootstrap.config.ModuleEventRegistryConfig`.

The durable flow is:

```text
producer transaction
  -> insert JSON into module_event_outbox
  -> commit
scheduled relay locks a due batch
  -> registry deserializes stable type + version
  -> Spring publishes the typed event synchronously
  -> each consumer claims its inbox key and commits its own mutation
  -> relay marks the event PUBLISHED after every consumer returns
```

If publication or a consumer fails, the relay records the error and retries with bounded
exponential backoff. After the configured attempt limit, the event is marked `DEAD` for operator
attention.

### Consumer workflow

Every durable consumer needs a stable, unique consumer name and must claim the event in the same
transaction as its mutation:

```java
@EventListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onTicketCreated(TicketCreatedEvent event) {
    if (!inboxService.claim("integration.webhook.ticket-created")) {
        return;
    }

    webhookService.enqueue(...);
}
```

This produces at-most-once successful processing per `(consumer_name, event_id)` while allowing a
failed consumer to be retried. On redelivery, consumers that already committed their inbox row are
skipped, while the failed consumer gets another attempt.

Consumer requirements:

- use synchronous `@EventListener`;
- use `REQUIRES_NEW` so each consumer commits independently of the relay and other consumers;
- claim before mutating;
- do not swallow failures that should trigger retry;
- do not add `@Async` to a durable listener;
- keep the consumer name stable across refactors;
- make external effects logically idempotent where the downstream system supports idempotency.

The core implementation lives in:

- `common.event.durable.DurableEventPublisher`;
- `common.event.durable.ModuleEventOutboxRelay`;
- `common.event.durable.ModuleEventInboxService`;
- `bootstrap.config.ModuleEventRegistryConfig`.

## Analytics projections

`analytics` is an owned read model, not permission to query every table.

Runtime analytics queries read only the ten `analytics_*_projection` tables: tenant, user,
invitation, document, conversation, message, ticket, recruitment job, recruitment application,
and recruitment interview. Live projection updates are built from producer-owned durable events.
Repair and reconciliation use paginated snapshot APIs owned by tenant, document, chat, support,
and recruitment, including `RecruitmentAnalyticsExportApi` for the three recruitment projections.

The projection rebuild boundary is `AnalyticsProjectionRebuildApi`. The implementation is
`AnalyticsProjectionRebuildService`, and a startup rebuild can be enabled with:

```text
app.analytics.rebuild-on-startup=true
```

Run a rebuild after a rollback interval, recovery from projection failure, or detected drift
before relying on the new application's analytics responses.

The message projection has a deliberate privacy boundary:

- user question text is stored for popular-question analytics;
- assistant response duration is stored for response-time analytics;
- assistant answer content is never stored in analytics.

Do not add assistant answer text, citations, prompts, or full transcripts to an analytics event or
projection without an explicit privacy review.

Flyway V24 creates and backfills the core projection tables, and V32 does the same for the three
recruitment projections. The default cache prefix is `ccn:v2`, so dashboard and analytics data
written before the refactor cannot be reused accidentally.

## Where new code belongs

Use these placement rules before creating a class:

| New code | Location |
| --- | --- |
| Cross-module callable interface | Owning module's `api` package |
| Boundary command, result, enum, or exception | Same owning `api` package |
| Published business fact | Producer's `api.event` package |
| REST request/response used only by one module's controllers | That module's internal `dto` package |
| JPA entity or repository | Owning module's `model` or `repository` package |
| Owner-specific JDBC query | Owning module's `query` or `repository` package |
| Cross-module wiring or registry | `bootstrap` |
| Business-neutral reusable infrastructure | `common` |
| Reporting state | An analytics-owned projection populated from events/exports |

Examples:

- A new support-ticket field belongs in `support`, its support DTOs/API events, and the existing
  support-owned table. It does not belong in tenant merely because the ticket has a tenant ID.
- A new tenant value needed by billing is returned by a tenant API or copied through a tenant
  event. Billing must not add a query against `tenants`.
- A new email reaction belongs in notification, consuming an event owned by the module where the
  fact occurred. The producer must not call `NotificationService`.
- A new dashboard metric gets an analytics projection field/table and owner event/export data. It
  must not add an operational cross-table join to `AnalyticsReadService`.
- A generic object-storage implementation can live in `common.storage`; document-specific upload
  policy stays in document.

## Database and migration rules

Flyway remains one ordered migration stream because the application deploys atomically. Table
ownership still applies to every statement within a migration.

When changing the schema:

1. identify the owning module;
2. keep the table name and external database contract stable unless a coordinated migration says
   otherwise;
3. prefer additive, backward-compatible changes;
4. preserve existing foreign keys unless the migration explicitly replaces them;
5. update the owning entity/repository and its boundary events or snapshots;
6. update analytics projection/backfill logic when the field affects reporting;
7. add migration coverage with representative data for production-specific SQL.

Migrations are excluded from the runtime SQL ownership scan because a migration may coordinate
several modules in one atomic deployment. Runtime Java code receives no such exemption.

## Enforcement

`ModularMonolithArchitectureTest` enforces that:

- business modules import another module only through `api` or `api.event`;
- API contracts do not leak internal entities, repositories, services, queries, or DTOs;
- `common` does not depend on a business module;
- the business-module graph is acyclic;
- every type ending in `ModuleApi` is an interface;
- JDBC and `EntityManager` access stays in an owner `repository`/`query` package, `common`, or
  `bootstrap`.

`TableOwnershipTest` scans runtime Java persistence references and rejects access to a known table
from a non-owner module.

These tests have no violation allowlist. If an architecture test fails, fix the boundary rather
than weakening the rule or adding an exception for the new dependency.

Run the complete API verification before opening a PR:

```sh
cd api
sh mvnw test
git diff --check
```

Add focused tests at the owner API or event boundary as well as behavior-level controller/service
tests. Durable flows should cover producer rollback, relay retry/dead-letter behavior, inbox
deduplication, and logical consumer idempotency when relevant.

## Operations and recovery

Flyway V24 must complete before the refactored application is considered ready. The
`modularReadiness` health contributor reports:

- whether V24 completed successfully;
- pending module-event count;
- dead module-event count;
- age of the oldest pending event.

The readiness group includes `modularReadiness`. Its default maximum pending age is 30 seconds,
configurable through `app.module-events.readiness-max-pending-age-seconds`. Normal analytics
freshness should remain under five seconds.

Readiness becomes `DOWN` when the migration is missing, the health query fails, or pending event
age exceeds the configured limit. A terminal `DEAD` event is reported as a degraded operational
state through the health details and platform failure APIs, but it does not make an otherwise
serving API reject traffic indefinitely. Operators must still repair or replay every dead event.

Monitor outbox age, retries/dead letters, consumer failures, projection lag, and analytics endpoint
errors. A `DEAD` event or a growing pending age is an operational incident, not a reason to bypass
the event boundary.

Because V24 is additive, the previous application version can run after a rollback. Before
re-enabling the refactored version after such an interval, rebuild analytics projections so changes
made during the rollback window are reconciled.

## New-developer checklist

Before implementing a change, answer these questions:

1. Which module owns the behavior?
2. Which module owns every table involved?
3. Does the caller need an immediate value, or is this an independent reaction?
4. If synchronous, is there a capability-focused owner API with API-owned value types?
5. If asynchronous, is the event owned by the producer and published durably?
6. Does every durable consumer claim a stable inbox name in the same transaction as its mutation?
7. Are all imported business types from the same module, another module's `api`, or
   `api.event`?
8. Does any API type leak an entity, repository, internal DTO, servlet type, or implementation?
9. Does runtime SQL touch only tables owned by the current module?
10. Are REST, JSON, protobuf, authentication, and database contracts still compatible?

Common mistakes to avoid:

- importing an implementation because it is already a Spring bean;
- sharing a JPA entity across module boundaries instead of using a UUID and owner API;
- placing business DTOs or orchestration in `common`;
- putting business behavior in `bootstrap` because it can see all modules;
- publishing a durable event outside the producer transaction;
- using `@Async` or after-commit listeners for durable consumers;
- swallowing a consumer exception and causing the relay to mark an incomplete event as published;
- reading another module's table for a convenient report;
- adding assistant answer content to analytics;
- fixing an architecture-test failure by relaxing the rule.

If a design cannot satisfy these four rules, stop and redesign the boundary before adding code.
