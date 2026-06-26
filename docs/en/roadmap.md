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
- Starter selector and SPI provider integration modes.
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
- README, samples, configuration comments, and bilingual docs support a first integration decision.
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

- Core: add a Nexary-level observation publishing entry point that reuses `NexaryObservationEvent`; public APIs must not expose Micrometer, Actuator, or native middleware types.
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

The third batch adds Spring Boot metrics bridging so the Nexary-level observation events from `alpha.2` can feed common metrics systems directly.

- Boot: add an independent Micrometer bridge starter that translates `NexaryObservationEvent` into Micrometer counters and timers.
- Core / capability APIs: keep Micrometer, Actuator, and concrete monitoring backends out of public capability APIs.
- Tags: allow only a fixed whitelist and continue rejecting high-cardinality fields such as cache key, message id, execution id, payload, tokens, exception text, and stack traces.
- Docs: document Spring Boot integration, common Prometheus export paths, metric names, tag boundaries, dashboard examples, and non-goals.

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
- Evolve samples and docs from runnable references into integration templates.
- Stabilize GitHub issue / PR templates, contribution guide, and changelog flow.

## `0.3.x` Governance and New Providers

`0.3.x` moves governance into its own module and adds three common replacement paths: Valkey as a cache deployment target, ActiveMQ Classic for messaging, and PowerJob for job triggers.

Included in `0.3.x`:

- Governance: deadline, traffic, rate limit, bulkhead, degradation, and retry-stop primitives.
- Cache: Valkey as a Redis-protocol-compatible deployment target with unchanged business APIs.
- Messaging: ActiveMQ Classic queue provider without exposing JMS types to business code.
- Job: PowerJob bridge that reuses the shared execution lifecycle.
- Samples: runnable governance, ActiveMQ Classic, and PowerJob examples.
- Docs: Chinese and English setup docs, boundaries, and local validation commands.

Governance must keep a clear boundary: it may consume events and policy extension points from cache, messaging, and job, but it should not make their primary APIs heavy.

The base `0.3.x` scope is closed. Later updates in the same minor line should continue two tracks:

- turn more governance primitives into configurable, runnable, and testable policies while keeping the primary cache / messaging / job APIs simple.
- keep validating Boot2 / Boot4 provider entries one by one; README support claims and dependency snippets are added only after samples and real middleware tests pass.

## `0.4.x` Runnable Governance Policies

`0.4.x` should not keep adding providers for its own sake. The main work is to connect the governance primitives from `0.3.x` to real execution paths so users can enable them through configuration, verify them through samples, and observe them through metrics.

Included scope:

- Governance: expose deadline, rate limit, bulkhead, degradation, and retry-stop as Spring Boot configurable policies.
- Cache: validate deadline, rate limit, degradation, and observation events at cache operation entry points without changing the `CacheClient` business API.
- Messaging: validate deadline, retry-stop, degradation, and failure events on publish / consume paths without exposing JMS, Kafka, or RocketMQ types to business code.
- Job: validate deadline, bulkhead, skip reason, and execution events on the local scheduler, XXL-JOB bridge, and PowerJob bridge trigger paths.
- Observation: document governance metric names, tag whitelist, Prometheus examples, and dashboard data sources.
- Samples: provide runnable governance examples for pass, rate-limited, degraded, and metric output paths.
- Verification: add governance policy unit tests, Spring Boot sample tests, and the repository-wide `check` gate.

`0.4.x` does not include:

- control planes, admin consoles, sidecars, or agents.
- automatic policy distribution or remote dynamic configuration.
- wrapping external scheduler, messaging, or cache consoles as Nexary-owned capabilities.

Later updates in the same minor line should only close two kinds of gaps:

- Expand Boot2 / Boot4 governance claims after real samples prove them.
- Add governance samples for more real middleware combinations, but do not put them in the README support matrix before tests pass.

