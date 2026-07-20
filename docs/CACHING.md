# Caching

This document is the operational reference for CacaNode's Spring and FastAPI caches. Redis stores
bounded cache values, while the semantic answer cache additionally uses a dedicated Qdrant
collection as a privacy-safe similarity index. It describes implemented behavior, production
rollout gates, and the evidence required before a cache is enabled.

## Current status

The cache infrastructure and all cache adapters are implemented. Every optional data cache remains
disabled by default in the checked-in configuration.

- Redis rate limiting remains active independently of the cache feature flags.
- PostgreSQL remains authoritative for identity, business, billing, document metadata, knowledge
  revisions, and chat data.
- Qdrant knowledge collections, Kuzu, SeaweedFS, RabbitMQ, Ollama, and configured model providers
  retain their existing roles. The semantic answer Qdrant collection is derived, disposable cache
  metadata and is never authoritative.
- Redis read, write, timeout, deserialization, and connection failures fail open to the
  authoritative path.
- No distributed lock, request coalescing, prewarming, or background cache refresh is active.
- Automated correctness and failure-path tests are present, but matching cache-disabled and
  cache-enabled production or seeded-stack performance evidence has not been attached. Keep all
  optional caches disabled in production until the applicable rollout gate is satisfied.

## Core rules

### Cache-aside reads

The standard Redis cache-aside adapters follow the same sequence in both services. Semantic answer
lookup adds the Qdrant tier described later in this document.

1. Read Redis.
2. Return a valid hit.
3. On a miss, corrupt value, timeout, or Redis error, invoke the authoritative backend.
4. Return the authoritative result even if the Redis fill fails.
5. Fill only successful, serializable results with a bounded TTL.

Disabled cache bypasses invoke the authoritative backend directly. Redis must never become a
startup dependency or turn an otherwise successful authoritative read into an HTTP failure.

### Keys and tenant isolation

Keys use the versioned `ccn:v1` namespace. Tenant-owned data includes a trusted tenant ID derived
from authentication or a server-side relationship. Free-form text and filter objects are
canonicalized and SHA-256 hashed.

Never put raw bearer tokens, email addresses, prompts, messages, queries, document text, model
identities, or unverified request tenant IDs in keys, metric labels, or logs.

### Serialization

- Spring business values use UTF-8 JSON envelopes:
  `{"schema_version":1,"payload":...}`.
- Embeddings use the `CCNE` version-1 float32 binary format.
- Retrieval results use schema-versioned JSON containing validated `RetrievedChunk` values.
- Semantic answers use schema-versioned JSON containing the final answer, validated citations,
  original model token counts, scope metadata, and expiry.
- Unknown schemas and malformed values are deleted best-effort and treated as misses.
- Java native serialization and Python pickle are prohibited.

### TTL jitter

Base TTLs receive the configured jitter, 10 percent by default. Jitter reduces synchronized expiry
without changing the documented upper bounds for authorization-sensitive data. Semantic answer
entries are the exception: they use an exact, non-jittered TTL so Redis and Qdrant expiry remain
aligned.

## Cache catalog

