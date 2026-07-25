#!/usr/bin/env bash
set -euo pipefail

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY is required}"
: "${RESTIC_PASSWORD:?RESTIC_PASSWORD is required}"
COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.prod.yml}
PROJECT=${COMPOSE_PROJECT_NAME:-cacanode}
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
WORK=${BACKUP_WORKDIR:-/tmp/cacanode-backup-$STAMP}

cleanup(){ rm -rf "$WORK"; }
trap cleanup EXIT
mkdir -p "$WORK/postgres" "$WORK/redis" "$WORK/rabbitmq" "$WORK/metadata" "$WORK/protected-config"

archive_volume(){
  local name=$1
  docker run --rm -v "${PROJECT}_${name}:/source:ro" -v "$WORK:/backup" alpine:3.20 \
    tar -C /source -czf "/backup/${name}.tar.gz" .
}

docker compose -p "$PROJECT" -f "$COMPOSE_FILE" config > "$WORK/metadata/compose.yaml"
git rev-parse HEAD > "$WORK/metadata/release-sha"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T postgres \
  pg_dump -U "${POSTGRES_USER:-postgres}" -Fc "${POSTGRES_DB:-cacanode}" > "$WORK/postgres/database.dump"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T redis redis-cli SAVE >/dev/null
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" cp redis:/data/dump.rdb "$WORK/redis/dump.rdb"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T rabbitmq rabbitmqctl export_definitions /tmp/definitions.json >/dev/null
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" cp rabbitmq:/tmp/definitions.json "$WORK/rabbitmq/definitions.json"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" stop rabbitmq graph-service
trap 'docker compose -p "$PROJECT" -f "$COMPOSE_FILE" start rabbitmq graph-service >/dev/null 2>&1 || true; cleanup' EXIT
for volume in redis_data rabbitmq_data qdrant_data kuzu_data seaweedfs_master seaweedfs_volume seaweedfs_filer; do
  archive_volume "$volume"
done
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" start rabbitmq graph-service
trap cleanup EXIT

for path in ${PROTECTED_CONFIG_PATHS:-.env.production deploy/Caddyfile}; do
  if [[ -f "$path" ]]; then cp "$path" "$WORK/protected-config/$(basename "$path")"; fi
done
for path in ${BACKUP_DATA_PATHS:-}; do
  if [[ -e "$path" ]]; then cp -R "$path" "$WORK/"; fi
done

find "$WORK" -type f ! -name SHA256SUMS -exec shasum -a 256 {} \; | sort > "$WORK/metadata/SHA256SUMS"
restic snapshots >/dev/null 2>&1 || restic init
restic backup "$WORK" --tag cacanode --tag "$PROJECT" --tag "$STAMP"
restic forget --tag cacanode --keep-daily 7 --keep-weekly 4 --keep-monthly 12 --prune
