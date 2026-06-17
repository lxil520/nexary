# Coding Standards and Maintenance Rules

This document is the maintainer-facing standards guide for Nexary.

## Baseline

- Java compile baseline: 17
- primary runtime verification target: Java 21
- Spring Boot mainline: 3.3
- package prefix: `org.nexary.*`
- configuration prefix: `nexary.*`

## API Rules

- Keep public APIs small and provider-neutral.
- Use `Duration` or `Instant` for public time values.
- Use `enum` for states.
- Use `@ConfigurationProperties` for configuration.
- Public APIs require Javadoc.
- API modules must not depend back on implementation modules.

## Module Rules

- `nexary-framework` holds shared semantics and SPI only.
- API modules hold shared abstractions only.
- Provider modules adapt middleware without redefining public semantics.
- Starters aggregate dependencies and auto-configuration only.
- Samples are adoption references, not a platform console.

## Documentation Rules

- Chinese documentation is the primary narrative.
- English documentation mirrors user-facing changes.
- User-facing docs should not expose internal dispatch logs or work records.

## Sample Rules

- Samples stay as Spring Boot projects.
- Each capability's focused starter / SPI samples are the user entry points.
- Capability samples must make copyable structure clear.
- Capability samples must first show how business code uses Nexary abstractions, then show how the capability is introduced. Users should not have to read provider wiring before understanding the business usage.
- Samples must express Nexary's core value: business code does not care whether the underlying implementation is Redis, Kafka, RocketMQ, XXL-JOB, or another provider. Switching providers must not require changes to business controllers, facades, job handlers, or consumers.
- Each capability must provide two adoption paths:
  - starter selector mode: depend on the capability starter, let the starter bring optional providers, and select the provider or mode through `nexary.*` configuration.
  - SPI provider mode: business code depends on the API, while provider modules are introduced separately through `ServiceLoader` / Spring bridge.
- Starter selector samples must not declare provider-specific `@Configuration`, producer factories, consumer factories, Redis clients, Kafka/RocketMQ templates, XXL-JOB executors, or similar framework wiring beans in sample code. Nexary starters and provider modules own that wiring.
- SPI provider samples may show provider dependencies and SPI discovery, but business code must still depend only on Nexary APIs and must not call native middleware SDKs directly.
- `build.gradle` files must include comments explaining the adoption mode:
  - starter selector samples explain what the starter aggregates and how provider selection is configured.
  - SPI provider samples explain what the API dependency and provider dependency each do.
- `application*.yml` files must comment key `nexary.*` options, default meanings, when to change them, and which values are local integration settings.
- Capability samples must separate provider-neutral entry points from provider or mode-specific wiring. Do not place multiple provider configurations and neutral application code in one flat package.
- Recommended package structure:
  - `app`: application bootstrap and startup boundary.
  - `api` or `web`: HTTP, CLI, or test trigger entry points.
  - `application` or `facade`: copyable user-facing use case entry points.
  - `common` or `support`: provider-neutral sample DTOs, state repositories, and diagnostics.
- Provider or mode packages may exist only in SPI samples or provider-specific validation samples to demonstrate SPI adoption or provider boundaries. Starter selector samples should not contain provider wiring packages.
- Each provider or mode must have its own profile, run command, and acceptance notes. Users should be able to switch provider or mode without modifying business code.
- Do not add catch-all configuration classes or pack all provider options into one controller, facade, or configuration file.
- Do not import Kafka, RocketMQ, Redis, XXL-JOB, or Disruptor native types from business sample code. Native types are allowed only in provider modules, starter auto-configuration, isolated SPI provider samples, or test verification code.
- Sample validation must check structure before functional pass. A structurally flat sample cannot be accepted as release-ready.

## Validation Rules

- baseline gate: `./gradlew check`
- real middleware validation is centralized through `scripts/middleware/*`
- each capability keeps its own acceptance surface

## Prohibited

- no legacy project names, company names, personal names, internal endpoints, or real secrets
- do not bloat the public API for short-term compatibility
- do not mix sidecars, agents, control-plane work, or admin consoles into `0.1.x`
