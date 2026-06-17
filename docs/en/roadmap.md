# Roadmap

This roadmap explains release boundaries and future issue / PR priorities. It is not an internal work log and does not describe maintainer conversations.

## Versioning Principles

- Stabilize one modern mainline before considering compatibility branches.
- Every new capability needs public API, samples, documentation, and test evidence before entering the mainline.
- Provider integrations should strengthen the shared abstractions first instead of making every middleware equally urgent.
- Governance capabilities should grow as separate modules and must not bloat the primary cache, messaging, or job APIs.

## `0.1.0` Baseline

`0.1.0` is the first externally explainable and locally verifiable baseline for later releases.

Included scope:

- Redis cache, TTL, batch, cache-aside, and distributed lock abstraction.
- Redis tiered cache mode with internal JVM local L1 and best-effort Redis Pub/Sub invalidation.
- Separate atomic counter API with Redis single-key atomic counter implementation.
- Kafka / RocketMQ / Redis queue / Disruptor messaging abstractions.
- Duplicate-consumption protection, bounded retry, and terminal failure / dead-letter abstractions.
- Local scheduler, sharding, worker topology, and job execution lifecycle.
- XXL-JOB bridge that maps external triggers and shard metadata to `NexaryJob`.
- Starter selector and SPI provider adoption modes.
- Local Docker middleware, smoke checks, and integration tests.
- Bilingual docs, samples, and release checklist.

`0.1.0` does not claim:

- cache strong consistency, exactly-once invalidation, fencing tokens, or version-checked reads.
- counter multi-key transactions, quota systems, or TTL refresh-on-write.
- messaging exactly-once delivery, global ordering, or distributed transactions.
- XXL-JOB Admin registration, platform scheduling lifecycle, or full executor callback ownership.
- running job cancellation or durable job execution store.
- JDK 8 / Spring Boot 2 compatibility line.
- sidecars, agents, control planes, or admin consoles.

Release exit criteria:

- stable `./gradlew check`.
- stable local Redis / Kafka / RocketMQ / MySQL / XXL-JOB Admin smoke and key integration tests.
- `publishToMavenLocal` covers primary modules.
- README, samples, configuration comments, and bilingual docs support a first adoption decision.
- license, SCM metadata, open-source metadata, GitHub Actions, secret scanning, and release checklist are ready.

## `0.2.x` Hardening Line

`0.2.x` should harden residual risks in the existing capability list instead of expanding blindly.

### `0.2.0-alpha.1`

The first batch includes only hardening items directly tied to accepted `0.1.0` residual risks:

- Cache: Redis lock fencing-token design and optional implementation while keeping the existing owner-token lock compatible.
- Messaging: Redis queue processing queue / ack model to reduce the reliability gap caused by app-level requeue after `leftPop`.
- Job: durable execution store, initially supporting one Redis or DB-backed execution-record path while keeping the in-memory store for local development.

This batch is implemented in the current development line.

This batch does not include:

- PowerJob bridge.
- ActiveMQ or other new messaging providers.
- unified governance modules.
- admin consoles, control planes, sidecars, or agents.

### `0.2.0-alpha.2`

The second batch keeps hardening existing capabilities with production observability. It does not add new providers and does not move governance modules forward prematurely.

- Core: add a provider-neutral observation publishing entry point that reuses `NexaryObservationEvent`; public APIs must not expose Micrometer, Actuator, or native middleware types.
- Cache: emit events and metrics for get/put/delete/expire, batch operations, tiered L1/L2 hit/miss, invalidation, locks, and counters.
- Messaging: emit events and metrics for publish, consume, retry, dead-letter, deduplication, provider ack/requeue/recovery paths.
- Job: emit events and metrics for trigger, execution status, duration, retry, timeout, skip reason, shard metadata, and durable store write/read paths.
- Boot: keep the optional Micrometer bridge in Spring Boot integration only, without requiring business code to know it exists.
- Docs: document production metric names, label boundaries, cardinality control, and non-goals.

This batch does not include:

- trace backends, audit-event backends, or independent governance modules.
- PowerJob bridge, ActiveMQ, or other new providers.
- admin consoles, control planes, sidecars, or agents.

### `0.2.0-alpha.3`

The third batch adds Spring Boot metrics bridging so the provider-neutral observation events from `alpha.2` can feed common metrics systems directly.

- Boot: add an independent Micrometer bridge starter that translates `NexaryObservationEvent` into Micrometer counters and timers.
- Core / capability APIs: keep Micrometer, Actuator, and concrete monitoring backends out of public capability APIs.
- Tags: allow only a fixed whitelist and continue rejecting high-cardinality fields such as cache key, message id, execution id, payload, tokens, exception text, and stack traces.
- Docs: document Spring Boot adoption, common Prometheus export paths, metric names, tag boundaries, dashboard examples, and non-goals.

This batch does not include:

- trace exporters, audit backends, or independent governance modules.
- new messaging providers, new cache providers, or PowerJob bridge.
- admin consoles, control planes, sidecars, or agents.

### `0.2.0-alpha.4`

The fourth batch starts the multi-version compatibility workstream with Spring Boot 2.7 / Java 8+ first, not Boot 4 first.