| Cache | Owner | Key shape | Base TTL | Invalidation or versioning |
| --- | --- | --- | ---: | --- |
| Widget configuration | Spring | `ccn:v1:widget-config:tenant:<tenant-id>` | 120s | Exact tenant-key deletion after widget, chatbot, or branding changes |
| Customer answer prompt | Spring | `ccn:v1:customer-answer-prompt:tenant:<tenant-id>` | 120s | Exact deletion after prompt update |
| Billing account | Spring | `ccn:v1:billing-account:tenant:<tenant-id>` | 30s | Exact deletion after billing, entitlement, member, and document mutations |
| Workspace | Spring | `ccn:v1:workspace:tenant:<tenant-id>` | 300s | Exact deletion after provisioning or workspace/chatbot/knowledge-base changes |
| Dashboard summary | Spring | `ccn:v1:dashboard-summary:tenant:<tenant-id>` | 20s | Short TTL plus convenient member/document invalidations |
| Analytics | Spring | `ccn:v1:analytics:tenant:<tenant-id>:scope:<scope>:start:<date>:end:<date>` | 60s | TTL-based; entitlement is checked before lookup |
| User directory | Spring | `ccn:v1:user-directory:tenant:<tenant-id>` | 30s | Exact deletion after invitation or member lifecycle changes |
| Document list | Spring | `ccn:v1:documents:tenant:<tenant-id>:kb:<kb-id>:gen:<n>:filters:<sha256>` | 15s | Tenant/knowledge-base generation increment after document mutations |
| Embedding | FastAPI | `ccn:v1:embedding:model:<sha256>:dim:<n>:norm:1:text:<sha256>` | 24h | Model, dimension, normalization version, and text digest isolate entries |
| Retrieval result | FastAPI | `ccn:v1:retrieval:tenant:<tenant-id>:kb:<kb-id>:rev:<n>:visible:<identity>:config:<sha256>:query:<sha256>` | 120s | Spring-supplied authoritative knowledge-base revision and complete retrieval configuration isolate entries |
| Semantic answer | FastAPI | `ccn:v1:semantic-answer:<scope-sha256>:query:<query-sha256>` | 3600s, no jitter | Scope includes tenant, revision, visibility, history, prompts, model, embedding, and retrieval configuration |
| Generation result | FastAPI | `ccn:v1:generation-result:<generation-id>` | 600s | Same generation ID replays the protobuf response so an `UNAVAILABLE` retry does not repeat model work |

### Spring business reads

Spring's shared versioned JSON cache wraps widget configuration, customer answer prompts, billing
accounts, workspaces, dashboard summaries, analytics, user directories, and document lists.

- Fills and invalidations run after commit when transaction synchronization is active.
- Rollbacks neither fill nor invalidate Redis.
- Directory entries cache a tenant snapshot and apply viewer-specific `currentUser` decoration and
  invitation-expiry evaluation after the read.
- Public widget requests still enforce active status and allowed origins after a cache hit.
- Billing mutation responses use authoritative account loading rather than reading a potentially
  stale cache entry.
- Document filter hashes include request mode, page, size, NFC-normalized search text, status,
  type, and visibility. Raw search text is absent from Redis keys and observability data.
- A failed document-generation read bypasses the document cache. A failed generation increment
  leaves any stale list bounded by the 15-second TTL.
- The platform customer-answer default is generated from the tenant display name. It requires
  polite responses to every message, handles greetings and other light conversation without
  citations, and preserves source grounding for tenant-specific facts. Custom tenant instructions
  cannot override tenant identity, isolation, grounding, or polite and respectful behavior.

### Embeddings

FastAPI canonicalizes CRLF and CR line endings to LF and applies Unicode NFC. It deliberately does
not lowercase or collapse whitespace. Query misses invoke Ollama and fill one vector. Document
batches deduplicate identical canonical text, load entries with multi-key operations, send only
unique misses to Ollama, and restore the original ordering.

Malformed dimensions, headers, lengths, or non-finite values are misses. Cache failures preserve
Ollama batching, error handling, and output ordering.

### Retrieval results

FastAPI caches the complete hybrid retrieval result before answer generation. Prompts, histories,
calculations, and authorization failures are never stored in the retrieval cache.

`knowledge_bases.search_revision` is authoritative and changes after searchable-content or
visibility mutations, including successful ingestion, reindex, cleanup, deletion, completion, and
visibility updates. The key also includes the visible-document identity and a digest of the full
retrieval configuration. Cached chunks are schema-validated, bounded, and checked against the
allowed document set before use.

### Generated semantic answers

The semantic answer cache is a separate, default-off optimization for grounded final answers. Redis
stores the answer, validated citations, source-model input/output token counts, schema version, and
expiry for exactly one hour. This TTL is intentionally not jittered so the Redis expiry and Qdrant
point expiry stay aligned.

The feature is effective only when `CACHE_ENABLED=true` and
`SEMANTIC_ANSWER_CACHE_MODE` is `shadow` or `serve`.

#### Request flow by mode

Spring authorization happens first. In one transaction Spring resolves idempotency, consumes
message quota, persists the current user message, and commits a pending turn before calling gRPC.
A cached answer still produces a normal assistant message and never reverses the quota charge.

