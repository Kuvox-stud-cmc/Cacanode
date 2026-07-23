#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.production}"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-cacanode}"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.prod.yml"

if [[ ! -r "${ENV_FILE}" ]]; then
  echo "Production environment file is missing or unreadable: ${ENV_FILE}" >&2
  exit 1
fi

if [[ "$(stat -c '%a' "${ENV_FILE}")" =~ [2367]$ ]]; then
  echo "Production environment file must not be readable by other users: ${ENV_FILE}" >&2
  exit 1
fi

exec 9>"${TMPDIR:-/tmp}/${PROJECT_NAME}-deploy.lock"
if ! flock -n 9; then
  echo "Another ${PROJECT_NAME} deployment is already running" >&2
  exit 1
fi

compose=(docker compose --project-name "${PROJECT_NAME}" --env-file "${ENV_FILE}" \
  -f "${COMPOSE_FILE}" --profile dedicated-workers)

env_value() {
  local name="$1"
  sed -n "s/^${name}=//p" "${ENV_FILE}" | tail -n 1
}

is_unconfigured() {
  local value="$1"
  [[ -z "${value}" || "${value}" == *replace-with* || "${value}" == *example.com* ]]
}

embedding_model="$(env_value TEXT_EMBEDDING_MODEL_ID)"
public_url="$(env_value ADMIN_WEB_URL)"
reranker_enabled="$(env_value RERANKER_ENABLED)"
worker_mode="$(env_value WORKER_MODE)"
embedding_model="${embedding_model:-embeddinggemma}"
reranker_enabled="${reranker_enabled:-true}"
worker_mode="${worker_mode:-disabled}"

if [[ "${worker_mode}" != "disabled" ]]; then
  echo "Production AI API must use WORKER_MODE=disabled; the dedicated worker is deployed separately" >&2
  exit 1
fi

for required_name in \
  DEPLOY_DOMAIN CADDY_ACME_EMAIL ADMIN_WEB_URL PUBLIC_API_BASE_URL \
  PUBLIC_WIDGET_URL CORS_ORIGINS \
  POSTGRES_PASSWORD RABBITMQ_PASSWORD TOKEN_KEY INTEGRATION_TOKEN_PEPPER \
  PUBLIC_EVIDENCE_SIGNING_KEY WIDGET_PREVIEW_SIGNING_KEY \
  INTEGRATION_SECRET_ENCRYPTION_KEY WEBHOOK_ENCRYPTION_KEY GRAPH_INTERNAL_TOKEN GRPC_CERT_DIR \
  OPENAI_API_KEY FROM_EMAIL VERIFICATION_LINK LOGIN_2FA_LINK INVITATION_LINK; do
  required_value="$(env_value "${required_name}")"
  if is_unconfigured "${required_value}"; then
    echo "${required_name} is missing or still contains an example value" >&2
    exit 1
  fi
done

sendgrid_api_key="$(env_value SENDGRID_API_KEY)"
brevo_api_key="$(env_value BREVO_API_KEY)"
if is_unconfigured "${sendgrid_api_key}" && is_unconfigured "${brevo_api_key}"; then
  echo "Configure at least one email provider: SENDGRID_API_KEY or BREVO_API_KEY" >&2
  exit 1
fi