## `0.5.x` Integration Experience and Ecosystem Stability

`0.5.x` should make it easier for users to decide how to integrate Nexary, how to verify the integration, and how to debug failures. It should also stabilize release, documentation, samples, and provider validation workflows.

Included in `0.5.1`:

- Release: document Maven Central namespace, signing, sources, Javadocs, tag publication, and failure handling.
- Docs: align README, capability docs, and sample docs on `0.5.1`, with dependency snippets, configuration, run commands, and limits for each integration path.
- Compatibility: keep Boot2 / Boot4 support claims scoped per capability instead of presenting unverified combinations as blanket support.
- Samples: fix sample ports and run commands so documented curl commands match the actual services.
- Operations: add a troubleshooting page for versions, dependencies, ports, middleware, providers, Job cron, metrics, and pre-release checks.

Later updates in the same minor line should continue:

- Cache: after Redis / Valkey are stable, evaluate Dragonfly, Garnet, and Memcached; README claims are added only after samples and real middleware tests pass.
- Messaging: harden production configuration docs for Kafka, RocketMQ, Redis queue, and ActiveMQ Classic; RabbitMQ or other new providers should start with an issue discussion.
- Job: continue validating real XXL-JOB / PowerJob platform triggers, worker / executor lifecycle boundaries, and failure callback boundaries. Docs must distinguish "bridge validation passed" from "the external platform owns the full lifecycle."
- Samples: continue turning samples into templates that can be copied into application projects.

`0.5.x` does not include:

- private deployment product claims.
- private deployment platforms, tenant management, user permissions, billing, or ticketing.
- support claims for providers that have not passed real middleware validation.

## `0.6.x` Local Governance Flow

`0.6.x` tightens the verifiable boundary of local governance. The goal is not a console or remote policy platform. The goal is to show one in-process Java call moving through normal execution, failure records, slow-call records, open circuit, fallback, half-open probing, recovery, and reopening.

Included in `0.6.x`:

- Governance: add circuit state, rejection reason, and local snapshot types with bounded fields for `CLOSED`, `OPEN`, and `HALF_OPEN`.
- Boot: bind `circuit-breaker` settings to local `GovernancePolicy`, covering failure rate, slow-call rate, half-open probes, and open duration.
- Cache: Redis client calls go through the local governance runtime, so failures and slow calls can open the same circuit.
- Messaging: consumer handlers go through `GovernanceExecution`; slow or failed consumption can open the local circuit. The publish path still uses the existing send events.
- Job: local, XXL-JOB, and PowerJob execution entries go through `GovernanceExecution`; slow or failed jobs can open the local circuit.
- Samples: `nexary-sample-governance` adds `LocalCircuitBreakerProfileGateway`, with curl paths for opening on failures, opening on slow calls, fallback while open, successful half-open recovery, and failed half-open reopening.
- Docs: governance and sample docs include dependencies, configuration, run commands, curl steps, expected fields, and boundaries.
- Tests: sample tests cover open circuit, fallback after rejection, half-open recovery, failed half-open reopening, and slow-call opening.

Later `0.6.x` work should close only two gaps:

- Put the messaging publish path behind the same governance runtime instead of relying only on existing send events and deadline headers.
- Add command-level real-middleware demos so Redis, Kafka/RocketMQ/ActiveMQ Classic, XXL-JOB, and PowerJob circuit behavior can be reproduced directly.

`0.6.x` does not include:

- consoles, sidecars, agents, remote dynamic config, or policy push.
- cross-service platform behavior, cross-process state sharing, or cross-instance circuit windows.
- invasive changes to the primary cache / messaging / job APIs.
- runtime-backed circuit breaking for messaging publish.
- README support claims that have not passed sample and test verification.

## `0.7.x` Messaging Publish Governance And Command-Level Samples

