# Contribution and Maintenance

This page is for public contributors and maintainers. It explains how Nexary accepts requests, reviews changes, and validates release quality. It does not contain internal task records or process conversations.

## Core Principles

1. Clarify the problem before deciding whether to change API, implementation, samples, or docs.
2. Cache, Messaging, and Job are maintained as separate capabilities so implementation details do not leak across boundaries.
3. Every capability change should consider public API, configuration, samples, documentation, and tests together.
4. Local validation is closed through Docker, smoke checks, integration tests, and `publishToMavenLocal`.
5. Governance capabilities become separate modules only after their scope is clear.

## Contribution Types

| Type | Recommended flow |
| --- | --- |
| Bug fix | Open an issue or PR with reproduction steps and test results |
| Provider integration | Open an issue first describing the scenario, provider semantics, and test plan |
| Public API change | Discuss the design before opening a PR |
| Docs / sample | Explain the target reader, adoption path, and validation commands |
| Governance capability | Define the boundary before mixing it into cache, messaging, or job |

## Change Requirements

- Public APIs must not expose native Redis, Kafka, RocketMQ, XXL-JOB, Caffeine, or similar provider types.
- Configuration prefixes stay under `nexary.*`.
- Example configuration may use only local or fake values; never include real secrets, internal addresses, or historical project identifiers.
- User-facing documentation changes should update Chinese and English versions together.
- Samples must show the business code users should copy; users should not have to read provider wiring first.
- New capabilities must describe suitable use cases, non-goals, and residual risks.

## Validation Requirements

Before merging, changes should provide:

- unit or integration test results for affected modules.
- `./scripts/middleware/smoke.sh` results when middleware is involved.
- integration test results when real middleware behavior changes.
- `publishToMavenLocal` results when published artifacts change.
- paths to updated docs and samples.

## Maintenance Boundaries

- `0.1.x` does not include sidecars, agents, control planes, or admin consoles.
- JDK 8 / Spring Boot 2 compatibility must not pollute the current mainline; if supported, it needs an independent compatibility line and verification gate.
- New providers should not force the primary API to become heavy.
- Unverified capabilities must not be documented as supported.