| Mode | Exact lookup | Semantic lookup | Served response |
| --- | --- | --- | --- |
| `off` | None | None | Existing RAG flow, unchanged |
| `shadow` | Before query embedding | After query embedding | Always the fresh RAG response |
| `serve` | Before query embedding | After query embedding when exact misses | A valid cache hit, otherwise fresh RAG |

An exact serve hit skips query embedding, retrieval, calculation, reranking, and answer generation.
A semantic serve hit reuses the already-generated query embedding and skips retrieval, calculation,
reranking, and answer generation. Both hit types persist the cached assistant message normally and
return the unchanged `AssistantMessageResponse` contract.

Recognized conversational turns—including greetings, assistant-identity questions, thanks,
farewells, light small talk, and basic capability questions—take a deterministic path before cache
lookup or retrieval. They still consume quota and persist both messages, but return no document
citations and never populate the semantic answer cache.

Shadow mode still runs embedding, retrieval, calculation, reranking, and model generation. When a
candidate and fresh eligible answer are both available, it records citation-set overlap and an
ephemeral answer-embedding cosine comparison. Answer comparison vectors are not persisted. The
fresh eligible response is written for later observations.

#### Lookup tiers

An exact scoped Redis lookup uses:

```text
ccn:v1:semantic-answer:<scope-sha256>:query:<query-sha256>
```

If exact lookup misses, Qdrant searches the dedicated `semantic_answer_cache_v1` collection using
the named `query_v1` cosine vector. Searches require exact scope and guard hashes, filter to
`expires_at > now`, inspect at most five candidates, and accept only similarity `>= 0.97`.
Candidate ordering is deterministic for equal scores. Missing, expired, corrupt, mismatched, or
out-of-scope Redis payloads are skipped and normal RAG continues.

Redis is written before Qdrant. A Qdrant write failure can therefore leave a usable exact entry.
Redis and Qdrant lookup or write failures always fail open; neither service is a startup dependency
for answer caching.

#### Scope and semantic guards

The scope digest isolates tenant, tenant display name, chatbot, knowledge base and authoritative
revision, channel, locale, customer-visible document set, prior external prompt history, tenant
answer prompt, prompt/schema version, model provider and settings, embedding identity, and the
complete retrieval configuration fingerprint.

For `WIDGET` and `CUSTOM_API` conversations, Spring supplies the first 20 messages that existed
before the current user message and FastAPI hashes only their exact `role` and `content` values. The
current query is deliberately excluded so a paraphrase can match under identical prior context.
The prompt history is reconstructed so model behavior remains unchanged. Employee playground uses
a stable empty-history digest and the visible-document identity `all`.

A separate guard digest requires the same deterministic query profile and exact normalized sets of
negation markers, numbers, dates, and currencies. Identifiers are normalized and hashed before they
enter the guard. Semantic similarity never overrides a guard mismatch.

#### Eligibility and validation

Calculations, calculation clarifications, explicit English or Vietnamese ticket/action requests,
responses with actions, empty or citationless answers, no-information responses, and ungrounded
answers are ineligible. Authorization failures, quotas, timeouts, and backend errors never fill the
cache.

Every Redis payload is schema-validated before use. Its scope, guard, exact-query hash, knowledge
revision, visible-document hash, and expiry must match the current request. Every citation must be
well formed, remain within the current customer-visible document set when one applies, and be
referenced by the cached answer. Invalid payloads are deleted best-effort and treated as misses.

#### Data retention and privacy

Qdrant contains no tenant ID, query, history, prompt, answer, citation text, document text, or model
ID. Its point payload is limited to scope, guard, and query hashes, integer expiry, and the derived
Redis identity. Redis does contain generated answer and citation content for up to one hour, so its
access controls, memory policy, incident handling, and retention review must treat this as tenant
conversation data. Deterministic Qdrant point IDs are derived from the scope and exact-query hashes.
The semantic collection can be deleted and rebuilt without losing authoritative application data.

## Configuration

All defaults are safe-off:

