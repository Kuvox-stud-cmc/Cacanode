#!/usr/bin/env bash
set -euo pipefail

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY is required}"
: "${RESTIC_PASSWORD:?RESTIC_PASSWORD is required}"
RESTORE_PROJECT=${RESTORE_PROJECT:-cacanode-recovery}
LIVE_PROJECT=${COMPOSE_PROJECT_NAME:-cacanode}
COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.prod.yml}
TARGET=${RESTORE_TARGET:-/tmp/cacanode-restore}

if [[ "$RESTORE_PROJECT" == "$LIVE_PROJECT" && "${ALLOW_LIVE_VOLUME_OVERWRITE:-}" != "I_UNDERSTAND_DATA_WILL_BE_REPLACED" ]]; then
  echo "Refusing to restore into the live Compose project without explicit destructive confirmation" >&2
  exit 2
fi

mkdir -p "$TARGET"
restic restore "${RESTIC_SNAPSHOT:-latest}" --target "$TARGET" --tag cacanode
ROOT=$(find "$TARGET" -type f -name SHA256SUMS -print -quit | xargs dirname)
(cd "$ROOT" && shasum -a 256 -c metadata/SHA256SUMS)

docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" config >/dev/null
for volume in redis_data rabbitmq_data qdrant_data kuzu_data seaweedfs_master seaweedfs_volume seaweedfs_filer; do
  archive="$ROOT/${volume}.tar.gz"
  if [[ -f "$archive" ]]; then
    docker volume create "${RESTORE_PROJECT}_${volume}" >/dev/null
    docker run --rm -v "${RESTORE_PROJECT}_${volume}:/target" -v "$ROOT:/backup:ro" alpine:3.20 \
      tar -C /target -xzf "/backup/${volume}.tar.gz"
  fi
done
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" up -d postgres redis rabbitmq
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" exec -T postgres \
  pg_restore --clean --if-exists -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-cacanode}" < "$ROOT/postgres/database.dump"
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" cp "$ROOT/redis/dump.rdb" redis:/data/dump.rdb
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" cp "$ROOT/rabbitmq/definitions.json" rabbitmq:/tmp/definitions.json
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" exec -T rabbitmq rabbitmqctl import_definitions /tmp/definitions.json

echo "Restore staged in isolated Compose project: $RESTORE_PROJECT"
echo "Run deploy/recovery-drill.sh to validate Flyway, Redis policy, queues, storage checksums, tenant isolation, and smoke routes."
