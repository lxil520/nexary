#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/deploy/middleware/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  ENV_FILE="$ROOT_DIR/deploy/middleware/.env.example"
fi

set -a
source "$ENV_FILE"
set +a

compose() {
  docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/deploy/middleware/docker-compose.yml" "$@"
}

running="$(compose ps --status running --services 2>/dev/null || true)"
for service in redis valkey kafka rocketmq-namesrv rocketmq-broker mysql xxl-job-admin powerjob-mysql powerjob-server powerjob-worker-samples activemq-classic; do
  if ! grep -qx "$service" <<<"$running"; then
    echo "required middleware service is not running: $service" >&2
    echo "run ./scripts/middleware/up.sh first" >&2
    compose ps >&2 || true
    exit 1
  fi
done

"$ROOT_DIR/gradlew" \
  --rerun-tasks \
  -DNEXARY_RUN_INFRA_TESTS=true \
  -DNEXARY_INFRA_REDIS_HOST="${NEXARY_INFRA_REDIS_HOST:-127.0.0.1}" \
  -DNEXARY_INFRA_REDIS_PORT="${REDIS_PORT:-16379}" \
  -DNEXARY_INFRA_VALKEY_HOST="${NEXARY_INFRA_VALKEY_HOST:-127.0.0.1}" \
  -DNEXARY_INFRA_VALKEY_PORT="${VALKEY_PORT:-16380}" \
  -DNEXARY_INFRA_KAFKA_BOOTSTRAP="${NEXARY_INFRA_KAFKA_BOOTSTRAP:-127.0.0.1:${KAFKA_PORT:-19092}}" \
  -DNEXARY_INFRA_ROCKETMQ_NAMESRV="${NEXARY_INFRA_ROCKETMQ_NAMESRV:-127.0.0.1:${ROCKETMQ_NAMESRV_PORT:-19876}}" \
  -DNEXARY_INFRA_POWERJOB_SERVER_HOST="${NEXARY_INFRA_POWERJOB_SERVER_HOST:-127.0.0.1}" \
  -DNEXARY_INFRA_POWERJOB_SERVER_PORT="${POWERJOB_SERVER_HTTP_PORT:-17700}" \
  -DNEXARY_INFRA_POWERJOB_WORKER_HOST="${NEXARY_INFRA_POWERJOB_WORKER_HOST:-127.0.0.1}" \
  -DNEXARY_INFRA_POWERJOB_WORKER_PORT="${POWERJOB_WORKER_PORT:-27777}" \
  -DNEXARY_INFRA_ACTIVEMQ_CLASSIC_BROKER_URL="${NEXARY_INFRA_ACTIVEMQ_CLASSIC_BROKER_URL:-tcp://127.0.0.1:${ACTIVEMQ_CLASSIC_PORT:-61616}}" \
  :nexary-cache:nexary-cache-redis:test \
  :nexary-messaging:nexary-messaging-redis:test \
  :nexary-messaging:nexary-messaging-kafka:test \
  :nexary-messaging:nexary-messaging-rocketmq:test \
  :nexary-messaging:nexary-messaging-activemq-classic:test \
  :nexary-job:nexary-job-powerjob:test