`0.7.x` explains the local governance boundary more clearly and makes the messaging sample inspectable with real commands. Users should be able to see publish results, consumed messages, and provider differences without reading Java code first. This remains Java SDK-level governance, not a console, sidecar, agent, or remote configuration platform.

Included scope:

- Messaging: document the publish governance resource as `kind=messaging`, `name=message-publish`, `operation=publish`, with provider set to `disruptor`, `redis`, `kafka`, `rocketmq`, or `activemq_classic`.
- Messaging: document that provider publishers transport `nexary-deadline-epoch-millis`; expired publish calls return `MessagePublishResult.failed("message publish deadline exceeded", RetrySignal.stop("deadline_exceeded"))`.
- Messaging: document that `GovernedMessagePublisher` protects publish calls in the current JVM only; it does not provide cross-instance windows, broker-level circuit breaking, or automatic provider switching.
- Samples: expand `nexary-sample-messaging` docs for `POST /app-error-logs` and `GET /app-error-logs`, including `result.status`, `published[].publishStatus`, `published[].providerMessageId`, `published[].detail`, and `consumed[]`.
- Samples: add startup and curl commands for Disruptor, Redis, Kafka, RocketMQ, and ActiveMQ Classic; real brokers still come from `./scripts/middleware/up.sh` or a user-provided local broker.
- Governance docs: add copyable messaging publish policy YAML and state that it applies only when publish goes through `GovernanceExecution`.

`0.7.x` does not include:

- consoles, sidecars, agents, remote dynamic config, or policy push.
- cross-process circuit windows, global rate limits, or cross-instance state sharing.
- broker high availability, fallback chains, production topic creation, or production queue creation.
- exactly-once, global ordering, or cross-provider transaction guarantees.
- README support claims that have not passed sample and real-middleware verification.

## `0.8.x` Governance Data Plane And Policy Snapshots

`0.8.x` turns local governance runtime state into stable read-only data. Users can inspect governance resources, policy snapshots, runtime snapshots, and recent events inside one JVM. The data is meant for diagnosing the current service and for future read-only Console fields. It is still not a console, sidecar, agent, or remote configuration platform.

Included scope:

- Runtime: add `GovernanceDiagnostics`, `GovernanceResourceDescriptor`, `GovernancePolicySnapshot`, `GovernanceRuntimeEvent`, and `GovernanceRuntimeSummary`.
- Runtime: keep the existing `GovernanceRuntime` execute behavior and add read-only `resources()`, `snapshots()`, `recentEvents()`, and `summary()` methods.
- Runtime: keep recent events in a bounded ring buffer and return them oldest-to-newest.
- Diagnostics: event fields are limited to `resourceKey`, `action`, `outcome`, `rejectionReason`, `circuitState`, `timestamp`, and `durationBucket`.
- Boot: `nexary-governance-spring-boot-starter` exposes `GET /nexary/governance/summary`, `/resources`, `/resources/{resourceKey}`, and `/events` only after `nexary.governance.diagnostics.enabled=true`.
- Samples: `nexary-sample-governance` adds diagnostics config and curl commands for success, failure, rate limit, bulkhead rejection, open circuit, half-open probe, and recovery.

`0.8.x` does not include:

- policy writes, config push, remote control, login, permissions, audit backends, or UI pages.
- userId, tenant, bizKey, messageId, cache key, payload, full exception messages, or stack traces.
- cross-instance windows, centralized state storage, or automatic provider switching.
- Boot2 / Boot4 / provider support claims that have not passed real samples and middleware tests.

## `0.9.x` Read-Only Governance Diagnostic Console

`0.9.x` adds a page on top of the v0.8 local governance data plane. After starting a Spring Boot application, users open `/nexary/console` to inspect summary, resources, resource detail, events, and read-only settings for the current JVM. The page is read-only and is meant for local debugging.

Included scope:

