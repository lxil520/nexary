#!/usr/bin/env bash
set -euo pipefail

base_url="${NEXARY_GOVERNANCE_INSTANCE_HEALTH_BASE_URL:-}"

if [[ -z "${base_url}" ]]; then
  cat <<'MSG'
Instance health smoke check.

Start nexary-sample-governance with the instance-health profile, then set the base URL:

  ./gradlew :nexary-samples:nexary-sample-governance:run --args='--spring.profiles.active=instance-health'
  NEXARY_GOVERNANCE_INSTANCE_HEALTH_BASE_URL=http://localhost:8080 scripts/governance-instance-health/smoke.sh

The script verifies:
  - instance-a remains HEALTHY
  - instance-b is marked by SLOW_RATIO
  - instance-c becomes a QUARANTINE_CANDIDATE
  - diagnostics exposes quarantineCandidateCount and low-cardinality event fields
  - diagnostics does not expose raw IP addresses, payloads, or stack traces
MSG
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for the instance health smoke check." >&2
  exit 127
fi

base_url="${base_url%/}"

scenario="$(curl -fsS -X POST "${base_url}/governance/instance-health/scenario")"
summary="$(curl -fsS "${base_url}/nexary/governance/summary")"
events="$(curl -fsS "${base_url}/nexary/governance/events")"
health="$(curl -fsS "${base_url}/nexary/governance/instance-health")"

if [[ "${scenario}" != *'"scenario":"scenario"'* || "${scenario}" != *'"instanceKey":"instance-a"'* ]]; then
  echo "Instance health scenario endpoint did not return expected instance aliases." >&2
  echo "${scenario}" >&2
  exit 1
fi

if [[ "${health}" != *'"instanceKey":"instance-a"'* || "${health}" != *'"state":"HEALTHY"'* ]]; then
  echo "Instance-a was not visible as HEALTHY." >&2
  echo "${health}" >&2
  exit 1
fi

if [[ "${health}" != *'"instanceKey":"instance-b"'* || "${health}" != *'"quarantineReason":"SLOW_RATIO"'* ]]; then
  echo "Instance-b did not expose the bounded SLOW_RATIO reason." >&2
  echo "${health}" >&2
  exit 1
fi

if [[ "${health}" != *'"instanceKey":"instance-c"'* || "${health}" != *'"state":"QUARANTINE_CANDIDATE"'* ]]; then
  echo "Instance-c was not marked as a quarantine candidate." >&2
  echo "${health}" >&2
  exit 1
fi

if [[ "${summary}" != *'"quarantineCandidateCount"'* || "${summary}" == *'"quarantineCandidateCount":0'* ]]; then
  echo "Diagnostics summary did not expose quarantineCandidateCount > 0." >&2
  echo "${summary}" >&2
  exit 1
fi

if [[ "${events}" != *'"action":"QUARANTINE_CANDIDATE"'* || "${events}" != *'"recoveryAdvice":"QUARANTINE_CANDIDATE"'* ]]; then
  echo "Diagnostics events did not expose bounded instance health state changes." >&2
  echo "${events}" >&2
  exit 1
fi

if [[ "${health}${events}" == *'10.'* || "${health}${events}" == *'"payload"'* || "${health}${events}" == *'"stackTrace"'* ]]; then
  echo "Diagnostics exposed raw network, payload, or stack trace fields." >&2
  exit 1
fi

echo "Instance health smoke passed: ${base_url}"