```dotenv
CACHE_ENABLED=false
CACHE_KEY_PREFIX=ccn:v1
CACHE_TTL_JITTER_PERCENT=10
REDIS_CONNECT_TIMEOUT_SECONDS=1
REDIS_OPERATION_TIMEOUT_SECONDS=1


BUSINESS_READ_CACHE_ENABLED=false
WIDGET_CONFIG_CACHE_ENABLED=false
WIDGET_CONFIG_CACHE_TTL_SECONDS=120
CUSTOMER_ANSWER_PROMPT_CACHE_ENABLED=false
CUSTOMER_ANSWER_PROMPT_CACHE_TTL_SECONDS=120
BILLING_ACCOUNT_CACHE_ENABLED=false
BILLING_ACCOUNT_CACHE_TTL_SECONDS=30
WORKSPACE_CACHE_ENABLED=false
WORKSPACE_CACHE_TTL_SECONDS=300
DASHBOARD_CACHE_ENABLED=false
DASHBOARD_CACHE_TTL_SECONDS=20
ANALYTICS_CACHE_ENABLED=false
ANALYTICS_CACHE_TTL_SECONDS=60
USER_DIRECTORY_CACHE_ENABLED=false
USER_DIRECTORY_CACHE_TTL_SECONDS=30
DOCUMENT_LIST_CACHE_ENABLED=false
DOCUMENT_LIST_CACHE_TTL_SECONDS=15

EMBEDDING_CACHE_ENABLED=false
EMBEDDING_CACHE_TTL_SECONDS=86400
RETRIEVAL_CACHE_ENABLED=false
RETRIEVAL_CACHE_TTL_SECONDS=120
SEMANTIC_ANSWER_CACHE_MODE=off
SEMANTIC_ANSWER_CACHE_TTL_SECONDS=3600
SEMANTIC_ANSWER_CACHE_SIMILARITY_THRESHOLD=0.97
SEMANTIC_ANSWER_CACHE_COLLECTION=semantic_answer_cache_v1
SEMANTIC_ANSWER_CACHE_VECTOR_NAME=query_v1
SEMANTIC_ANSWER_CACHE_CANDIDATE_LIMIT=5
SEMANTIC_ANSWER_CACHE_CLEANUP_BATCH_SIZE=1000
```

Enablement requirements:

| Cache group | Required switches |
| --- | --- |
| One Spring business domain | `CACHE_ENABLED=true`, `BUSINESS_READ_CACHE_ENABLED=true`, and exactly one domain flag initially |
| Embedding | FastAPI: `CACHE_ENABLED=true` and `EMBEDDING_CACHE_ENABLED=true` |
| Retrieval | FastAPI: `CACHE_ENABLED=true` and `RETRIEVAL_CACHE_ENABLED=true` |
| Semantic answer shadow/serve | FastAPI: `CACHE_ENABLED=true` and `SEMANTIC_ANSWER_CACHE_MODE=shadow` or `serve` |

Rate limiting does not require `CACHE_ENABLED` and remains available when every optional cache is
off.

## Observability

The cache layer exports controlled, low-cardinality metrics:

```text
cacanode_cache_operations_total{service,cache,outcome}
cacanode_cache_operation_seconds{service,cache,operation}
cacanode_cache_payload_bytes{service,cache}
cacanode_redis_operations_total{service,component,operation,outcome}
cacanode_cache_authoritative_seconds{service,cache,outcome}
cacanode_cache_authoritative_loads_in_flight{service,cache}
cacanode_cache_same_key_overlaps_total{service,cache}
cacanode_cache_same_key_concurrency{service,cache}
cacanode_semantic_answer_cache_operations_total{service,mode,tier,outcome}
cacanode_semantic_answer_cache_lookup_seconds{service,mode,tier,outcome}
cacanode_semantic_answer_cache_similarity{service,mode,tier}
cacanode_semantic_answer_cache_shadow_citation_overlap{service,mode,tier}
cacanode_semantic_answer_cache_shadow_answer_similarity{service,mode,tier}
cacanode_semantic_answer_cache_avoided_llm_requests_total{service,mode,tier}
cacanode_semantic_answer_cache_avoided_tokens_total{service,mode,tier,token_type}
```

