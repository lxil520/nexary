# Changelog

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
