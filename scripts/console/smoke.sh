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
PROMETHEUS_URL="http://127.0.0.1:${NEXARY_PROMETHEUS_PORT:-18095}"
SKYWALKING_URL="http://127.0.0.1:${NEXARY_SKYWALKING_UI_PORT:-18097}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

echo "[console] seed platform demo data"
curl -fsS -X POST "$BASE_URL/demo/platform/seed" | grep '"seeded":true' >/dev/null

echo "[console] run live middleware probe"
curl -fsS -X POST "$BASE_URL/demo/platform/probe?iterations=20" | grep '"liveProbe":true' >/dev/null
curl -fsS "$BASE_URL/api/platform/signals" | grep '"source":"live-probe"' >/dev/null

echo "[console] platform overview"
curl -fsS "$BASE_URL/api/platform/overview" | grep '"workspaceKey":"cloud-phone"' >/dev/null
curl -fsS "$BASE_URL/api/platform/overview" | grep '"notificationRoutes"' >/dev/null
curl -fsS "$BASE_URL/api/platform/snapshot" | grep '"requestFlows"' >/dev/null

echo "[console] platform topology"
curl -fsS "$BASE_URL/api/platform/topology" | grep '"room-resource"' >/dev/null

echo "[console] request flows and host signals"
curl -fsS "$BASE_URL/api/platform/request-flows" | grep '"flow-signaling-redis-timeout"' >/dev/null
curl -fsS "$BASE_URL/api/platform/request-flows" | grep '"flow-live-redis-probe"' >/dev/null
curl -fsS "$BASE_URL/api/platform/transactions" | grep '"endpointKey"' >/dev/null
curl -fsS "$BASE_URL/api/platform/transactions" | grep '"probe:redis:set-get"' >/dev/null
curl -fsS "$BASE_URL/api/platform/transactions" | grep '"probe:rabbitmq:publish-consume"' >/dev/null
curl -fsS "$BASE_URL/api/platform/hosts" | grep '"redis-room-a-primary"' >/dev/null

echo "[console] platform connectors"
curl -fsS "$BASE_URL/api/platform/connectors" | grep '"FEISHU"' >/dev/null