The same-key tracker is process-local and observational. It hashes the key before retaining
temporary state, removes the entry after success, error, timeout, or cancellation, and never delays
a caller. Disabled bypasses are excluded from stampede measurements.

Semantic answer metrics use only the controlled labels `service`, `mode`, `tier`, `outcome`, and
`token_type`. Queries, answers, tenant identifiers, prompts, histories, document identifiers, model
identities, and similarity candidates must never become labels.

Also monitor Redis `used_memory`, key count, keyspace hits and misses, evictions, rejected
connections, connected clients, and command latency. Production uses `noeviction`; a full Redis
instance may reject cache writes, which must not affect authoritative responses.

## Measurement and rollout

### Baseline runner

Use the same seeded tenant, credentials, data, deployment, and sample count for disabled and enabled
runs. `BASELINE_ACCESS_TOKEN` is an issued tenant-admin JWT, not `TOKEN_KEY`.
`BASELINE_INTEGRATION_TOKEN` is an issued `api:chat` credential, not
`INTEGRATION_TOKEN_PEPPER`. Credentials are supplied through environment variables and are not
written to the report.

#### What must be repeated

Not every setup step is required before every report:

| Action | When it is required |
| --- | --- |
| `make db-seed` | Once for a new or reset local PostgreSQL database. It is not a per-report step. |
| `umask 077` | Once in each shell that will create the temporary credential file. It is a shell setting, so the current directory does not matter. |
| `make -s baseline-credentials` | Before the first report, after the access JWT expires, after credentials are otherwise invalidated, or when starting from a shell that has no usable saved credentials. One credential set may be reused for several consecutive reports while it remains valid. Running the command again revokes and replaces the previous baseline integration token. |
| Change cache flags and restart a service | Whenever the cache configuration under test changes. Restart FastAPI for embedding, retrieval, or semantic-answer flags. Restart Spring for Spring business-cache flags. |
| Flush local Redis database 15 | Before each independent cold-cache configuration run. Do not flush between warmups and measured samples; the runner performs those in one invocation. Flushing is optional only when deliberately measuring an already-warm cache. |
| Set `BASELINE_RAG_QUERY` | For reports that must exercise embedding or retrieval. It may be reused across all comparison runs. |

The runner's three warmup requests intentionally fill an enabled cache. Its following 30 requests
measure the warm-cache path. Flushing Redis before the invocation gives every configuration the
same cold starting state without removing the cache entries between warmups and samples.

Only flush the dedicated local test database. Never apply the commands below to production or to a
Redis database shared with data that must be retained.

#### One-time local setup

Start Spring and FastAPI, then seed the development account. The seed is required again only after
the local PostgreSQL data is reset:

```bash
cd api
make db-seed
```

The seeded login is `admin@cacanode.local` / `Cacanode@123`. Never use this development seed in a
shared or production environment.

Choose one dedicated local Redis database for the benchmark. With the repository Compose file,
Redis is exposed to host processes on port `16379`; database 15 is reserved for isolated local
tests. FastAPI and the baseline runner must point to the same database:

```dotenv
# rag-chatbot-fastapi/.env
REDIS_URL=redis://localhost:16379/15
```

Edit `rag-chatbot-fastapi/.env`, not `.env.example`. The example file is only a template and does
not change a running service.

#### Credentials for a benchmark session

With Spring running on `http://localhost:8080`, create the credential file and load it into the
current shell:

```bash
cd api
umask 077
make -s baseline-credentials > /tmp/cacanode-baseline.env
source /tmp/cacanode-baseline.env
cd ../rag-chatbot-fastapi
export BASELINE_INFERENCE_METRICS_URL=http://localhost:18000/metrics/
export BASELINE_REDIS_URL=redis://127.0.0.1:16379/15
export BASELINE_RAG_QUERY='Ask a stable question answered by the seeded knowledge base'
```

