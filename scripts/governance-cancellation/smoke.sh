#!/usr/bin/env bash
set -euo pipefail

base_url="${NEXARY_GOVERNANCE_CANCELLATION_BASE_URL:-}"
endpoint="${NEXARY_GOVERNANCE_CANCELLATION_ENDPOINT:-/governance/cancellation/slow/smoke?durationMillis=3000}"
expected_status="${NEXARY_GOVERNANCE_CANCELLATION_EXPECTED_STATUS:-200}"

if [[ -z "${base_url}" ]]; then
  cat <<'MSG'
Governance cancellation smoke check.

Start nexary-sample-governance, then set the base URL:

  NEXARY_GOVERNANCE_CANCELLATION_BASE_URL=http://localhost:28091 scripts/governance-cancellation/smoke.sh

The script sends a low-cardinality cancellation signal and verifies:
  - the sample endpoint returns quickly
  - diagnostics includes cancelledCount
  - events include CLIENT_DISCONNECTED
  - cancellation id is not exposed by diagnostics
MSG
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for the governance cancellation smoke check." >&2
  exit 127
fi

tmp_body="$(mktemp)"
trap 'rm -f "${tmp_body}"' EXIT

url="${base_url%/}/${endpoint#/}"
cancel_id="smoke-hidden-cancel-id"
status="$(
  curl -sS -o "${tmp_body}" -w "%{http_code}" \
    -H "Nexary-Cancellation-Id: ${cancel_id}" \
    -H "Nexary-Cancel-Reason: CLIENT_DISCONNECTED" \
    "${url}"
)"

if [[ "${status}" != "${expected_status}" ]]; then
  echo "Unexpected status from ${url}: got ${status}, expected ${expected_status}." >&2
  echo "Response body:" >&2
  sed -n '1,120p' "${tmp_body}" >&2
  exit 1
fi

summary="$(curl -sS "${base_url%/}/nexary/governance/summary")"
events="$(curl -sS "${base_url%/}/nexary/governance/events")"

if [[ "${summary}" != *'"cancelledCount"'* ]]; then
  echo "Diagnostics summary does not expose cancelledCount." >&2
  echo "${summary}" >&2
  exit 1
fi

if [[ "${events}" != *'"cancellationReason":"CLIENT_DISCONNECTED"'* ]]; then
  echo "Diagnostics events did not record CLIENT_DISCONNECTED." >&2
  echo "${events}" >&2
  exit 1
fi

if [[ "${events}" == *"${cancel_id}"* ]]; then
  echo "Diagnostics events leaked cancellation id." >&2
  exit 1
fi

echo "Governance cancellation smoke passed: ${url}"
