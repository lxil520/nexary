#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${NEXARY_GOVERNANCE_PLATFORM_BASE_URL:-http://127.0.0.1:18092}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

curl -fsS "${BASE_URL}/api/platform/topology" -o "${TMP_DIR}/topology.json"
curl -fsS "${BASE_URL}/api/platform/services" -o "${TMP_DIR}/services.json"
curl -fsS "${BASE_URL}/api/platform/incidents" -o "${TMP_DIR}/incidents.json"
curl -fsS "${BASE_URL}/api/platform/connectors" -o "${TMP_DIR}/connectors.json"
curl -fsS "${BASE_URL}/api/platform/plans" -o "${TMP_DIR}/plans.json"
curl -fsS "${BASE_URL}/api/platform/notification-routes" -o "${TMP_DIR}/notification-routes.json"
curl -fsS "${BASE_URL}/nexary/console/" -o "${TMP_DIR}/console.html"

grep -q 'open-api' "${TMP_DIR}/topology.json"
grep -q 'redis-main' "${TMP_DIR}/topology.json"
grep -q 'room-resource' "${TMP_DIR}/services.json"
grep -q 'DEPENDENCY_TIMEOUT' "${TMP_DIR}/incidents.json"
grep -q 'primaryResourceKey' "${TMP_DIR}/incidents.json"
grep -q 'evidenceCount' "${TMP_DIR}/incidents.json"
grep -q 'HOST_WATERMARK' "${TMP_DIR}/incidents.json"
grep -q 'nexary-sdk-demo' "${TMP_DIR}/connectors.json"
grep -q 'skywalking-readonly-demo' "${TMP_DIR}/connectors.json"
grep -q '"planKey"' "${TMP_DIR}/plans.json"
grep -q '"routeKey"' "${TMP_DIR}/notification-routes.json"
grep -q 'Nexary Console' "${TMP_DIR}/console.html"

PLAN_KEY="$(sed -n 's/.*"planKey":"\([^"]*\)".*/\1/p' "${TMP_DIR}/plans.json" | head -n 1)"
if [[ -z "${PLAN_KEY}" ]]; then
  echo "No governance review plan returned" >&2
  exit 1
fi
curl -fsS -X POST "${BASE_URL}/api/platform/plans/${PLAN_KEY}/dry-run" -o "${TMP_DIR}/dry-run.json"
curl -fsS -X POST "${BASE_URL}/api/platform/plans/${PLAN_KEY}/export-review" -o "${TMP_DIR}/export-review.json"
grep -q '"passed":' "${TMP_DIR}/dry-run.json"
grep -q '"risk"' "${TMP_DIR}/dry-run.json"
grep -q '"summary":"TEST / DRY-RUN only; external systems are not changed"' "${TMP_DIR}/dry-run.json"
grep -q '"mode":"REVIEW_ONLY"' "${TMP_DIR}/export-review.json"
grep -q '"summary"' "${TMP_DIR}/export-review.json"

ROUTE_KEY="$(sed -n 's/.*"routeKey":"\([^"]*\)".*/\1/p' "${TMP_DIR}/notification-routes.json" | head -n 1)"
if [[ -z "${ROUTE_KEY}" ]]; then
  echo "No notification route returned" >&2
  exit 1
fi
curl -fsS -X POST "${BASE_URL}/api/platform/notification-routes/${ROUTE_KEY}/preview" -o "${TMP_DIR}/notification-preview.json"
curl -fsS -X POST "${BASE_URL}/api/platform/notification-routes/${ROUTE_KEY}/test" -o "${TMP_DIR}/notification-test.json"
grep -q '"mode":"' "${TMP_DIR}/notification-preview.json"
grep -q 'TEST / DRY-RUN' "${TMP_DIR}/notification-preview.json"
grep -q '"accepted":false' "${TMP_DIR}/notification-test.json"
grep -q '"status":"TEST_DISABLED"' "${TMP_DIR}/notification-test.json"
curl -fsS "${BASE_URL}/api/platform/audit-records" -o "${TMP_DIR}/audit-records.json"
grep -q '"action"' "${TMP_DIR}/audit-records.json"

echo "Nexary governance platform smoke passed at ${BASE_URL}"
