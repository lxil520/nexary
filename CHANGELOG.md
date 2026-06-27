# Changelog

## 0.17.0

- Added the read-only governance platform foundation with asset, dependency, connector, signal, topology, service, and incident candidate models.
- Added `nexary-governance-platform-api`, `nexary-governance-platform-server`, and `nexary-governance-platform-storage-postgres`.
- Added platform ingestion and query endpoints for `/api/platform/resources`, `/api/platform/signals`, `/api/platform/topology`, `/api/platform/services`, `/api/platform/incidents`, `/api/platform/connectors`, and `/api/platform/signals`.
- Added Console Platform Mode so users can switch from single-JVM local diagnostics to an aggregated services, dependencies, incidents, and connector-status view.
- Added `nexary-sample-governance-platform` with an abstract cloud-phone topology demo and platform API data.
- Rejected high-cardinality or sensitive platform signal attributes such as user ids, tenants, payloads, cache keys, message ids, exception text, stack traces, tokens, and passwords.
- Kept policy writes, Sentinel / Gateway / APM rule changes, production IM alerts, automatic traffic drain, automatic scaling, capacity, and chaos work out of this release.

## 0.16.0

- Added local fault traces that connect recent governance events, retry stops, cancellation, priority isolation, Sentinel blocks, and instance-health state inside the current JVM.
- Added bounded trace models: fault trace, trace step, trace stage, trace stop reason, trace recorder, and fault trace summary.
- Added read-only diagnostics endpoints for `/nexary/governance/traces`, `/nexary/governance/traces/{traceKey}`, and `/nexary/governance/faults/summary`.
- Extended runtime diagnostics and the read-only Console with fault trace counts, stopped trace counts, trace stop reasons, trace stage filters, resource last-trace state, and trace detail pages.
- Extended `nexary-sample-governance` with a `trace` profile and added `scripts/governance-trace/smoke.sh`.
- Kept traces local and low-cardinality; this release does not add a Jaeger, Zipkin, SkyWalking, OpenTelemetry exporter, external trace id exposure, or cross-instance trace storage.

## 0.15.0

- Added local instance health detection for downstream resources with several instances behind the same resource key.
- Added fixed low-cardinality instance health models: instance references, health signals, health state, quarantine reason, and recovery advice.
- Added local sliding-window detection for slow-call ratio, timeout spikes, reset spikes, server-error ratio, and status-code skew.
- Added read-only diagnostics endpoints for `/nexary/governance/instance-health` and `/nexary/governance/instance-health/{resourceKey}`.
- Extended runtime diagnostics and the read-only Console with suspect instance counts, quarantine candidate counts, recovery probe counts, and per-resource instance health snapshots.
- Extended `nexary-sample-governance` with an `instance-health` profile and added `scripts/governance-instance-health/smoke.sh`.
- Kept quarantine as local diagnostic state and suggested action only; this release does not call registries, Gateway route APIs, cloud APIs, or Sentinel rule updates.

## 0.14.0

- Added fixed low-cardinality traffic classes and governance priority buckets for local governance and the Boot3 Sentinel provider.
- Added priority-specific policy selection so low-priority batch, offline, or background work can be rate-limited, isolated, or sent to fallback before online traffic is affected.
- Added mixed-traffic warning events when one resource is used by more than one fixed traffic class.
- Extended diagnostics and the read-only Console with traffic class counts, priority counts, isolated count, per-event isolation reasons, and resource isolation state.
- Extended the Sentinel governance sample with priority isolation endpoints and added `scripts/governance-priority/smoke.sh`.
- Kept Boot2 and Boot4 Sentinel provider compatibility out of this release line; those entries still require their own samples and gates.

## 0.13.0

- Added bounded retry stop reasons and a retry-stop classifier for governance rejection, expired deadlines, cancellation, client disconnects, upstream cancellation, shutdown, rate limiting, bulkhead rejection, open circuit, degradation, retry exhaustion, and timeouts.
- Extended local and Sentinel governance diagnostics with `retryStopReason`, `lastRetryStopReason`, and `retryStoppedCount`.
- Updated messaging and job retry loops so governance stop signals end retry propagation instead of amplifying already rejected or expired work.
- Updated the read-only Console and HTTP diagnostics to show retry-stop counts and reasons without exposing cancellation ids, payloads, message ids, cache keys, exception text, or stack traces.
- Extended the Sentinel sample and smoke script with a retry-stop path.
- Kept Boot2 and Boot4 Sentinel provider entries out of the public matrix until their own samples and gates pass.

## 0.12.0

- Added the optional Sentinel governance provider on the Spring Boot 3.3 / Java 17+ mainline.
- Added `nexary-governance-sentinel` and `nexary-governance-sentinel-spring-boot-starter`, using Sentinel `1.8.10`.
- Added `nexary.governance.provider=sentinel` so business code can keep calling Nexary APIs while Sentinel executes QPS flow control, thread-count flow control, slow-call circuit breaking, and exception circuit breaking.
- Added low-cardinality diagnostics and Console fields for governance engine, Sentinel block reason, blocked count, and Sentinel resource count.
- Added `nexary-sample-governance-sentinel` and `scripts/governance-sentinel/smoke.sh` for local Sentinel provider validation.
- Kept Boot2 and Boot4 Sentinel support out of the public matrix until their independent samples and gates pass.

## 0.11.1

- Added the Spring Boot 2.7 Gateway cancellation starter.
- Added the Spring Boot 2.7 Gateway sample and gate for request cancellation propagation.
- Updated the public support matrix after the Boot2 Gateway starter and sample tests passed.

## 0.11.0

- Added the request cancellation model: cancellation tokens, scoped cancellation context, low-cardinality cancellation reasons, and deadline / timeout / cancellation headers.
- Extended the local governance runtime so canceled work can reject before the primary action starts, use fallback when provided, or stop cooperatively through `CancellationContext`.
- Added diagnostics and Console fields for cancellation outcomes, cancelled counts, and low-cardinality cancellation reasons without exposing cancellation ids.
- Added the Spring Boot 3.3 Gateway cancellation starter, downstream cancellation receiver, downstream governance sample, Gateway sample, and cancellation smoke script.
- Clarified that Nexary does not replace Sentinel; the Sentinel provider remains planned for v0.12 and retry stop propagation remains planned for v0.13.

## 0.10.1

- Added a Docker Compose demo for the governance sample so the read-only Console can be opened through one local script.
- Added console smoke commands that trigger sample governance events and verify diagnostics JSON plus packaged Console HTML.
- Kept the Console as a local read-only page inside the sample process; this patch does not add remote policy writes, cross-instance aggregation, or a separate control service.

## 0.5.1

- Fixed the Maven Central release workflow so a deployment that is already visible in Maven Central is treated as complete even if the Central Portal status API keeps reporting `PUBLISHING`.
- Kept the bundle build, signing, and release documentation aligned with the patch release version.

## 0.5.0

- Aligned README, capability docs, module READMEs, and sample READMEs on the same release version.
- Added troubleshooting guidance for local middleware, provider selection, sample ports, release checks, and metrics.
- Added the public v0.5 release runbook for local validation, Central Portal bundle checks, GitHub Actions tag publication, Maven Central sync checks, and failure handling.
- Tightened the release workflow so manual runs default to bundle-only checks, Central publication must run from a matching tag, and missing Central credentials fail the publish step.

## 0.1.0-SNAPSHOT

- Rewrote the legacy project into the Nexary module structure.
- Added core governance primitives for deadline, traffic tags, retry, observation, and fault signals.
- Added cache, messaging, and job APIs with Spring Boot adapter modules.