The credentials command prints shell exports for the JWT, integration token, knowledge-base ID,
and playground chatbot ID. `BASELINE_PLAYGROUND_CHATBOT_ID` therefore normally comes from this
file; it does not need to be entered separately. The access JWT expires normally. If a report starts
returning `401`, rerun the credential command and source the new file. A FastAPI restart alone does
not require new credentials.

`BASELINE_RAG_QUERY` is read by the benchmark process, not by the running FastAPI service. Export it
in the shell as shown above. Keep the exact query unchanged when comparing configurations.

#### Repeatable embedding and retrieval comparison

For each configuration, stop FastAPI, update the actual `rag-chatbot-fastapi/.env`, and start
FastAPI again. Changing these values without restarting FastAPI does not reconfigure the running
process. Keep semantic-answer caching off when isolating embedding or retrieval behavior.

Use these configurations:

```dotenv
# Cache-disabled baseline
CACHE_ENABLED=false
EMBEDDING_CACHE_ENABLED=false
RETRIEVAL_CACHE_ENABLED=false
SEMANTIC_ANSWER_CACHE_MODE=off
```

```dotenv
# Embedding cache only
CACHE_ENABLED=true
EMBEDDING_CACHE_ENABLED=true
RETRIEVAL_CACHE_ENABLED=false
SEMANTIC_ANSWER_CACHE_MODE=off
```

```dotenv
# Retrieval cache only
CACHE_ENABLED=true
EMBEDDING_CACHE_ENABLED=false
RETRIEVAL_CACHE_ENABLED=true
SEMANTIC_ANSWER_CACHE_MODE=off
```

```dotenv
# Embedding and retrieval caches together
CACHE_ENABLED=true
EMBEDDING_CACHE_ENABLED=true
RETRIEVAL_CACHE_ENABLED=true
SEMANTIC_ANSWER_CACHE_MODE=off
```

After restarting FastAPI for a configuration, reset database 15 from the repository root. This
uses the Redis CLI inside the container, so a host installation of `redis-cli` is not required:

```bash
cd ..
docker compose -f ./docker-compose.yml exec -T redis redis-cli -n 15 FLUSHDB
docker compose -f ./docker-compose.yml exec -T redis redis-cli -n 15 DBSIZE
cd rag-chatbot-fastapi
```

`DBSIZE` must print `0` before the benchmark starts. Then run exactly one report for that
configuration:

```bash
make baseline-cache > artifacts/cache-disabled.json

make baseline-cache-enabled > artifacts/embedding-cache-enabled.json
make baseline-cache-enabled > artifacts/retrieval-cache-enabled.json
make baseline-cache-enabled > artifacts/embedding-retrieval-cache-enabled.json
```

The four commands above are alternatives, not a batch to execute under one configuration. Before
each command, select the matching `.env` block, restart FastAPI, and flush database 15. The disabled
run uses `make baseline-cache`; every enabled configuration uses `make baseline-cache-enabled`.

The Make targets only set the report's `cache_enabled` label. They do not enable or disable a cache
inside an already-running Spring or FastAPI process. The runtime `.env` flags and service restart
determine what is actually being tested.

You do not need fresh credentials between these four runs if the current credentials still work.
If a run returns `401`, refresh and source the credentials, flush Redis again, and rerun that
configuration so the report contains a complete, comparable sample.

#### Validate each report

Before comparing latency, verify the report is valid:

- All expected endpoint and RAG status counts are `200`.
- `optional_rag` is non-null. If it is null, `BASELINE_RAG_QUERY` or
  `BASELINE_PLAYGROUND_CHATBOT_ID` was missing from the benchmark shell.
- `ai_metrics.before` and `ai_metrics.after` are non-empty. If they are empty, confirm FastAPI is
  running and `BASELINE_INFERENCE_METRICS_URL` points to its metrics endpoint.
- The report's `embedding_cache_probe.redis` snapshots refer to database 15 and show activity for
  the enabled configuration.
- For an embedding-only run, expect one initial embedding miss/write followed by embedding hits,
  while retrieval is bypassed.
- For a retrieval-only run, expect one initial retrieval miss/write followed by retrieval hits.
  Query embedding still runs for every request, so retrieval-only latency may improve much less
  than embedding-only latency.
