# Compatibility

## Current Baseline

| Item | Supported |
| --- | --- |
| Java compile baseline | 17, verified |
| Primary runtime target | 21, verified |
| Spring Boot | 3.3.x, verified |
| Spring Boot 2.7 + Java 8+ | planned support, requires an independent compatibility gate |
| Spring Boot 4 | not a v0.2 target, evaluate later against the official stable line; Nexary plans Java 21+ as the primary verification target, while Spring documentation remains the source for the official minimum JDK |

## Compatibility Line Strategy

Nexary should reach more users like mature frameworks do, but each compatibility line must be verified independently:

- The Boot 3 mainline stays on Java 17, Spring Boot 3.3, and the current starters.
- The Boot 2 compatibility line is primarily for Java 8 users. It should prefer independent starters or an independent BOM so Boot 2 dependencies do not pollute the Boot 3 mainline.
- JDK 8 support needs a separate audit for Java syntax, dependency versions, Gradle toolchains, Caffeine 2/3 differences, Spring Data Redis/Lettuce, Kafka, RocketMQ, XXL-JOB, and related providers.
- Before the first public release, the project should decide whether starter artifacts need explicit `spring-boot3` and `spring-boot2` suffixes to avoid user confusion.
- A compatibility combination can enter the README dependency matrix only after compilation, sample runtime, documentation-claim scans, and provider integration checks pass.

## Compatibility Policy

- Public APIs should change conservatively within `0.2.x`, but strict compatibility is not promised before `1.0.0`.
- Provider adapters may gain behavior and configuration options as integration coverage increases.
- Historical internal implementations are not compatibility targets.