- Console API: `GET /nexary/console/api/summary`, `/resources`, `/resources/{id}`, and `/events`.
- Console pages: Overview, Resources, Resource Detail, Events, and Settings Readonly.
- Starter: `nexary-console-spring-boot-starter` registers the page and API only after `nexary.console.enabled=true`.
- Packaging: `nexary-console-server` packages the Vue build output under `static/nexary/console` in the jar.
- Sample: `nexary-sample-governance` includes the Console starter, so `/nexary/console` works after the sample starts.

`0.9.x` does not include:

- policy writes, config push, remote control, login, permissions, audit backends, or multi-instance aggregation.
- userId, tenant, bizKey, messageId, cache key, payload, full exception messages, or stack traces.
- separately deployed frontend services, sidecars, agents, or cross-service management pages.

## `0.10.x` Pre-1.0 Console And Local Diagnostics Hardening

`0.10.x` is a hardening line before 1.0, not a new governance platform promise. It keeps serving local debugging inside one Spring Boot application and makes the v0.9 read-only Console easier to open and harder to regress during packaging and release.

Included scope:

- Console direct URLs: `/nexary/console`, `/nexary/console/`, `/nexary/console/resources`, `/nexary/console/resources/{resourceKey}`, `/nexary/console/events`, and `/nexary/console/settings` should all return a renderable page.
- Static assets: after packaging into the `nexary-console-server` jar, entry HTML, JS, and CSS paths must stay stable; missing assets or blank-page regressions should be caught by tests or the release gate.
- Local sample visual verification: after starting `nexary-sample-governance`, use curl to trigger success, failure, rate limit, bulkhead rejection, open circuit, and half-open recovery, then verify Overview, Resources, Resource Detail, Events, and Settings Readonly are non-empty and navigable in a browser.
- Release gate: before publishing `0.10.1`, continue running release preflight, Gradle check, Console UI build, static-resource packaging checks, Docker sample smoke, and public documentation scans.

`0.10.x` does not include:

- Policy writes, policy rollback, remote configuration, or dynamic push.
- Multi-instance aggregation, cross-process state sync, or centralized state storage.
- Login, permissions, RBAC, user management, or audit backends.
- Sidecars, agents, separately deployed consoles, or cross-service management pages.
- Automatic blocking, external platform management, or incident response workflows.

Acceptance targets:

- Console direct URLs and deep links open inside the Spring Boot jar without visiting the overview first.
- Missing static assets cannot silently become a blank page.
- The local governance sample supports visual verification across empty data, normal data, and open circuit data.
- The release gate is stable and points failures to Gradle, UI build, static-resource packaging, documentation scanning, or release input problems.

## `0.11.x` Request Cancellation for Stale Work

`0.11.x` does not replace Sentinel and does not add a remote governance platform. It only solves stale request cancellation: when a request has expired, upstream has canceled, or the client has disconnected, local governance should stop it so threads, connections, and downstream quotas are not spent on stale work.

Included scope:

- `0.11.0`: Spring Boot 3.3 mainline adds the cancellation model, downstream receiver, Gateway cancellation starter, downstream sample, Gateway sample, read-only Console cancellation fields, and smoke script.
- Runtime: when a deadline has expired, upstream has canceled, or the token has been canceled, reject before the primary action starts or run an explicitly provided fallback.
- Runtime: after business work has started, support cooperative stop checks through `CancellationContext` and record `CANCEL/CANCELLED`, `cancelledCount`, and low-cardinality cancellation reasons.
- Gateway: propagate deadline and cancellation id; when the client disconnects, notify the downstream receiver so the token in that JVM is canceled.
- `0.11.1`: add the Spring Boot 2.7 Gateway starter, sample, and gates, then update the README support matrix.
- `0.11.2`: add the Spring Boot 4.x Gateway starter, sample, and gates; update the README support matrix only after they pass.

`0.11.x` does not include:

