#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: scripts/release/preflight.sh <version>" >&2
  exit 2
fi

VERSION="$1"
ROOT="$(git rev-parse --show-toplevel)"
cd "${ROOT}"

fail() {
  echo "release preflight failed: $*" >&2
  exit 1
}

[ "$(git config user.name)" = "lxil520" ] || fail "git user.name must be lxil520"
[ "$(git config user.email)" = "15406983+lxil520@users.noreply.github.com" ] ||
  fail "git user.email must be 15406983+lxil520@users.noreply.github.com"

if [ -n "$(git ls-files AGENTS.md _system .gradle build .DS_Store .codegraph)" ]; then
  git ls-files AGENTS.md _system .gradle build .DS_Store .codegraph
  fail "private/generated files are tracked"
fi

if git ls-files | rg '(^|/)(spring5|spring7)(/|$)' >/tmp/nexary-release-spring-legacy.txt; then
  cat /tmp/nexary-release-spring-legacy.txt
  fail "legacy spring5/spring7 paths are tracked"
fi

INTERNAL_PATTERN='AGENTS\.md|_system|brand@yskj|yskj\.com|内部规范|对话记录|source_thread_id|codex_delegation|spring5|spring7'
if rg -n "${INTERNAL_PATTERN}" README.md README.en.md docs nexary-*/README.md nexary-samples/*/README*.md; then
  fail "public docs contain private or legacy terms"
fi

TEMPLATE_PATTERN='赋能|打造|闭环|体系|聚焦|智能化|降本增效|适用场景|当前范围|能力入口|provider-neutral|SPI/provider dependency|bridge-shaped|focused sample|adoption|showcase'
if rg -n "${TEMPLATE_PATTERN}" README.md README.en.md docs/zh/index.md docs/en/index.md docs/zh/getting-started.md docs/en/getting-started.md docs/zh/cache.md docs/en/cache.md docs/zh/messaging.md docs/en/messaging.md docs/zh/job.md docs/en/job.md docs/zh/configuration.md docs/en/configuration.md docs/zh/samples.md docs/en/samples.md docs/zh/observation.md docs/en/observation.md docs/zh/compatibility.md docs/en/compatibility.md docs/zh/troubleshooting.md docs/en/troubleshooting.md nexary-*/README.md nexary-samples/*/README*.md; then
  fail "public docs contain template/AI wording"
fi

if ! rg -n "^nexaryVersion=${VERSION}$" gradle.properties >/dev/null; then
  fail "gradle.properties must set nexaryVersion=${VERSION}"
fi

if ! rg -n "version = findProperty\\('nexaryVersion'\\) \\?: '${VERSION}'" build.gradle >/dev/null; then
  fail "build.gradle fallback version must be ${VERSION}"
fi

ruby -e 'require "yaml"; YAML.load_file(".github/workflows/release.yml")' ||
  fail "release workflow YAML is invalid"

git diff --check
./gradlew verifyCentralReleaseInputs -PnexaryVersion="${VERSION}" -PnexarySigningKey=dummy -PnexarySigningPassword=dummy --quiet

echo "release preflight passed for ${VERSION}"