- For both caches together, expect one initial miss/write and subsequent hits for both embedding
  and retrieval.

The latency baseline JSON files above are not retrieval-ranking files. If a retrieval evaluation
dataset has been run separately and its per-query rankings were recorded with caches disabled and
enabled, compare those ranking artifacts with:

```bash
make compare-retrieval-cache \
  CACHE_DISABLED_RESULTS=artifacts/retrieval-cache-disabled.json \
  CACHE_ENABLED_RESULTS=artifacts/retrieval-cache-enabled.json
```

The comparison rejects ranking changes and absolute regressions greater than `0.001` for recall,
MRR, NDCG, or no-answer precision.

### Required rollout evidence

Enable one cache domain at a time in staging. Record:

- Equivalent HTTP responses and authorization decisions.
- Cache hit rate after the first canonical request.
- Authoritative backend call reduction.
- Endpoint or component p50 and p95.
- Cache payload distribution.
- Redis memory and key-count deltas.
- No eviction increase during the sample.
- For embeddings and retrieval, identical rankings and no quality regression beyond `0.001`.
- Redis-unavailable behavior returning the authoritative result.

Do not promote a cache to production merely because Redis keys appear. The checked-in repository
does not contain a valid seeded disabled/enabled comparison, so production enablement is still an
operational decision requiring attached evidence.

### Stampede and cold-start evidence

The guarded harness defaults to 20 synchronized callers, five cold rounds, and five matching warm
rounds for billing account, analytics, public widget configuration, direct query embedding, and
direct hybrid retrieval without an LLM call.

Cold resets are destructive and permitted only with an affirmative flag against an explicitly
configured loopback Redis database 15. Database 0, remote hosts, missing URLs, and query-string
database overrides are rejected.

```bash
cd rag-chatbot-fastapi
export PHASE5_REDIS_URL=redis://127.0.0.1:6379/15
export PHASE5_ACCESS_TOKEN=...
export PHASE5_INTEGRATION_TOKEN=...
export PHASE5_TENANT_ID=...
export PHASE5_KNOWLEDGE_BASE_ID=...
export PHASE5_QUERY=...
export CACHE_ENABLED=true
export EMBEDDING_CACHE_ENABLED=true
export RETRIEVAL_CACHE_ENABLED=true
make phase5-harness PHASE5_HARNESS_ARGS=--allow-cold-cache-reset
```

Create `artifacts/phase-5-production.json` using this schema and populate it from a passive
production window:

```json
{
  "schema_version": 1,
  "window_start": "ISO-8601 timestamp",
  "window_end": "ISO-8601 timestamp",
  "candidates": {
    "billing-account": {
      "window_days": 7,
      "authoritative_loads": 0,
      "same_key_overlaps": 0,
      "same_key_concurrency_p95": 0,
      "backend_impact_correlated": false
    },
    "analytics": {},
    "embedding": {},
    "retrieval": {},
    "widget-config": {
      "window_days": 7,
      "authoritative_loads": 0,
      "same_key_overlaps": 0,
      "same_key_concurrency_p95": 0,
      "backend_impact_correlated": false,
      "restart_cold_requests_affect_slo": false,
      "projected_cache_footprint_bytes": 0,
      "redis_memory_budget_bytes": 0
    }
  }
}
```

The analytics, embedding, and retrieval objects use the same five single-flight fields shown for
billing account. Classify the production and seeded artifacts with:

```bash
make phase5-report \
  PHASE5_PRODUCTION_OBSERVATION=artifacts/phase-5-production.json \
  PHASE5_SEEDED_REPORT=artifacts/phase-5-seeded.json
```

A single-flight candidate qualifies only when all of these hold:

- The production window covers at least seven days and contains at least 100 authoritative loads.
- Either overlaps are at least 5 percent and at least 50 loads, or p95 same-key concurrency is at
  least 3 and correlates with saturation, errors, or latency regression.
- One seeded cold round produces at least 3 authoritative calls for one logical key.
- Seeded authoritative p95 is at least 100 ms for Spring or 250 ms for embedding/retrieval.

