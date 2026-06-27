#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${NEXARY_GOVERNANCE_PLATFORM_BASE_URL:-http://localhost:18092}"

topology="$(curl -fsS "${BASE_URL}/api/platform/topology")"
services="$(curl -fsS "${BASE_URL}/api/platform/services")"
incidents="$(curl -fsS "${BASE_URL}/api/platform/incidents")"
connectors="$(curl -fsS "${BASE_URL}/api/platform/connectors")"
console="$(curl -fsS "${BASE_URL}/nexary/console/")"

printf '%s' "${topology}" | grep -q 'open-api'
printf '%s' "${topology}" | grep -q 'redis-main'
printf '%s' "${services}" | grep -q 'room-resource'
printf '%s' "${incidents}" | grep -q 'QUARANTINE_CANDIDATE'
printf '%s' "${connectors}" | grep -q 'nexary-sdk-demo'
printf '%s' "${console}" | grep -q 'Nexary Console'

echo "Nexary governance platform smoke passed at ${BASE_URL}"
