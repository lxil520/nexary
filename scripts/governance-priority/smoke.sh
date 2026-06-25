#!/usr/bin/env bash
set -euo pipefail

base_url="${NEXARY_GOVERNANCE_PRIORITY_BASE_URL:-${NEXARY_GOVERNANCE_SENTINEL_BASE_URL:-}}"

if [[ -z "${base_url}" ]]; then
  cat <<'MSG'
Priority isolation smoke check.

Start nexary-sample-governance-sentinel, then set the base URL:

  NEXARY_GOVERNANCE_PRIORITY_BASE_URL=http://localhost:8080 scripts/governance-priority/smoke.sh

The script verifies:
  - ONLINE/HIGH traffic succeeds on the shared resource
  - BATCH/LOW traffic is isolated after its low-priority window is exhausted
  - diagnostics exposes isolatedCount, trafficClass, priority, and isolationReason
  - mixed traffic on the same resource records a low-cardinality warning
MSG
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for the priority isolation smoke check." >&2
  exit 127
fi

base_url="${base_url%/}"

online_first="$(curl -fsS "${base_url}/governance/sentinel/priority/online")"
batch_first="$(curl -fsS "${base_url}/governance/sentinel/priority/batch")"
batch_second="$(curl -fsS "${base_url}/governance/sentinel/priority/batch")"
online_second="$(curl -fsS "${base_url}/governance/sentinel/priority/online")"

summary="$(curl -fsS "${base_url}/nexary/governance/summary")"
events="$(curl -fsS "${base_url}/nexary/governance/events")"

if [[ "${online_first}" != *'"source":"business"'* || "${online_second}" != *'"source":"business"'* ]]; then
  echo "ONLINE/HIGH traffic did not continue through the shared resource." >&2
  echo "first=${online_first}" >&2
  echo "second=${online_second}" >&2
  exit 1
fi

if [[ "${batch_first}" != *'"source":"business"'* || "${batch_second}" != *'"source":"fallback"'* ]]; then
  echo "BATCH/LOW traffic was not isolated after the low-priority window was exhausted." >&2
  echo "first=${batch_first}" >&2
  echo "second=${batch_second}" >&2
  exit 1
fi

if [[ "${summary}" != *'"isolatedCount"'* || "${summary}" == *'"isolatedCount":0'* ]]; then
  echo "Diagnostics summary did not expose isolatedCount > 0." >&2
  echo "${summary}" >&2
  exit 1
fi

if [[ "${summary}" != *'"trafficClassCounts"'* || "${summary}" != *'"priorityCounts"'* ]]; then
  echo "Diagnostics summary is missing traffic or priority counts." >&2
  echo "${summary}" >&2
  exit 1
fi

if [[ "${events}" != *'"trafficClass":"BATCH"'* || "${events}" != *'"priority":"LOW"'* ]]; then
  echo "Diagnostics events did not expose fixed BATCH/LOW fields." >&2
  echo "${events}" >&2
  exit 1
fi

if [[ "${events}" != *'"isolationReason":"PRIORITY_RATE_LIMITED"'* || "${events}" != *'"isolationReason":"MIXED_TRAFFIC"'* ]]; then
  echo "Diagnostics events did not expose priority isolation and mixed-traffic warning reasons." >&2
  echo "${events}" >&2
  exit 1
fi

if [[ "${events}" == *'"tenant"'* || "${events}" == *'"bizKey"'* || "${events}" == *'"payload"'* || "${events}" == *'"stackTrace"'* ]]; then
  echo "Diagnostics events exposed high-cardinality or exception details." >&2
  exit 1
fi

echo "Priority isolation smoke passed: ${base_url}"