- Sentinel rule adapters, Sentinel dashboard, cluster flow control, or a Sentinel provider.
- Forced interruption after ordinary Java business code has already started; running work relies on cooperative checks.
- Remote policy push, multi-instance aggregation, sidecars, agents, or separately deployed consoles.
- Retry stop propagation across repeated messaging, job, or other retry chains.

Acceptance targets:

- Requests with already-expired deadlines do not start the primary action.
- Gateway disconnect notification lets the downstream sample stop slow work quickly.
- Cancellation, rejection, and fallback results are visible in local diagnostics, the Console, and observation events.
- Docs and scripts only claim Gateway versions that have passed real samples and gates.

## `0.12.x` Sentinel Provider

`0.12.x` connects Nexary local governance resources to the Sentinel execution plane. It still does not replace Sentinel: Sentinel Dashboard, cluster flow control, remote rule platforms, and Sentinel runtime behavior stay owned by the Sentinel ecosystem. Nexary provides the Java integration boundary, low-cardinality diagnostics, samples, and the read-only Console.

Included scope:

- `0.12.0`: Spring Boot 3.3 / Java 17+ mainline adds `nexary-governance-sentinel` and `nexary-governance-sentinel-spring-boot-starter`.
- Runtime: after `nexary.governance.provider=sentinel`, the Sentinel resource name uses `GovernanceResource.key()`; `provider`, `operation`, and `priority` stay in Nexary diagnostics fields.
- Policy mapping: `max-requests-per-window` maps to a QPS flow rule, `max-concurrency` maps to a thread-count flow rule, and failure-rate / consecutive-failure / slow-call settings map to Sentinel degrade rules.
- Runtime: v0.11 cancellation is still checked before Sentinel entry, so canceled requests do not enter Sentinel statistic windows.
- Diagnostics / Console: add `engine`, `blockReason`, `lastBlockReason`, `blockedCount`, and `sentinelResourceCount`.
- Samples: add `nexary-sample-governance-sentinel` and `scripts/governance-sentinel/smoke.sh` for rate limiting, bulkhead, slow-call circuit breaking, exception circuit breaking, fallback, diagnostics, and Console checks.

Later patch versions:

- `0.12.1`: Boot2 Sentinel compatibility line; update README only after Java 8 / Boot2 samples and gates pass.
- `0.12.2`: Boot4 Sentinel compatibility line; update README only after Java 21 / Boot4 samples and gates pass.

`0.12.x` does not include:

- a Sentinel Dashboard replacement.
- cluster flow control, a remote rule center, or rule-editing pages.
- Boot2 / Boot4 Sentinel support claims before samples and gates pass.

## `0.13.x` Retry Stop Propagation

`0.13.0` implements retry stop propagation on the Boot3 mainline. Governance rejection, expired deadlines, request cancellation, and execution timeout map to fixed `RetryStopReason` values and continue into messaging consume and job execution retry loops so useless work is not amplified by later retries.

Included scope:

- Core: add `RetryStopReason` and `RetryStopClassifier`.
- Runtime: local and Sentinel runtime events, snapshots, and summaries expose `retryStopReason`, `lastRetryStopReason`, and `retryStoppedCount`.
- Messaging: consume paths stop the current retry loop after governance rejection, expired deadlines, cancellation, or timeout.
- Job: execution paths stop the current retry loop after governance rejection or timeout.
- Console: Overview, Events, and Resource detail show retry-stop counts and reasons.
- Samples: `nexary-sample-governance-sentinel` adds `/governance/sentinel/retry-stop`.

`0.13.x` does not include:

- remote rule push.
- a Sentinel Dashboard replacement.
- Boot2 / Boot4 Sentinel provider claims before samples and gates pass.

## `0.14.x` Traffic Isolation and Priority Governance

`0.14.0` implements fixed traffic classes and priority isolation on the Boot3 mainline. When online requests share a resource with offline tasks, batch work, or background repair work, lower-priority traffic can be rate-limited, bulkhead-isolated, or sent to fallback first so online requests keep capacity.

