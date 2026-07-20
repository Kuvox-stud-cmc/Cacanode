# Production deployment

This document covers the supported single-host production deployment for CacaNode. The reference
profile targets an Ubuntu DigitalOcean droplet with 4 vCPUs, 8 GB RAM, 160 GB disk, and 4 GB swap.
It uses hosted OpenAI generation and graph extraction, embedding-only Ollama, a CPU multilingual
MiniLM reranker, and Kuzu graph storage.

## Topology and exposure

Caddy is the only Internet-facing service. It terminates HTTPS and proxies to the internal Nginx
gateway. Only the following host ports are published:

| Port | Purpose |
| ---: | --- |
| Configured SSH port | Administration and GitHub Actions deployment |
| 80/TCP | ACME challenge and HTTP-to-HTTPS handling |
| 443/TCP and 443/UDP | HTTPS and HTTP/3 |

PostgreSQL, Redis, RabbitMQ, Qdrant, Ollama, Kuzu, SeaweedFS, the reranker, and both application
APIs remain private. PostgreSQL is attached to a dedicated control-plane network shared only with
Spring; AI services and workers have neither PostgreSQL credentials nor network connectivity.

The normal profile runs:

```text
caddy -> gateway -> admin-web / business-api
                                  |  \
                       PostgreSQL |   +-- mTLS gRPC --> ai-api
                                  |                     |
                           RabbitMQ/Redis        Qdrant/graph/Redis/S3
```

One embedded document consumer runs inside `ai-api`. Do not start the `dedicated-workers` profile
while `WORKER_MODE=embedded`.

## 1. Provision DNS and the host

Create an `A` record for `DEPLOY_DOMAIN` pointing to the droplet's public IPv4 address. Confirm DNS
before starting Caddy; automatic certificate issuance requires working DNS and inbound ports 80 and
443.

Upload the repository or [bootstrap-ubuntu.sh](../deploy/bootstrap-ubuntu.sh), then run as root:

```bash
sudo ./deploy/bootstrap-ubuntu.sh cacanode 22
```

The script:

- Installs Docker Engine, Buildx, and Compose from Docker's Ubuntu repository.
- Configures Docker JSON-file log rotation when no daemon configuration exists.
- Creates the non-root deployment user and `/opt/cacanode/{releases,shared}`.
- Creates and persists a 4 GB swap file with `vm.swappiness=10`.
- Configures UFW to deny inbound traffic except SSH, HTTP, and HTTPS.

Mirror the same inbound rules in the DigitalOcean Cloud Firewall. Add the deployment SSH public key
to `/home/cacanode/.ssh/authorized_keys`, then reconnect so Docker group membership is active. Do
not deploy as root.

## 2. Prepare production configuration

Copy the example outside source control and restrict its permissions:

```bash
cp .env.production.example .env.production
chmod 600 .env.production
```

Replace every example domain, email address, password, token, and API key. Generate independent
secrets rather than reusing one value:

```bash
openssl rand -hex 32
```

At minimum, review these groups:

- Public URLs: `DEPLOY_DOMAIN`, `ADMIN_WEB_URL`, `PUBLIC_API_BASE_URL`,
  `PUBLIC_WIDGET_URL`, and `CORS_ORIGINS`.
- Storage and queue credentials: PostgreSQL, RabbitMQ, Qdrant when enabled, and SeaweedFS S3 when
  credentials are configured.
- Application secrets: `TOKEN_KEY`, `PUBLIC_EVIDENCE_SIGNING_KEY`,
  `WIDGET_PREVIEW_SIGNING_KEY`, `INTEGRATION_TOKEN_PEPPER`,
  `INTEGRATION_SECRET_ENCRYPTION_KEY`, `WEBHOOK_ENCRYPTION_KEY`, and
  `GRAPH_INTERNAL_TOKEN`.
- gRPC certificates: set `GRPC_CERT_DIR` to a root-readable directory containing `ca.crt`,
  `spring-client.crt`, `spring-client.key`, `ai-server.crt`, and `ai-server.key`.
- Hosted model settings: `OPENAI_API_KEY`, `OPENAI_MODEL`, and graph-extraction limits.
- Email and authentication links. `LOGIN_2FA_BYPASS_EMAILS` is acceptable only for a controlled,
  temporary demonstration account and should normally be empty.
- PayOS credentials, `PAYOS_WEBHOOK_URL`, and public return/cancel URLs. Keep
  `PAYOS_ENABLED=false` until the public webhook endpoint is reachable and a low-value live
  verification is complete.
