# Local Validation Guide

This page explains how to validate Nexary locally: compilation, tests, middleware integration, and local publication artifacts. It is not an internal task-process document.

## What to Read First

- validation checklist: [verification-acceptance.md](verification-acceptance.md)
- middleware integration: [integration.md](integration.md)
- release checklist: [release.md](release.md)

## Baseline Validation

```bash
./gradlew check
```

This covers baseline compilation, unit tests, and standard quality gates.

## Local Middleware Validation

Start local Redis, Kafka, RocketMQ, MySQL, and XXL-JOB Admin:

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
```

Run integration tests:

```bash
./scripts/middleware/run-integration-tests.sh
```

When validating one capability, run the relevant scoped tests. If the change affects real Redis, Kafka, RocketMQ, or XXL-JOB behavior, keep the exact commands and results for PR review.

## Local Publication Validation

```bash
./gradlew publishToMavenLocal
```

Release-related changes should confirm that sources, javadocs, and the primary starter / provider modules can be generated and installed into the local Maven repository.

## Evidence Checklist

Before a PR or release, record:

- affected capability: cache, messaging, job, or boot.
- Gradle commands that were run.
- whether middleware smoke passed.
- whether real middleware integration tests were run.
- whether `publishToMavenLocal` passed.
- known residual risks and unsupported claims.

## Boundaries

- Validation commands prove only the behavior covered by the tests; they do not imply production strong consistency, exactly-once delivery, or platform-managed lifecycle.
- Failing tests should explain cause, impact, and whether they block release.
- Unverified providers, configuration, or deployment modes must not be documented as supported.
