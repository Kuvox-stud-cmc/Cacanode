# Production deployment

This deployment profile targets one Ubuntu DigitalOcean droplet with 4 vCPUs, 8 GB RAM, and
160 GB disk. It uses OpenAI for answer generation and graph extraction, Ollama only for
`embeddinggemma`, a CPU multilingual MiniLM reranker, and Kuzu for graph storage.

Only SSH, HTTP, and HTTPS are published. PostgreSQL, Redis, RabbitMQ, Qdrant, Ollama, Kuzu, and
SeaweedFS remain private to the Docker network.

## 1. Prepare DNS and the droplet

Create an `A` record for the deployment domain pointing to the droplet's public IPv4 address.
Automatic HTTPS will not succeed until DNS resolves to the droplet and inbound ports 80 and 443
are open.

On a new Ubuntu droplet, upload this repository or just `deploy/bootstrap-ubuntu.sh`, then run:

```bash
sudo ./deploy/bootstrap-ubuntu.sh cacanode 22
```

The script installs Docker Engine and Compose, creates `/opt/cacanode`, enables a 4 GB swap file,
and configures UFW for the supplied SSH port plus ports 80 and 443. Configure the DigitalOcean
Cloud Firewall with the same inbound rules. Reconnect after the script finishes so the `cacanode`
user receives its Docker group membership.

Add the GitHub Actions SSH public key to `/home/cacanode/.ssh/authorized_keys`. Do not use the
root account for deployments.

## 2. Create production configuration

Copy [.env.production.example](.env.production.example) locally and replace every example domain,
email, API key, password, and token:

```bash
cp .env.production.example .env.production
chmod 600 .env.production
```

Generate unrelated random secrets rather than reusing one value:

```bash
openssl rand -hex 32
```

Important settings:

- `OPENAI_MODEL=o4-mini` keeps the already-tested hosted generation path for the presentation.
- `TEXT_EMBEDDING_MODEL_ID=embeddinggemma` is the only model Ollama downloads.
- `RERANKER_MODEL_ID=cross-encoder/mmarco-mMiniLMv2-L12-H384-v1` preserves multilingual reranking
  without the GPU-sized `BAAI/bge-reranker-v2-m3` model.
- `WORKER_MODE=embedded` and `WORKER_KINDS=document` run exactly one ingestion consumer inside
  `ai-api`. Do not enable the `dedicated-workers` profile at the same time.
- `LOGIN_2FA_BYPASS_EMAILS` is acceptable only for a controlled presentation account when email is
  not configured. Remove the bypass after the demonstration.

The values embedded into the web and mobile clients must use HTTPS:

```dotenv
PUBLIC_API_BASE_URL=https://app.example.com/api/v1
PUBLIC_AI_API_BASE_URL=https://app.example.com/api/v1
EXPO_PUBLIC_API_BASE_URL=https://app.example.com/api/v1
EXPO_PUBLIC_AI_API_BASE_URL=https://app.example.com/api/v1
```

The two `EXPO_PUBLIC_*` values are supplied when producing the Expo build; they are not consumed
by Docker Compose.

Before adding the environment to GitHub, render the Compose configuration locally if Docker is
available:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
```

## 3. Configure GitHub Actions

Create a protected GitHub Environment named `production`. Add these Environment secrets:

| Secret | Value |
|---|---|
| `DEPLOY_HOST` | Droplet hostname or IPv4 address |
| `DEPLOY_USER` | `cacanode` |
| `DEPLOY_SSH_KEY` | Private key dedicated to GitHub Actions deployment |
| `DEPLOY_KNOWN_HOSTS` | Pinned SSH host-key line for the droplet |
| `PRODUCTION_ENV_FILE` | Entire completed `.env.production` file |

Generate the known-hosts value from a trusted machine and compare its fingerprint with the
droplet before saving it:

```bash
ssh-keyscan -H -p 22 app.example.com
```

Optional Environment variables:

| Variable | Default |
|---|---|
| `DEPLOY_PORT` | `22` |
| `DEPLOY_ROOT` | `/opt/cacanode` |

The [Deploy production](.github/workflows/deploy-production.yml) workflow runs on pushes to `main`
and can also be started manually. It uploads an immutable Git revision, installs the protected
environment file, builds one image at a time, pulls `embeddinggemma`, starts the stack, performs
HTTPS smoke checks, and retains the three newest releases.

For the first deployment, use **Actions → Deploy production → Run workflow**. Watch the Caddy logs
if certificate issuance takes longer than expected:

```bash
docker compose --project-name cacanode \
  --env-file /opt/cacanode/shared/.env.production \
  -f /opt/cacanode/current/docker-compose.prod.yml logs -f caddy
```

## 4. Manual deployment

For an emergency manual deployment from a checked-out release:

```bash
ENV_FILE=/opt/cacanode/shared/.env.production ./deploy/deploy.sh
```

The deployment script intentionally uses `COMPOSE_PARALLEL_LIMIT=1` to avoid memory spikes during
the Java, Node, and Python image builds.

## 5. Verification and rehearsal

Public health checks:

```bash
./deploy/smoke-test.sh https://app.example.com
```

Operational checks:

```bash
docker compose --project-name cacanode \
  --env-file /opt/cacanode/shared/.env.production \
  -f /opt/cacanode/current/docker-compose.prod.yml ps
docker stats --no-stream
free -h
df -h
```

Rehearse these presentation paths before the session:

1. Sign in using the presentation account and complete or bypass 2FA as configured.
2. Ask one question against a pre-ingested document and verify its citation.
3. Open customer conversations and close a test conversation.
4. Open tickets and perform one safe status update.
5. Upload only a small text PDF if live ingestion is part of the presentation.

Pre-ingest the main demonstration documents. Graph extraction uses hosted OpenAI calls and can be
slower than retrieval, while the first local reranker and embedding requests may also warm their
models.

## 6. Service and resource decisions

- `graph-service` remains enabled. It owns the Kuzu database and does not load a language model.
- Graph entity/relation extraction runs from the document worker through OpenAI.
- Dedicated OCR, ASR, vision, audio, and video workers are not started.
- `reranker-service` uses the CPU TEI image and the multilingual MiniLM checkpoint.
- Ollama loads at most one model and serves embeddings only.
- Caddy is the only public entry point and obtains certificates automatically.
- Runtime memory limits prevent a single service from consuming the entire droplet. The limits are
  intentionally overcommitted because normal service peaks do not happen simultaneously; the 4 GB
  swap file provides deployment and warm-up headroom, not normal working memory.

If the droplet becomes memory constrained during the presentation, first avoid uploads and restart
the reranker. Do not disable `graph-service` if graph retrieval must remain available. Disabling the
document worker preserves existing retrieval but prevents new uploads from being ingested.

## 7. Rollback

The workflow automatically attempts the previous release when smoke checks fail. To roll back
manually, list releases and run the deployment script from the chosen revision:

```bash
ls -1dt /opt/cacanode/releases/*
ENV_FILE=/opt/cacanode/shared/.env.production \
  /opt/cacanode/releases/<revision>/deploy/deploy.sh
ln -sfn /opt/cacanode/releases/<revision> /opt/cacanode/current
```

Named Docker volumes are shared across releases. Never run `docker compose down -v` during a
rollback because it deletes PostgreSQL, queue, vector, graph, object-storage, model, and TLS data.