Public widget prewarming additionally requires cold p95 above 250 ms, a cold-to-warm gap of at
least 100 ms, material endpoint-SLO impact after restart/deployment, and projected footprint below
5 percent of the Redis memory budget.

Query-frequency embedding prewarming remains declined because identity collection has not passed
privacy review. Retrieval, document, message, arbitrary-query prewarming, and background refresh
remain out of scope.

Until valid production and seeded evidence is attached, the classifier result is
`insufficient_data`; do not add a lock or prewarming implementation. If one cache qualifies, the
follow-up design must use `SET NX PX`, bounded waiting, a unique owner, fail-open fallback, and a
compare-and-delete Lua release.

### Semantic-answer rollout and cleanup

Deploy semantic answer caching in `off`, then use `shadow` in staging and production. Shadow mode
performs lookups and compares citation overlap plus ephemeral answer-embedding cosine similarity,
but always serves the fresh RAG response. Keep shadow mode for at least seven days and 200 eligible
semantic candidates, and manually review a tenant-authorized stratified sample of at least 100.
Serving requires at least 99 percent answer-equivalence precision, 100 percent valid citations, zero
tenant/visibility/negation/literal/calculation/action violations, lookup p95 overhead no greater than
50 ms, no material error increase, no Redis evictions, and acceptable Redis/Qdrant footprint.

Enable `serve` in staging first. Production serving remains default-off until those gates pass.
After serving, require fewer LLM calls and tokens, improved end-to-end p95 on hits, unchanged public
contracts, and no retrieval-quality or support-error regression. Roll back immediately with
`SEMANTIC_ANSWER_CACHE_MODE=off`; Redis entries expire within one hour and expired Qdrant points are
ignored even before deletion.

Run the bounded cleanup once daily. It is a dry run unless `--apply` is explicit, processes batches
of 1,000, stops after at most 10 batches, and only touches the dedicated semantic answer collection:

```bash
cd rag-chatbot-fastapi
make cleanup-semantic-answer-cache-dry-run
make cleanup-semantic-answer-cache
```

The direct command is equivalent and exposes the bounded run limit:

```bash
python -m app.maintenance.cleanup_semantic_answer_cache --max-batches 10
python -m app.maintenance.cleanup_semantic_answer_cache --max-batches 10 --apply
```

## Testing

Run the service gates:

```bash
cd api
sh mvnw test

cd ../rag-chatbot-fastapi
make check
```

Real Redis tests use dedicated database 15. The semantic integration test also requires a local
Qdrant endpoint:

```bash
REDIS_TEST_URL=redis://127.0.0.1:6379/15 make test

REDIS_TEST_URL=redis://127.0.0.1:6379/15 \
QDRANT_TEST_URL=http://127.0.0.1:6333 \
python -m pytest tests/test_semantic_answer_cache_integration.py
```

Coverage includes key isolation, schema validation, TTL jitter, multi-key embedding operations,
authoritative fallback, after-commit invalidation, rollback suppression, revision changes,
same-key overlap detection, tracker cleanup, Redis unavailability, guarded cold-reset safety,
semantic paraphrase hits, literal and intent guards, history isolation, cache-hit persistence and
quota accounting, provider token extraction, privacy-safe Qdrant payloads, and bounded cleanup.

## Rollback and recovery

Disable the narrowest domain switch first. Disabling a cache immediately restores authoritative
reads; existing keys expire naturally.

- Do not flush production Redis. It also contains rate-limit state and unrelated cache domains.
- Do not use `KEYS` or broad `SCAN` deletion in request or mutation paths.
- Exact invalidation and versioned/generation keys are the supported cleanup mechanisms.
- If an incompatible format is deployed, increment `CACHE_KEY_PREFIX` to a new namespace rather
  than migrating or scanning old keys.
- For semantic answers, `SEMANTIC_ANSWER_CACHE_MODE=off` is the immediate rollback. Existing Redis
  entries expire within one hour; Qdrant points are ignored after expiry and removed by maintenance.
- Redis is not backed up as an authoritative datastore. Recovery is to restore the service and let
  bounded cache entries refill.

For production deployment and rollback procedures, see [DEPLOYMENT.md](DEPLOYMENT.md).