Included scope:

- Core: fixed `GovernanceTrafficClass` values are `ONLINE`, `OFFLINE`, `BATCH`, and `BACKGROUND`; fixed `GovernancePriority` values are `HIGH`, `NORMAL`, and `LOW`.
- Context: `GovernanceContext` can bind traffic class and priority, then restore the previous context after nested calls.
- Runtime: the local runtime keeps separate windows by resource, traffic class, and priority; priority policy wins, then resource policy, then default policy.
- Sentinel: the Boot3 Sentinel provider keeps the Sentinel resource name stable while applying Nexary-side priority-aware windows first, so lower-priority rules do not block high-priority online requests.
- Diagnostics / Console: add `trafficClass`, `priority`, `isolationReason`, `trafficClassCounts`, `priorityCounts`, and `isolatedCount`.
- Samples: `nexary-sample-governance-sentinel` adds `/priority/online`, `/priority/batch`, and `scripts/governance-priority/smoke.sh`.

`0.14.x` does not include:

- a Sentinel Dashboard replacement.
- remote rule platforms, cross-instance aggregation, or rule-editing pages.
- using this release to fill Boot2 / Boot4 Sentinel provider support matrix gaps.

## `0.15.x` Abnormal Instance Detection and Local Quarantine Model

`0.15.0` detects abnormal instance candidates inside the current JVM. When several instances sit behind the same downstream resource and one instance has a much higher 5xx, timeout, reset, or slow-call ratio than the resource baseline, Nexary marks it as `SUSPECT` or `QUARANTINE_CANDIDATE` and exposes a fixed reason plus a suggested action.

Included scope:

- Runtime: keep sliding windows by `resourceKey + instanceKey`, recording only signal type, outcome, status-code class, and duration bucket.
- API: add `GovernanceInstanceRef`, `InstanceHealthSignal`, `InstanceHealthState`, `InstanceQuarantineReason`, `InstanceRecoveryAdvice`, and `GovernanceInstanceHealth`.
- Safety: instance keys are masked by default, and diagnostics do not expose raw hosts, ports, URLs, queries, user ids, tenants, payloads, exception text, or stack traces.
- Diagnostics / Console: add suspect instance count, quarantine candidate count, recovery probe count, per-resource instance tables, and recent instance events.
- Samples: `nexary-sample-governance` adds the `instance-health` profile and `scripts/governance-instance-health/smoke.sh`.

`0.15.x` does not include:

- automatic traffic drain.
- registry, Spring Cloud LoadBalancer, Gateway route, cloud vendor, or PaaS instance-removal adapters.
- cross-instance aggregation or a remote quarantine platform.
- using this release to fill Boot2 / Boot4 Sentinel provider support matrix gaps.

## `0.16.x` Trace and Fault Location

The next step connects governance events, request cancellation, retry-stop, priority isolation, and abnormal instance candidates into a trace-oriented view. Users should be able to see why a call stopped, which governance rule blocked it, whether an abnormal instance candidate was involved, and which resource should be checked first.

Planned scope:

- Add low-cardinality trace / span summaries to governance events.
- Show request, downstream call, governance event, and instance health event relationships in samples.
- Document how to inspect problems together with Micrometer Observation / OpenTelemetry.

This does not add a distributed tracing backend, a log collection platform, or request-body storage.

## `0.17+` Capacity, Fault Injection, and Automatic Stop-Loss Evaluation

Later releases can evaluate capacity protection, local fault injection, and automatic stop-loss. The prerequisite is that v0.15 abnormal instance candidates and v0.16 trace location are stable enough for users to understand why a suggested action appears.

Possible directions:

- Capacity watermarks and lower-priority traffic backoff.
- Local fault-injection samples to validate fallback, retry-stop, and abnormal instance detection.
- Design discussion for automatic traffic-drain adapters, while keeping the default behavior read-only and local.

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