- Worker mode. Use `WORKER_MODE=embedded` with `WORKER_KINDS=document` for the reference profile.
- Cache flags. They are safe-off by default; follow [CACHING.md](CACHING.md) before enabling one.

Client-facing URLs must be HTTPS:

```dotenv
PUBLIC_API_BASE_URL=https://app.example.com/api/v1
PUBLIC_WIDGET_URL=https://app.example.com/widget/v1/cacanode-chat.js
ADMIN_WEB_URL=https://app.example.com
GRPC_CERT_DIR=/opt/cacanode/certs/grpc
AI_GRPC_PLAINTEXT=false
```

The Expo `EXPO_PUBLIC_*` values are supplied when building the mobile application and are not
consumed by Docker Compose.

Validate the environment before storing it in GitHub:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
docker compose --env-file .env.production -f docker-compose.prod.yml \
  --profile dedicated-workers config --quiet
bash -n deploy/bootstrap-ubuntu.sh deploy/deploy.sh deploy/smoke-test.sh
```

The deployment script rejects an unreadable or broadly readable environment file, missing required
values, unchanged example values, unsupported embedding model characters, invalid public URLs,
missing email-provider credentials, and incomplete PayOS credentials when PayOS is enabled.

## 3. Configure GitHub Actions

Create a protected GitHub Environment named `production` and add:

| Environment secret | Value |
| --- | --- |
| `DEPLOY_HOST` | Droplet hostname or IPv4 address |
| `DEPLOY_USER` | Non-root deployment user, normally `cacanode` |
| `DEPLOY_SSH_KEY` | Private key dedicated to deployment |
| `DEPLOY_KNOWN_HOSTS` | Pinned SSH host-key line |
| `PRODUCTION_ENV_FILE` | Complete `.env.production` contents |

Optional Environment variables:

| Variable | Default |
| --- | --- |
| `DEPLOY_PORT` | `22` |
| `DEPLOY_ROOT` | `/opt/cacanode` |

Generate the known-hosts value from a trusted machine and compare the fingerprint with the droplet:

```bash
ssh-keyscan -H -p 22 app.example.com
```

The [production workflow](../.github/workflows/deploy-production.yml) validates the shell scripts,
Compose profiles, Nginx configuration, and Caddy configuration before deployment. It runs on pushes
to `main` and through manual dispatch. Concurrency is serialized; an active deployment is not
cancelled by a newer one.

For the first release, use **Actions → Deploy production → Run workflow** and monitor the job.

## 4. Release behavior

The workflow packages the exact Git revision and uploads it with the protected environment. On the
host it creates:

```text
/opt/cacanode/
  current -> releases/<git-sha>
  releases/<git-sha>/
  shared/.env.production
  shared/.env.production.previous
```

The deployment script holds a host-level lock so two releases cannot run concurrently. It then:

1. Validates the environment and rendered Compose model.
2. Pulls infrastructure images one at a time.
3. Builds application images one at a time to limit memory spikes.
4. Starts Ollama and pulls only the configured embedding model.
5. Starts the full stack with a five-minute health timeout.
6. Waits for the CPU reranker when enabled.
7. Validates and reloads Nginx after application containers stabilize.
8. Runs public smoke checks.
9. Prints Compose status and prunes unused images.

After success, `current` points to the new revision and the workflow keeps the three newest release
directories. Named Docker volumes are shared across releases.

### Database migrations

The Spring production profile runs Flyway with `validate-on-migrate=true`; Hibernate uses
`ddl-auto=validate`. Migrations run when `business-api` starts.

### gRPC certificate rotation

Issue Spring client and AI server certificates from the same internal CA. Stage the replacement
files beside the current set, validate both chains, atomically replace the mounted files, then
restart `ai-api` followed by `business-api`. Keep the previous CA trusted during overlapping-CA
rotations, and remove it only after every client and server has restarted successfully.

Database rollback is not automatic. Every production migration must therefore be backward
compatible with the immediately previous application release. Use additive changes first, deploy
code that tolerates old and new schemas, and remove old columns only in a later release. A workflow
rollback changes application code but leaves the migrated schema in place.

## 5. Manual deployment

For an emergency deployment from a checked-out release:

```bash
ENV_FILE=/opt/cacanode/shared/.env.production ./deploy/deploy.sh
```

To start Compose directly for diagnosis rather than as the normal release procedure:

```bash
COMPOSE_PARALLEL_LIMIT=1 docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  up -d --build
```

If dedicated ingestion is required, first set `WORKER_MODE=disabled`, then start only the document
worker profile:

```bash
WORKER_MODE=disabled docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  --profile dedicated-workers up -d document-worker
```

Do not run embedded and dedicated document consumers simultaneously unless duplicate-consumer
behavior has been intentionally reviewed.

## 6. Verification

Public smoke checks:

```bash
./deploy/smoke-test.sh https://app.example.com
```

The script retries these routes for approximately three minutes:

| Route | Expected behavior |
| --- | --- |
| `/health/live` | AI API process is live |
| `/health/ready` | AI API configuration and required model/worker state are ready |
| `/actuator/health` | Spring business API is healthy |
| `/` | Management web application is reachable |

Operational checks on the host:

```bash
docker compose --project-name cacanode \
  --env-file /opt/cacanode/shared/.env.production \
  -f /opt/cacanode/current/docker-compose.prod.yml ps
