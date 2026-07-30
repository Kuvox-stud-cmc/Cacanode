#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${1:?Usage: smoke-test.sh https://app.example.com}"
ATTEMPTS="${SMOKE_TEST_ATTEMPTS:-36}"
DELAY_SECONDS="${SMOKE_TEST_DELAY_SECONDS:-5}"

check() {
  local path="$1"
  local response status body
  if ! response="$(curl --silent --show-error \
      --connect-timeout 5 \
      --max-time 20 \
      --write-out $'\n%{http_code}' \
      "${BASE_URL}${path}" 2>&1)"; then
    LAST_FAILURE="${path}: ${response}"
    return 1
  fi
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [[ "${status}" =~ ^2[0-9][0-9]$ ]]; then
    return 0
  fi
  body="${body:0:500}"
  LAST_FAILURE="${path} returned HTTP ${status}${body:+: ${body}}"
  return 1
}

operational_checks() {
  [[ "${SMOKE_COMPOSE_CHECKS:-false}" == "true" ]] || return 0
  local compose_file=${COMPOSE_FILE:-docker-compose.prod.yml}
  local project=${COMPOSE_PROJECT_NAME:-cacanode}
  docker compose -p "$project" -f "$compose_file" config >/dev/null
  docker compose -p "$project" -f "$compose_file" exec -T redis sh -c \
    'test "$(redis-cli --raw CONFIG GET appendonly | tail -1)" = yes && test "$(redis-cli --raw CONFIG GET appendfsync | tail -1)" = everysec && test "$(redis-cli --raw CONFIG GET maxmemory-policy | tail -1)" = noeviction'
  docker compose -p "$project" -f "$compose_file" exec -T rabbitmq rabbitmq-diagnostics -q check_running
  docker compose -p "$project" -f "$compose_file" exec -T rabbitmq rabbitmqctl list_queues name durable arguments | grep -E 'resume-analysis|interview-events|recording-operations|dlq' >/dev/null
  docker compose -p "$project" -f "$compose_file" exec -T business-api sh -c \
    'wget -qO- http://localhost:8080/actuator/prometheus | grep -q recruitment'
  docker compose -p "$project" -f "$compose_file" exec -T gateway nginx -t
  grep -q 'location = /ws/v1/interviews/twilio/media' gateway/nginx.conf
  grep -A3 'public/interview-invitations' gateway/nginx.conf | grep -q 'access_log off'
  if [[ "${RECRUITMENT_GA_UNLOCKED:-false}" == "true" && "${RECRUITMENT_ENABLED:-false}" != "true" ]]; then
    echo "GA unlock requires recruitment deployment enablement" >&2; return 1
  fi
}

for ((attempt = 1; attempt <= ATTEMPTS; attempt += 1)); do
  LAST_FAILURE="unknown failure"
  if check "/health/live" && check "/health/ready" && check "/actuator/health" && check "/"; then
    operational_checks
    echo "Production smoke checks passed at ${BASE_URL}"
    exit 0
  fi
  echo "Waiting for production health checks (${attempt}/${ATTEMPTS}): ${LAST_FAILURE}"
  sleep "${DELAY_SECONDS}"
done

echo "Production smoke checks failed at ${BASE_URL}" >&2
exit 1
