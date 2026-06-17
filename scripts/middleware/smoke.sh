#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/middleware/docker-compose.yml"
ENV_FILE="$ROOT_DIR/deploy/middleware/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  ENV_FILE="$ROOT_DIR/deploy/middleware/.env.example"
fi

set -a
source "$ENV_FILE"
set +a

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

require_running_services() {
  local expected=(redis kafka rocketmq-namesrv rocketmq-broker mysql xxl-job-admin)
  local running
  running="$(compose ps --status running --services 2>/dev/null || true)"
  local missing=()
  for service in "${expected[@]}"; do
    if ! grep -qx "$service" <<<"$running"; then
      missing+=("$service")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "middleware services are not running: ${missing[*]}" >&2
    echo "run ./scripts/middleware/up.sh first, then re-run this script" >&2
    compose ps >&2 || true
    exit 1
  fi
}

require_running_services

echo "[redis] ping"
compose exec -T redis redis-cli ping | grep PONG >/dev/null

echo "[kafka] produce and consume"
compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic nexary.smoke --partitions 1 --replication-factor 1 >/dev/null
printf 'nexary-smoke\n' | compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic nexary.smoke >/dev/null
compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic nexary.smoke --from-beginning --max-messages 1 --timeout-ms 10000 | grep nexary-smoke >/dev/null

echo "[rocketmq] broker cluster check"
compose exec -T rocketmq-broker sh mqadmin clusterList -n rocketmq-namesrv:9876 >/dev/null

echo "[mysql] xxl_job schema check"
compose exec -T mysql mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" -D xxl_job -e "show tables;" | grep xxl_job_info >/dev/null

echo "[xxl-job] admin endpoint"
curl -fsS "http://127.0.0.1:${XXL_JOB_ADMIN_PORT:-18080}/xxl-job-admin/" >/dev/null

echo "middleware smoke passed"
