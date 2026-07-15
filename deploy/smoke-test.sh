#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${1:?Usage: smoke-test.sh https://app.example.com}"
ATTEMPTS="${SMOKE_TEST_ATTEMPTS:-36}"
DELAY_SECONDS="${SMOKE_TEST_DELAY_SECONDS:-5}"

check() {
  local path="$1"
  curl --fail --silent --show-error \
    --connect-timeout 5 \
    --max-time 20 \
    "${BASE_URL}${path}" >/dev/null
}

for ((attempt = 1; attempt <= ATTEMPTS; attempt += 1)); do
  if check "/health/live" && check "/health/ready" && check "/actuator/health" && check "/"; then
    echo "Production smoke checks passed at ${BASE_URL}"
    exit 0
  fi
  echo "Waiting for production health checks (${attempt}/${ATTEMPTS})..."
  sleep "${DELAY_SECONDS}"
done

echo "Production smoke checks failed at ${BASE_URL}" >&2
exit 1
