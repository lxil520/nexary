#!/usr/bin/env bash
set -euo pipefail

base_url="${NEXARY_GOVERNANCE_SENTINEL_BASE_URL:-}"

if [[ -z "${base_url}" ]]; then
  cat <<'MSG'
Sentinel provider smoke check.

Start nexary-sample-governance-sentinel, then set the base URL:

  NEXARY_GOVERNANCE_SENTINEL_BASE_URL=http://localhost:8080 scripts/governance-sentinel/smoke.sh

The script verifies:
  - repeated business calls are blocked by Sentinel
  - diagnostics exposes blockedCount and sentinelResourceCount
  - events expose a low-cardinality RATE_LIMITED block reason
  - diagnostics does not expose Sentinel origin or stack traces
MSG
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for the Sentinel provider smoke check." >&2
  exit 127
fi

base_url="${base_url%/}"

curl -fsS "${base_url}/governance/sentinel/rate" >/dev/null
curl -fsS "${base_url}/governance/sentinel/rate" >/dev/null

summary="$(curl -fsS "${base_url}/nexary/governance/summary")"
events="$(curl -fsS "${base_url}/nexary/governance/events")"
resources="$(curl -fsS "${base_url}/nexary/governance/resources")"

if [[ "${summary}" != *'"blockedCount"'* || "${summary}" != *'"sentinelResourceCount"'* ]]; then
  echo "Diagnostics summary is missing Sentinel counters." >&2
  echo "${summary}" >&2
  exit 1
fi

if [[ "${events}" != *'"engine":"SENTINEL"'* || "${events}" != *'"blockReason":"RATE_LIMITED"'* ]]; then
  echo "Diagnostics events did not record a Sentinel RATE_LIMITED block." >&2
  echo "${events}" >&2
  exit 1
fi

if [[ "${resources}" != *'"engine":"SENTINEL"'* ]]; then
  echo "Diagnostics resources did not expose the Sentinel engine label." >&2
  echo "${resources}" >&2
  exit 1
fi

if [[ "${events}" == *'"origin"'* || "${events}" == *'"stackTrace"'* || "${events}" == *'"exception"'* ]]; then
  echo "Diagnostics events exposed high-cardinality or exception details." >&2
  exit 1
fi

echo "Sentinel provider smoke passed: ${base_url}"
