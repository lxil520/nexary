#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${NEXARY_GOVERNANCE_PLATFORM_BASE_URL:-http://127.0.0.1:18092}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

curl -fsS "${BASE_URL}/api/platform/topology" -o "${TMP_DIR}/topology.json"
curl -fsS "${BASE_URL}/api/platform/services" -o "${TMP_DIR}/services.json"
curl -fsS "${BASE_URL}/api/platform/incidents" -o "${TMP_DIR}/incidents.json"
curl -fsS "${BASE_URL}/api/platform/connectors" -o "${TMP_DIR}/connectors.json"
curl -fsS "${BASE_URL}/nexary/console/" -o "${TMP_DIR}/console.html"

grep -q 'open-api' "${TMP_DIR}/topology.json"
grep -q 'redis-main' "${TMP_DIR}/topology.json"
grep -q 'room-resource' "${TMP_DIR}/services.json"
grep -q 'QUARANTINE_CANDIDATE' "${TMP_DIR}/incidents.json"
grep -q 'primaryResourceKey' "${TMP_DIR}/incidents.json"
grep -q 'evidenceCount' "${TMP_DIR}/incidents.json"
grep -q 'INSTANCE_HEALTH' "${TMP_DIR}/incidents.json"
grep -q 'sentinel-open-api-profile' "${TMP_DIR}/incidents.json"
grep -q 'nexary-sdk-demo' "${TMP_DIR}/connectors.json"
grep -q 'Nexary Console' "${TMP_DIR}/console.html"

echo "Nexary governance platform smoke passed at ${BASE_URL}"
