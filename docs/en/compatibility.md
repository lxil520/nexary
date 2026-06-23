# Compatibility

## Current Baseline

| Item | Supported |
| --- | --- |
| Java compile baseline | 17, verified |
| Primary runtime target | 21, verified |
| Spring Boot | 3.3.x, verified |
| Spring Boot 2.7 + Java 8+ | Cache Redis single-tier is verified; Messaging Redis-only provider / starter is verified; Job provider / starter is verified with a bounded scope; Messaging Disruptor/Kafka/RocketMQ still require independent compatibility gates |
| Spring Boot 4.1 + Java 21 | Cache Redis provider / starter is verified; Messaging is verified per provider and the starter provides Nexary-level core only; Job provider / starter is verified with a bounded scope; this is not whole-repository Boot4 support; Spring documentation remains the source for the official minimum JDK |

## Compatibility Line Strategy

Nexary should reach more users like mature frameworks do, but each compatibility line must be verified independently:

- The Boot 3 mainline stays on Java 17, Spring Boot 3.3, and the current starters.
- The Boot 2 compatibility line primarily serves Java 8 users. It handles Java 17+ syntax, Java 9+ APIs, dependency versions, and auto-configuration entry-point differences by capability: Cache, Messaging, and Job.
- The Boot 2 compatibility line should use independent starters / providers and BOM constraints so Boot 2 dependencies do not pollute the Boot 3 mainline.
- The Boot 4 line uses Java 21 as Nexary's primary validation runtime and is verified independently by capability: Cache, Messaging, and Job. Spring documentation remains the source for the official minimum JDK.
- JDK 8 support needs a separate audit for `record`, pattern matching, switch expressions, `Stream.toList()`, `Map.of/List.of/Set.of`, Gradle toolchains, Caffeine 2/3 differences, Spring Data Redis/Lettuce, Kafka, RocketMQ, XXL-JOB, and related providers.
- Boot 2 auto-configuration needs `spring.factories` or an equivalent compatibility entry. The current Boot 3 mainline uses `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Before the first public release, the project should decide whether starter artifacts need explicit `spring-boot3`, `spring-boot2`, and `spring-boot4` suffixes to avoid user confusion.
- A compatibility combination can enter the README dependency matrix only after compilation, sample runtime, documentation-claim scans, and provider integration checks pass.

## `0.2.x` Compatibility Sequence

1. Run `./gradlew compatibilityAudit` and record the current Java 8 / Boot 2 blockers.
2. Decide the Java 8 compatibility strategy by capability: migrate the current public APIs or split a Java 8-compatible API / adapter. A capability cannot declare Java 8 support while its public APIs still expose `record`.
3. Cache Boot 2.7 / Java 8+ Redis single-tier starter / provider gates have passed, the Messaging Boot 2.7 / Java 8+ Redis-only provider / starter gate has passed, and the Job Boot 2.7 / Java 8+ provider / starter bounded-scope gate has passed.
4. Messaging Disruptor/Kafka/RocketMQ Boot2/JDK8 providers still require independent gates. Boot4/JDK21 has verified Cache Redis, Messaging per provider, and bounded Job entries, but a dedicated Boot4 BOM, samples, and release closeout still need to progress.
5. Add Maven / Gradle dependency snippets to README only after the corresponding gate passes. Boot4 Messaging must be documented as starter plus exactly one provider artifact, not as an aggregate-all-provider starter.

`compatibilityAudit` prints blocker counts to the console and writes `build/reports/nexary/compatibility-audit.md`. This report is compatibility-gap evidence, not a support declaration.

`./gradlew check` also runs public documentation hygiene checks to prevent non-public project metadata from entering user-facing docs.

## Compatibility Policy

- Public APIs should change conservatively within `0.2.x`, but strict compatibility is not promised before `1.0.0`.
- Provider adapters may gain behavior and configuration options as integration coverage increases.
- Compatibility is defined by documented public APIs and verified release entries, not by implementation details.
