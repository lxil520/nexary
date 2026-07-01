#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/console/docker-compose.yml"
ENV_FILE="$ROOT_DIR/deploy/console/.env"
APP_BUILD_DIR="$ROOT_DIR/deploy/console/build"
APP_JAR_DIR="$ROOT_DIR/nexary-samples/nexary-sample-governance-platform/build/libs"

if [[ ! -f "$ENV_FILE" ]]; then
  ENV_FILE="$ROOT_DIR/deploy/console/.env.example"
fi

cd "$ROOT_DIR"
./gradlew :nexary-samples:nexary-sample-governance-platform:bootJar -PnexaryVersion="${NEXARY_VERSION:-0.23.0}"
rm -rf "$APP_BUILD_DIR"
mkdir -p "$APP_BUILD_DIR"
APP_JAR="$(find "$APP_JAR_DIR" -maxdepth 1 -type f -name 'nexary-sample-governance-platform-*.jar' ! -name '*plain*' | sort | tail -n 1)"
if [[ -z "$APP_JAR" ]]; then
  echo "No platform sample boot jar found in $APP_JAR_DIR" >&2
  exit 1
fi
cp "$APP_JAR" "$APP_BUILD_DIR/app.jar"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build --wait --wait-timeout 180

set -a
source "$ENV_FILE"
set +a

echo "Nexary governance Console: http://127.0.0.1:${NEXARY_CONSOLE_PORT:-18090}/nexary/console"
echo "SkyWalking UI: http://127.0.0.1:${NEXARY_SKYWALKING_UI_PORT:-18097}"
echo "Prometheus: http://127.0.0.1:${NEXARY_PROMETHEUS_PORT:-18095}"
echo "RabbitMQ management: http://127.0.0.1:${NEXARY_DEMO_RABBITMQ_MANAGEMENT_PORT:-18094}"
