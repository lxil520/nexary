#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: scripts/release/check-central.sh <version> [artifactId]" >&2
  exit 2
fi

VERSION="$1"
EXTRA_ARTIFACT="${2:-nexary-cache-spring-boot-starter}"
BASE_URL="https://repo.maven.apache.org/maven2/com/aweimao"

check_artifact() {
  local artifact="$1"
  local url="${BASE_URL}/${artifact}/${VERSION}/${artifact}-${VERSION}.pom"
  local status
  status="$(curl -sS -o /dev/null -w '%{http_code}' -I "${url}")"
  printf '%s %s %s\n' "${status}" "${artifact}" "${url}"
  [ "${status}" = "200" ]
}

check_artifact "nexary-bom"
check_artifact "${EXTRA_ARTIFACT}"