payos_enabled="$(env_value PAYOS_ENABLED)"
if [[ "${payos_enabled,,}" == "true" ]]; then
  for payos_name in PAYOS_CLIENT_ID PAYOS_API_KEY PAYOS_CHECKSUM_KEY PAYOS_WEBHOOK_URL; do
    payos_value="$(env_value "${payos_name}")"
    if is_unconfigured "${payos_value}"; then
      echo "${payos_name} is required when PAYOS_ENABLED=true" >&2
      exit 1
    fi
  done
  payos_webhook_url="$(env_value PAYOS_WEBHOOK_URL)"
  if [[ ! "${payos_webhook_url}" =~ ^https://.+/api/v1/public/billing/payos/webhook/?$ ]]; then
    echo "PAYOS_WEBHOOK_URL must be the public HTTPS CacaNode PayOS webhook endpoint" >&2
    exit 1
  fi
fi

grpc_cert_dir="$(env_value GRPC_CERT_DIR)"
for certificate_file in ca.crt spring-client.crt spring-client.key ai-server.crt ai-server.key; do
  if [[ ! -r "${grpc_cert_dir}/${certificate_file}" ]]; then
    echo "Missing readable gRPC certificate material: ${grpc_cert_dir}/${certificate_file}" >&2
    exit 1
  fi
done

if [[ ! "${embedding_model}" =~ ^[A-Za-z0-9._:/-]+$ ]]; then
  echo "TEXT_EMBEDDING_MODEL_ID contains unsupported characters" >&2
  exit 1
fi
public_api_url="$(env_value PUBLIC_API_BASE_URL)"
public_widget_url="$(env_value PUBLIC_WIDGET_URL)"
if [[ ! "${public_url}" =~ ^https:// ]]; then
  echo "ADMIN_WEB_URL must be an HTTPS URL" >&2
  exit 1
fi
if [[ ! "${public_api_url}" =~ ^https://.+/api/v1/?$ ]]; then
  echo "PUBLIC_API_BASE_URL must be an HTTPS URL ending in /api/v1" >&2
  exit 1
fi
if [[ ! "${public_widget_url}" =~ ^https://.+/widget/v1/cacanode-chat\.js$ ]]; then
  echo "PUBLIC_WIDGET_URL must be the public HTTPS widget script URL" >&2
  exit 1
fi

"${compose[@]}" config --quiet

COMPOSE_PARALLEL_LIMIT=1 "${compose[@]}" pull \
  caddy gateway ollama reranker-service postgres redis rabbitmq qdrant \
  seaweedfs-master seaweedfs-volume seaweedfs-filer seaweedfs-s3

COMPOSE_PARALLEL_LIMIT=1 "${compose[@]}" build --pull \
  admin-web business-api ai-api graph-service

# Pause ingestion before taking pre-activation snapshots. RabbitMQ continues accepting requests.
"${compose[@]}" stop document-worker >/dev/null 2>&1 || true
snapshot_id="$(date -u +%Y%m%dT%H%M%SZ)"
if "${compose[@]}" ps --status running --services | grep -qx redis; then
  "${compose[@]}" exec -T redis redis-cli SAVE >/dev/null
  "${compose[@]}" exec -T redis sh -c \
    "cp /data/dump.rdb /data/pre-activation-${snapshot_id}.rdb"
fi
if "${compose[@]}" ps --status running --services | grep -qx qdrant && \
   "${compose[@]}" ps --status running --services | grep -qx ai-api; then
  "${compose[@]}" exec -T ai-api python -c \
    "import os,urllib.request; r=urllib.request.Request('http://qdrant:6333/snapshots',method='POST',headers={'api-key':os.getenv('QDRANT_API_KEY','')}); urllib.request.urlopen(r,timeout=30).read()"
fi
if "${compose[@]}" ps --status running --services | grep -qx graph-service; then
  "${compose[@]}" stop graph-service
  "${compose[@]}" run --rm --no-deps graph-service python -c \
    "import shutil; shutil.make_archive('/backups/kuzu-${snapshot_id}','gztar','/data/kuzu')"
fi

"${compose[@]}" up -d ollama
for attempt in {1..24}; do
  if "${compose[@]}" exec -T ollama ollama list >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" -eq 24 ]]; then
    echo "Ollama did not become healthy" >&2
    exit 1
  fi
  sleep 5
done

"${compose[@]}" exec -T ollama ollama pull "${embedding_model}"
# Activate the graph role first, then API roles, then resume the dedicated worker.
"${compose[@]}" up -d --wait --wait-timeout 300 graph-service
"${compose[@]}" up -d --remove-orphans --wait --wait-timeout 300 \
  --scale document-worker=0
"${compose[@]}" up -d --wait --wait-timeout 300 document-worker

if [[ "${reranker_enabled,,}" == "true" ]]; then
  for attempt in {1..60}; do
    if "${compose[@]}" exec -T ai-api python -c \
      "import urllib.request; urllib.request.urlopen('http://reranker-service/health', timeout=3)" \
      >/dev/null 2>&1; then
      break
    fi
    if [[ "${attempt}" -eq 60 ]]; then
      echo "CPU reranker did not become ready" >&2
      exit 1
    fi
    sleep 5
  done
fi

# Compose can recreate application containers without recreating the gateway.
# Reload Nginx after all upstreams are stable so it does not retain stale
# Docker DNS addresses from the previous release.
"${compose[@]}" exec -T gateway nginx -t
"${compose[@]}" exec -T gateway nginx -s reload

"${ROOT_DIR}/deploy/smoke-test.sh" "${public_url%/}"
"${compose[@]}" ps
docker image prune --force >/dev/null
