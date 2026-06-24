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

echo "[console] trigger local governance events"
curl -fsS "$BASE_URL/governance/profiles/u-1" >/dev/null
curl -fsS "$BASE_URL/governance/profiles/u-2" >/dev/null
curl -fsS "$BASE_URL/governance/profiles/u-3" >/dev/null

echo "[console] diagnostics summary"
curl -fsS "$BASE_URL/nexary/governance/summary" | grep '"resourceCount"' >/dev/null

echo "[console] diagnostics resources"
curl -fsS "$BASE_URL/nexary/governance/resources" | grep '"resourceKey"' >/dev/null

echo "[console] page html"
curl -fsSL "$BASE_URL/nexary/console" | grep '/nexary/console/assets/' >/dev/null

echo "console smoke passed: $BASE_URL/nexary/console"
