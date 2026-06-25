# Changelog

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
