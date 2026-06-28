#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/deploy/console/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  ENV_FILE="$ROOT_DIR/deploy/console/.env.example"
fi

set -a
source "$ENV_FILE"
set +a

BASE_URL="http://127.0.0.1:${NEXARY_CONSOLE_PORT:-18090}"

echo "[console] seed platform demo data"
curl -fsS -X POST "$BASE_URL/demo/platform/seed" | grep '"seeded":true' >/dev/null

echo "[console] platform overview"
curl -fsS "$BASE_URL/api/platform/overview" | grep '"workspaceKey":"cloud-phone"' >/dev/null
curl -fsS "$BASE_URL/api/platform/overview" | grep '"notificationRoutes"' >/dev/null
curl -fsS "$BASE_URL/api/platform/snapshot" | grep '"requestFlows"' >/dev/null

echo "[console] platform topology"
curl -fsS "$BASE_URL/api/platform/topology" | grep '"room-resource"' >/dev/null

echo "[console] request flows and host signals"
curl -fsS "$BASE_URL/api/platform/request-flows" | grep '"flow-signaling-redis-timeout"' >/dev/null
curl -fsS "$BASE_URL/api/platform/transactions" | grep '"endpointKey"' >/dev/null
curl -fsS "$BASE_URL/api/platform/hosts" | grep '"redis-room-a-primary"' >/dev/null

echo "[console] platform connectors"
curl -fsS "$BASE_URL/api/platform/connectors" | grep '"FEISHU"' >/dev/null

echo "[console] page html"
curl -fsSL "$BASE_URL/nexary/console" | grep '/nexary/console/assets/' >/dev/null

echo "console smoke passed: $BASE_URL/nexary/console"
