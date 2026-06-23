#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: scripts/release/watch-github-run.sh <run-id>" >&2
  exit 2
fi

RUN_ID="$1"
REPO="${NEXARY_GITHUB_REPO:-lxil520/nexary}"
API="https://api.github.com/repos/${REPO}/actions/runs/${RUN_ID}"

python_json() {
  local mode="$1"
  local file="$2"
  python3 - "${mode}" "${file}" <<'PY'
import json
import sys

mode = sys.argv[1]
with open(sys.argv[2], encoding="utf-8") as source:
    data = json.load(source)

if mode == "run":
    print(f"run {data['id']} status={data['status']} conclusion={data.get('conclusion')} attempt={data.get('run_attempt')} url={data.get('html_url')}")
elif mode == "jobs":
    for job in data.get("jobs", []):
        print(f"job {job['name']} status={job['status']} conclusion={job.get('conclusion')} started={job.get('started_at')} completed={job.get('completed_at')}")
        for step in job.get("steps", []):
            if step.get("conclusion") == "failure" or step.get("status") == "in_progress":
                print(f"  step {step['name']} status={step['status']} conclusion={step.get('conclusion')}")
PY
}

RUN_JSON_FILE="$(mktemp)"
JOBS_JSON_FILE="$(mktemp)"
trap 'rm -f "${RUN_JSON_FILE}" "${JOBS_JSON_FILE}"' EXIT

curl -sS -H 'Cache-Control: no-cache' "${API}" >"${RUN_JSON_FILE}"
curl -sS -H 'Cache-Control: no-cache' "${API}/jobs" >"${JOBS_JSON_FILE}"

python_json run "${RUN_JSON_FILE}"
python_json jobs "${JOBS_JSON_FILE}"

STATUS="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["status"])' "${RUN_JSON_FILE}")"
CONCLUSION="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8")).get("conclusion") or "")' "${RUN_JSON_FILE}")"

if [ "${STATUS}" != "completed" ]; then
  exit 10
fi

[ "${CONCLUSION}" = "success" ]
