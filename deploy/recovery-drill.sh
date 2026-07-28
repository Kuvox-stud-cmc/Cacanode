#!/usr/bin/env bash
set -euo pipefail

export RESTORE_PROJECT=${RESTORE_PROJECT:-cacanode-recovery-drill}
"$(dirname "$0")/restore.sh"
COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.prod.yml}

docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" up -d
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" exec -T redis redis-cli CONFIG GET appendonly appendfsync maxmemory-policy
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" exec -T rabbitmq rabbitmq-diagnostics check_running
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" exec -T rabbitmq rabbitmqctl list_queues name durable messages_ready messages_unacknowledged consumers
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" exec -T business-api sh -c 'wget -qO- http://localhost:8080/actuator/health/readiness'
docker compose -p "$RESTORE_PROJECT" -f "$COMPOSE_FILE" exec -T business-api sh -c 'wget -qO- http://localhost:8080/actuator/prometheus | grep -q recruitment'

echo "Recovery drill completed for $RESTORE_PROJECT. Record the elapsed time and evidence in the protected release environment."
