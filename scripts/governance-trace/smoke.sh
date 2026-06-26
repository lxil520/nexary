#!/usr/bin/env bash
set -euo pipefail

base_url="${NEXARY_GOVERNANCE_TRACE_BASE_URL:-}"

if [[ -z "${base_url}" ]]; then
  cat <<'MSG'
Governance trace smoke check.

Start nexary-sample-governance with the trace profile, then set the base URL:

  ./gradlew :nexary-samples:nexary-sample-governance:run --args='--spring.profiles.active=trace'
  NEXARY_GOVERNANCE_TRACE_BASE_URL=http://localhost:8080 scripts/governance-trace/smoke.sh

The script verifies:
  - normal calls create SUCCESS traces
  - deadline, retry-stop, priority isolation, and instance-health scenarios create bounded stop reasons
  - diagnostics exposes traces, fault summary, trace stage, and suggested resource fields
  - diagnostics does not expose payloads, URL query, exception text, or stack traces
MSG
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for the governance trace smoke check." >&2
  exit 127
fi

base_url="${base_url%/}"

online_high="$(curl -fsS "${base_url}/governance/trace/priority?priority=high")"
deadline="$(curl -fsS "${base_url}/governance/trace/deadline/trace-deadline")"
retry_stop="$(curl -fsS "${base_url}/governance/trace/retry-stop")"
batch_first="$(curl -fsS "${base_url}/governance/trace/priority?priority=low")"
batch_second="$(curl -fsS "${base_url}/governance/trace/priority?priority=low")"
instance_health="$(curl -fsS -X POST "${base_url}/governance/trace/instance-health")"

summary="$(curl -fsS "${base_url}/nexary/governance/summary")"
fault_summary="$(curl -fsS "${base_url}/nexary/governance/faults/summary")"
events="$(curl -fsS "${base_url}/nexary/governance/events")"
traces="$(curl -fsS "${base_url}/nexary/governance/traces")"

if [[ "${online_high}" != *'"source":"primary"'* ]]; then
  echo "ONLINE/HIGH trace scenario did not execute business work." >&2
  echo "${online_high}" >&2
  exit 1
fi

if [[ "${deadline}" != *'"source":"fallback"'* ]]; then
  echo "Deadline trace scenario did not return fallback output." >&2
  echo "${deadline}" >&2
  exit 1
fi

if [[ "${retry_stop}" != *'"scenario":"retry_stop"'* || "${retry_stop}" != *'"status":"stopped"'* ]]; then
  echo "Retry-stop trace scenario did not stop retries." >&2
  echo "${retry_stop}" >&2
  exit 1
fi

if [[ "${batch_second}" != *'"source":"fallback"'* ]]; then
  echo "BATCH/LOW trace scenario did not isolate low-priority traffic." >&2
  echo "first=${batch_first}" >&2
  echo "second=${batch_second}" >&2
  exit 1
fi

if [[ "${instance_health}" != *'"scenario":"scenario"'* || "${instance_health}" != *'"instanceKey":"instance-c"'* ]]; then
  echo "Instance-health trace scenario did not expose demo instance aliases." >&2
  echo "${instance_health}" >&2
  exit 1
fi

if [[ "${summary}" != *'"faultTraceCount"'* || "${summary}" == *'"faultTraceCount":0'* ]]; then
  echo "Runtime summary did not expose faultTraceCount > 0." >&2
  echo "${summary}" >&2
  exit 1
fi

if [[ "${fault_summary}" != *'"traceCount"'* || "${fault_summary}" == *'"traceCount":0'* || "${fault_summary}" == *'"stoppedCount":0'* ]]; then
  echo "Fault summary did not expose retained stopped traces." >&2
  echo "${fault_summary}" >&2
  exit 1
fi

if [[ "${traces}" != *'"terminalOutcome":"SUCCESS"'* || "${traces}" != *'"primaryStopReason":"DEADLINE_EXPIRED"'* ]]; then
  echo "Trace diagnostics are missing SUCCESS or DEADLINE_EXPIRED traces." >&2
  echo "${traces}" >&2
  exit 1
fi

if [[ "${traces}" != *'"retryStopReason"'* || "${traces}" != *'"isolationReason"'* || "${traces}" != *'"instanceHealthState":"QUARANTINE_CANDIDATE"'* ]]; then
  echo "Trace diagnostics are missing retry, isolation, or instance-health fields." >&2
  echo "${traces}" >&2
  exit 1
fi

if [[ "${events}" != *'"traceStage"'* || "${events}" != *'"tracePrimaryStopReason"'* ]]; then
  echo "Events did not expose trace stage or primary stop reason." >&2
  echo "${events}" >&2
  exit 1
fi

if [[ "${traces}${events}" == *'"payload"'* || "${traces}${events}" == *'"query"'* || "${traces}${events}" == *'"stackTrace"'* || "${traces}${events}" == *'"exception"'* ]]; then
  echo "Trace diagnostics exposed payload, query, exception, or stack trace details." >&2
  exit 1
fi

echo "Governance trace smoke passed: ${base_url}"