echo "[console] controlled governance entry"
curl -fsS "$BASE_URL/api/platform/plans" -o "$TMP_DIR/plans.json"
grep '"planKey"' "$TMP_DIR/plans.json" >/dev/null
PLAN_KEY="$(sed -n 's/.*"planKey":"\([^"]*\)".*/\1/p' "$TMP_DIR/plans.json" | head -n 1)"
if [[ -z "$PLAN_KEY" ]]; then
  echo "No governance review plan returned" >&2
  exit 1
fi
curl -fsS -X POST "$BASE_URL/api/platform/plans/${PLAN_KEY}/dry-run" -o "$TMP_DIR/dry-run.json"
grep '"passed":' "$TMP_DIR/dry-run.json" >/dev/null
grep '"risk"' "$TMP_DIR/dry-run.json" >/dev/null
grep '"summary":"TEST / DRY-RUN only; external systems are not changed"' "$TMP_DIR/dry-run.json" >/dev/null
curl -fsS -X POST "$BASE_URL/api/platform/plans/${PLAN_KEY}/export-review" -o "$TMP_DIR/export-review.json"
grep '"mode":"REVIEW_ONLY"' "$TMP_DIR/export-review.json" >/dev/null
grep '"summary"' "$TMP_DIR/export-review.json" >/dev/null

curl -fsS "$BASE_URL/api/platform/notification-routes" -o "$TMP_DIR/notification-routes.json"
grep '"routeKey"' "$TMP_DIR/notification-routes.json" >/dev/null
ROUTE_KEY="$(sed -n 's/.*"routeKey":"\([^"]*\)".*/\1/p' "$TMP_DIR/notification-routes.json" | head -n 1)"
if [[ -z "$ROUTE_KEY" ]]; then
  echo "No notification route returned" >&2
  exit 1
fi
curl -fsS -X POST "$BASE_URL/api/platform/notification-routes/${ROUTE_KEY}/preview" -o "$TMP_DIR/notification-preview.json"
grep '"mode":"' "$TMP_DIR/notification-preview.json" >/dev/null
grep 'TEST / DRY-RUN' "$TMP_DIR/notification-preview.json" >/dev/null
curl -fsS -X POST "$BASE_URL/api/platform/notification-routes/${ROUTE_KEY}/test" -o "$TMP_DIR/notification-test.json"
grep '"accepted":false' "$TMP_DIR/notification-test.json" >/dev/null
grep '"status":"TEST_DISABLED"' "$TMP_DIR/notification-test.json" >/dev/null
curl -fsS "$BASE_URL/api/platform/audit-records" | grep '"action"' >/dev/null

echo "[console] connector configuration center"
cat >"$TMP_DIR/connector-config.json" <<JSON
{"connectorKey":"skywalking-smoke","kind":"SKYWALKING","displayName":"SkyWalking Smoke","endpoint":"http://127.0.0.1:8080/api/platform/topology","authMode":"NONE","accessMode":"READ_ONLY","state":"DISABLED","testEnabled":false,"attributes":{"targetTeam":"platform-team","writeDisabled":"true"}}
JSON
curl -fsS -X POST "$BASE_URL/api/platform/connector-configs" \
  -H 'Content-Type: application/json' \
  --data-binary "@$TMP_DIR/connector-config.json" \
  -o "$TMP_DIR/saved-connector.json"
grep '"connectorKey":"skywalking-smoke"' "$TMP_DIR/saved-connector.json" >/dev/null
grep '"writeDisabled":true' "$TMP_DIR/saved-connector.json" >/dev/null
curl -fsS -X POST "$BASE_URL/api/platform/connector-configs/skywalking-smoke/test" -o "$TMP_DIR/connector-test.json"
grep '"accepted":true' "$TMP_DIR/connector-test.json" >/dev/null
grep '"status":"TEST_REACHABLE"' "$TMP_DIR/connector-test.json" >/dev/null
cat >"$TMP_DIR/service-mapping.json" <<JSON
{"mappingKey":"skywalking-smoke-room-resource","serviceKey":"room-resource","connectorKey":"skywalking-smoke","sourceKind":"SKYWALKING","externalKey":"service:room-resource","resourceKind":"service","confidence":0.9,"attributes":{"source":"smoke"}}
JSON
curl -fsS -X POST "$BASE_URL/api/platform/service-mappings" \
  -H 'Content-Type: application/json' \
  --data-binary "@$TMP_DIR/service-mapping.json" \
  -o "$TMP_DIR/service-mapping-response.json"
grep '"mappingKey":"skywalking-smoke-room-resource"' "$TMP_DIR/service-mapping-response.json" >/dev/null
curl -fsS "$BASE_URL/api/platform/service-mappings" | grep '"skywalking-smoke-room-resource"' >/dev/null

echo "[console] probe prometheus"
curl -fsS "$BASE_URL/demo/platform/prometheus" | grep 'nexary_demo_probe_calls_total' >/dev/null
for attempt in {1..8}; do
  if curl -fsS "$PROMETHEUS_URL/-/ready" >/dev/null \
    && curl -fsS "$PROMETHEUS_URL/api/v1/query?query=nexary_demo_probe_calls_total" | grep '"status":"success"' >/dev/null; then
    break
  fi
  if [[ "$attempt" == "8" ]]; then
    echo "Prometheus did not expose nexary_demo_probe_calls_total" >&2
    exit 1
  fi
  sleep 2
done

echo "[console] skywalking ui"
for attempt in {1..12}; do
  if curl -fsSL "$SKYWALKING_URL" | grep -i 'skywalking' >/dev/null; then
    break
  fi
  if [[ "$attempt" == "12" ]]; then
    echo "SkyWalking UI did not become readable at $SKYWALKING_URL" >&2
    exit 1
  fi
  sleep 5
done

echo "[console] skywalking traces"
SKYWALKING_TABLE_SUFFIX="$(date -u +%Y%m%d)"
for attempt in {1..12}; do
  SEGMENT_COUNT="$(docker exec nexary-console-postgres psql -U "${NEXARY_DEMO_POSTGRES_USER:-nexary}" -d nexary -tAc "select count(*) from segment_${SKYWALKING_TABLE_SUFFIX};" 2>/dev/null || echo 0)"
  if [[ "${SEGMENT_COUNT//[[:space:]]/}" =~ ^[0-9]+$ ]] && [[ "${SEGMENT_COUNT//[[:space:]]/}" -gt 0 ]]; then
    break
  fi
  if [[ "$attempt" == "12" ]]; then
    echo "SkyWalking did not store traced segments" >&2
    exit 1
  fi
  sleep 5
done

echo "[console] page html"
curl -fsSL "$BASE_URL/nexary/console" | grep '/nexary/console/assets/' >/dev/null

echo "console smoke passed: $BASE_URL/nexary/console"
