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

compose=(docker compose --project-name "${PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

env_value() {
  local name="$1"
  sed -n "s/^${name}=//p" "${ENV_FILE}" | tail -n 1
}

embedding_model="$(env_value TEXT_EMBEDDING_MODEL_ID)"
public_url="$(env_value ADMIN_WEB_URL)"
reranker_enabled="$(env_value RERANKER_ENABLED)"
embedding_model="${embedding_model:-embeddinggemma}"
reranker_enabled="${reranker_enabled:-true}"

for required_name in \
  DEPLOY_DOMAIN CADDY_ACME_EMAIL ADMIN_WEB_URL CORS_ORIGINS \
  POSTGRES_PASSWORD RABBITMQ_PASSWORD TOKEN_KEY INTEGRATION_TOKEN_PEPPER \
  WEBHOOK_ENCRYPTION_KEY GRAPH_INTERNAL_TOKEN GRPC_CERT_DIR \
  OPENAI_API_KEY FROM_EMAIL VERIFICATION_LINK LOGIN_2FA_LINK INVITATION_LINK; do
  required_value="$(env_value "${required_name}")"
  if [[ -z "${required_value}" || "${required_value}" == *replace-with* || "${required_value}" == *example.com* ]]; then
    echo "${required_name} is missing or still contains an example value" >&2
    exit 1
  fi
done

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
if [[ ! "${public_url}" =~ ^https:// ]]; then
  echo "ADMIN_WEB_URL must be an HTTPS URL" >&2
  exit 1
fi

"${compose[@]}" config --quiet

COMPOSE_PARALLEL_LIMIT=1 "${compose[@]}" pull \
  caddy gateway ollama reranker-service postgres redis rabbitmq qdrant \
  seaweedfs-master seaweedfs-volume seaweedfs-filer seaweedfs-s3

COMPOSE_PARALLEL_LIMIT=1 "${compose[@]}" build --pull \
  admin-web business-api ai-api graph-service

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
"${compose[@]}" up -d --remove-orphans --wait --wait-timeout 300

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
