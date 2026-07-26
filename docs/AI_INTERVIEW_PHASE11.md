# Phase 11 — Security, operations, and staged activation

Phase 11 adds a second, operator-owned rollout boundary around the existing deployment flags. Deployment flags remain hard upper bounds. Every tenant starts at `OFF`, with every capability disabled.

## Effective capability model

`GET /api/v1/recruitment/capabilities` returns the effective tenant capabilities and stable blocker codes. Effective access is the conjunction of:

1. the global deployment flag;
2. the platform-managed tenant gate;
3. the tenant preference and plan entitlement where applicable.

Platform admins manage gates through `GET|PUT /api/v1/platform/recruitment/tenants/{tenantId}/activation`. Updates are optimistic, audited, and reconcile public projections. Only one tenant may be `INTERNAL`; `PILOT` is restricted to Pro and Business; `GA` is refused unless `RECRUITMENT_GA_UNLOCKED=true`. Public discovery additionally requires a GA tenant and the tenant discovery gate. Internal and pilot jobs remain available only by direct public job ID.

Set `RECRUITMENT_AUTO_ACTIVATE_NEW_TENANTS=true` to grant newly created tenants the deployment-enabled recruitment capabilities immediately. These tenants use the `AUTO` rollout stage, which supports multiple tenants and never enables public discovery. The setting defaults to `false`, does not change existing tenants, and does not overwrite later platform-admin activation decisions.

Recommended rollout:

1. Deploy with all global and tenant gates off.
2. Enable infrastructure flags with no active tenants.
3. Activate one unlisted `INTERNAL` tenant.
4. Promote selected Pro/Business tenants to unlisted `PILOT`.
5. Complete the protected GA checklist, unlock GA, promote tenants, then enable discovery.

Rollback in reverse order: discovery, recording, calling, CV AI, automation, tenant master, deployment flags. Calling and recording kill switches reconcile active provider work asynchronously.

## Candidate and transport security

Invitation secrets are exchanged from a URL fragment in a POST body. Subsequent interview invitation routes use HttpOnly, Secure, SameSite cookies and an `X-CSRF-Token`; token-path routes return `410 Gone` with `no-store`. Twilio runtime tokens are deterministic HMAC values bound to the prepared snapshot and call attempt. They are present only in Media Stream custom parameters, never callback URLs or WebSocket query strings.

Public throttling trusts forwarded addresses only when the immediate peer is in `TRUSTED_PROXY_CIDRS`. Calling uses libphonenumber validation plus keyed Redis fingerprints. Defaults are three destination attempts per 24 hours, 100 tenant attempts daily, and two tenants per destination daily. Redis failure rejects calls closed; raw phone numbers are never metric labels or Redis keys.

CV uploads are capped at 6 MiB by Nginx (the application-level CV cap remains lower). Production keeps malware scanning and Turnstile coupled to public recruitment enablement.

## Privacy deletion

Candidates request deletion from their authenticated candidate session with CSRF and confirm through a fresh one-hour email link. Tenant admins must provide a non-PII verification reference. Requests remain visible as a non-PII status ledger.

The asynchronous worker revokes candidate sessions and tokens, cancels deliveries and interviews, removes CV data, waits for confirmed provider/storage recording deletion, removes application/interview data, removes an unreferenced candidate, and emits a status-only event that clears analytics projections. Transient failures retry with backoff. Ten exhausted attempts leave the request in `EXHAUSTED` for operator action; external objects are never silently orphaned.

## Recovery and GA evidence

`deploy/backup.sh` creates encrypted restic snapshots containing PostgreSQL, Redis, RabbitMQ definitions and durable data, Qdrant, Kuzu, SeaweedFS volumes, protected configuration, Compose metadata, checksums, and release SHA. Retention is 7 daily, 4 weekly, and 12 monthly snapshots. `deploy/restore.sh` refuses the live Compose project without the exact destructive confirmation and restores into an isolated project by default. `deploy/recovery-drill.sh` validates core durability and smoke endpoints.

Target RPO is 24 hours and target RTO is 4 hours. Run protected production restoration drills quarterly and record elapsed time, checksum results, tenant-isolation checks, Twilio sandbox evidence, and real English/Vietnamese verified-number call evidence in the protected GA environment.

No Phase 11 feature makes an automatic hiring or rejection decision. AI summaries and scores remain advisory.
