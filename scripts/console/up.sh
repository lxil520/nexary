#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/console/docker-compose.yml"
ENV_FILE="$ROOT_DIR/deploy/console/.env"
APP_BUILD_DIR="$ROOT_DIR/deploy/console/build/nexary-sample-governance"
APP_INSTALL_DIR="$ROOT_DIR/nexary-samples/nexary-sample-governance/build/install/nexary-sample-governance"

if [[ ! -f "$ENV_FILE" ]]; then
  ENV_FILE="$ROOT_DIR/deploy/console/.env.example"
fi

cd "$ROOT_DIR"
./gradlew :nexary-samples:nexary-sample-governance:installDist -PnexaryVersion="${NEXARY_VERSION:-0.18.0}"
rm -rf "$APP_BUILD_DIR"
mkdir -p "$(dirname "$APP_BUILD_DIR")"
cp -R "$APP_INSTALL_DIR" "$APP_BUILD_DIR"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build --wait --wait-timeout 180

set -a
source "$ENV_FILE"
set +a

echo "Nexary governance Console: http://127.0.0.1:${NEXARY_CONSOLE_PORT:-18090}/nexary/console"