docker stats --no-stream
free -h
df -h
```

Rehearse the important user paths after material configuration or model changes:

1. Sign in and complete 2FA.
2. Query a pre-ingested document and verify citation provenance.
3. Create or continue a widget conversation.
4. View and safely update a ticket.
5. When ingestion changed, upload a small document and verify completion and retrieval.

Pre-ingest critical documents before a demonstration or launch window. Graph extraction uses hosted
model calls, and the first embedding/reranker requests may warm local models.

## 7. Operations

### Logs

```bash
docker compose --project-name cacanode \
  --env-file /opt/cacanode/shared/.env.production \
  -f /opt/cacanode/current/docker-compose.prod.yml \
  logs -f caddy gateway business-api ai-api graph-service reranker-service ollama
```

Caddy emits structured access logs. Application logs should be collected off-host for durable
retention; Docker's local log rotation is intentionally small.

### Resource pressure

The service limits are intentionally overcommitted because normal peaks do not occur at the same
time. Swap is deployment and warm-up headroom, not normal capacity.

If memory is constrained:

1. Pause live uploads.
2. Inspect `docker stats` and queue depth.
3. Restart or temporarily disable the reranker if reranking is not essential.
4. Disable the document worker to preserve existing retrieval while stopping new ingestion.
5. Keep `graph-service` running when graph retrieval is required.

Ollama should load only `embeddinggemma` in the reference profile. Do not configure a second local
generation model without revisiting the host capacity plan.

### Cache operations

Redis uses AOF, a 192 MB default max-memory setting, and `noeviction`. Optional caches fail open, but
rate-limit counters share the same Redis database. Never flush production Redis as a cache rollback.
Disable the narrowest feature flag and follow [CACHING.md](CACHING.md).

### Backups

The repository does not currently automate production backups or restore drills. Before treating
the deployment as durable production, configure off-host backups and test restoration for:

- PostgreSQL, including Flyway schema history.
- Qdrant collections or volume snapshots.
- Kuzu data in the `kuzu_data` volume.
- SeaweedFS master, filer, and volume data.
- RabbitMQ only if queued jobs must survive a total host loss.
- The protected production environment and any external secret-manager records.

Redis cache contents and rate-limit counters are not authoritative and normally do not require
restoration. Caddy certificates can be reissued, though preserving its volumes reduces recovery
time. A release-directory backup is optional because releases can be reconstructed from Git.

Do not claim a disaster-recovery objective until backup schedules, retention, encryption, and a
successful restore rehearsal are documented outside this repository.

## 8. Rollback

When smoke checks fail, the GitHub workflow restores the previous environment file and attempts the
previous release automatically. It still exits failed so the incident remains visible.

Manual application rollback:

```bash
ls -1dt /opt/cacanode/releases/*
ENV_FILE=/opt/cacanode/shared/.env.production \
  /opt/cacanode/releases/<revision>/deploy/deploy.sh
ln -sfn /opt/cacanode/releases/<revision> /opt/cacanode/current
```

After rollback, rerun smoke checks and inspect migrations, queue consumers, and worker state. Named
volumes remain shared. Never run `docker compose down -v` during rollback; it deletes PostgreSQL,
RabbitMQ, Qdrant, Kuzu, SeaweedFS, Redis, model, and TLS data.

If a rollback is caused by one optional cache, disable that cache first rather than rolling back the
entire release. If a schema migration is incompatible with the previous application, restore from a
tested backup or deploy a forward fix; switching code alone cannot undo the schema.