- Build: add a report-only `compatibilityAudit` gate that continuously reports Java 8 / Boot 2 blockers.
- API: audit public APIs for Java 8-incompatible `record`, Java 9+ collection factories, `Stream.toList()`, pattern matching, and switch expressions.
- Boot: evaluate independent `spring-boot2` BOM / starter naming and dependency locks without polluting the Boot 3 mainline.
- Auto-configuration: add Boot 2-compatible `spring.factories` or an equivalent compatibility entry.
- Samples: prepare independent Boot 2 / Java 8 samples where business code still depends only on Nexary APIs.
- CI: add Boot 2.7 / Java 8+ compilation, sample runtime, and provider integration gates.

The exit criterion is not a documentation claim. It is a concrete gap list, migration design, and executable gate. README gets Boot2 supported dependency snippets for each capability only after that capability's gate passes.

### `0.2.0-alpha.5`

The fifth batch validates Spring Boot 4.x / Java 21+ after the verified Boot2 entries are closed out for release.

- Build: add a Boot 4 / Java 21+ CI matrix.
- Boot: evaluate whether independent `spring-boot4` BOM / starter artifacts are needed to prevent user confusion.
- Dependencies: verify Spring Boot 4 dependency constraints, Micrometer, Spring Data Redis, Kafka, RocketMQ, and the XXL-JOB bridge.
- Samples: prepare independent Boot 4 / Java 21+ samples.
- Docs: expand README dependency snippets only after the gate passes.

Spring documentation remains the source for Boot 4's official minimum JDK. Nexary uses Java 21+ as its own primary validation target.

### `0.2.0` Release Candidate

`0.2.0` should stop adding capabilities and focus on open-source release infrastructure:

- README and getting-started pages provide Maven / Gradle dependency entry points.
- BOM, starters, APIs, providers, and the observation bridge have complete publication metadata.
- GitHub Actions cover `check`, the release gate, `publishToMavenLocal`, and Maven Central bundle creation.
- License, SCM, developer metadata, sources, Javadocs, and signing configuration are ready.
- Maven Central publishes framework modules only, not samples.

This batch does not include:

- v0.3 governance implementation.
- unverified Spring Boot 2 / JDK 8 or Spring Boot 4 / Java 21+ support claims.
- automatic Maven Central deployment; the final upload still requires the GitHub owner, Sonatype namespace, and signing keys.

It starts in parallel:

- Spring Boot 2.7 / Java 8+ compatibility gap audit and migration gate.
- Spring Boot 4.x / Java 21+ follow-up validation gate.
- independent BOM / starter / compatibility-branch design.
- README dependency matrix expansion only after real compilation, samples, and provider integration checks pass.

### Cache

- Stronger Redis lock semantics and optional fencing-token design evaluation.
- Counter extensions: TTL refresh-on-write, multi-key rate limit, or quota support as separate capabilities.
- Cache metrics: L1/L2 hit rate, miss, invalidation, lock, and counter metrics.
- Provider evaluation for Valkey, Dragonfly, Garnet, Memcached, and similar systems.
- Keep Caffeine/JVM local as an internal L1, not a public backend peer to Redis.

### Messaging

- More production-grade Kafka / RocketMQ consumer container integration.
- Redis queue processing queue / ack model / atomic retry model evaluation.
- Delayed message / scheduled message abstraction evaluation.
- Batch publish / batch consume.
- Deeper provider-specific limits and production configuration docs.
- ActiveMQ or other systems should start as provider-integration evaluations and should not outrank hardening existing providers.

### Job

- Durable execution store, with Redis or DB as possible implementations.
- Whether running cancellation should become a supported capability.
- PowerJob bridge.
- Deeper real executor-side XXL-JOB validation without claiming Nexary owns Admin platform behavior.
- More production scenarios for misfire, concurrency, and retry policies.
- Job metrics: execution count, duration, failure rate, skip reasons, and shard distribution.

### Release

- Stabilize Maven Central publication.
- Evolve samples and docs from runnable references into adoption templates.
- Stabilize GitHub issue / PR templates, contribution guide, and changelog flow.

## `0.3.x` Governance Line

`0.3.x` is the right place to let governance capabilities become independent modules.

Priority candidates:

- unified telemetry export: metrics, traces, audit events.
- governance policies such as timeout, retry, bulkhead, rate limit, degradation, and fallback.
- first service-governance module prototype.
- shared configuration model and observation tags across capabilities.
- optional providers such as PowerJob and ActiveMQ only after the existing abstractions are stable; they should not outrank the governance mainline.

Governance must keep a clear boundary: it may consume events and policy extension points from cache, messaging, and job, but it should not make their primary APIs heavy.

## `1.0.0` Stability Target

- Public APIs are stable enough for long-term maintenance.
- Maven Central publication is stable.
- Documentation, samples, issue / PR, and release workflows are stable.
- Compatibility policy is explicit, including whether Spring Boot 2 / JDK 8 needs a dedicated compatibility line.

## Community Workflow

Suggested contribution categories:

1. bug fix: direct issue or PR.
2. provider integration: issue first, then roadmap decision.
3. public API change: design discussion first, PR second.
4. governance capability: scope definition first, implementation second.

For a capability to enter the roadmap, it should answer:

- what real problem it solves.
- whether it expands the public API surface.
- whether it needs a new sample.
- whether it needs new Docker integration or integration tests.
- whether it changes the current release risk statements.
