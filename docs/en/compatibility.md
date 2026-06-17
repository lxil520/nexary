# Compatibility

## Current Baseline

| Item | Supported |
| --- | --- |
| Java compile baseline | 17, verified |
| Primary runtime target | 21, verified |
| Spring Boot | 3.3.x, verified |
| Spring Boot 2.7 + Java 8+ | `0.2.x` target, priority audit and adaptation, requires an independent compatibility gate |
| Spring Boot 4.x + Java 21+ | later `0.2.x` validation target after the Boot2 gate; Spring documentation remains the source for the official minimum JDK |

## Compatibility Line Strategy

Nexary should reach more users like mature frameworks do, but each compatibility line must be verified independently:

- The Boot 3 mainline stays on Java 17, Spring Boot 3.3, and the current starters.
- The Boot 2 compatibility line primarily serves Java 8 users. It must first address Java 17+ syntax, Java 9+ APIs, dependency versions, and auto-configuration entry-point differences in public APIs and implementation code.
- The Boot 2 compatibility line should prefer independent starters or an independent BOM so Boot 2 dependencies do not pollute the Boot 3 mainline.
- The Boot 4 line uses Java 21+ as Nexary's primary verification target, but it starts after the Boot 2 gate passes. Spring documentation remains the source for the official minimum JDK.
- JDK 8 support needs a separate audit for `record`, pattern matching, switch expressions, `Stream.toList()`, `Map.of/List.of/Set.of`, Gradle toolchains, Caffeine 2/3 differences, Spring Data Redis/Lettuce, Kafka, RocketMQ, XXL-JOB, and related providers.
- Boot 2 auto-configuration needs `spring.factories` or an equivalent compatibility entry. The current Boot 3 mainline uses `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Before the first public release, the project should decide whether starter artifacts need explicit `spring-boot3`, `spring-boot2`, and `spring-boot4` suffixes to avoid user confusion.
- A compatibility combination can enter the README dependency matrix only after compilation, sample runtime, documentation-claim scans, and provider integration checks pass.

## `0.2.x` Compatibility Sequence

1. Run `./gradlew compatibilityAudit` and record the current Java 8 / Boot 2 blockers.
2. Decide the Java 8 compatibility strategy first: migrate the current public APIs or split a Java 8-compatible API / adapter. Java 8 support cannot be declared while public APIs still expose `record`.
3. Add independent Boot 2.7 / Java 8+ BOM, starters, samples, and CI gates.
4. After the Boot 2 gate passes, add Boot 4.x / Java 21+ BOM, starters, samples, and CI gates.
5. Add Maven / Gradle dependency snippets to README only after the corresponding gate passes.

`compatibilityAudit` prints blocker counts to the console and writes `build/reports/nexary/compatibility-audit.md`. This report is compatibility-gap evidence, not a support declaration.

`./gradlew check` also runs public documentation hygiene checks to prevent internal coordination records, task IDs, and agent instruction files from entering user-facing docs.

## Compatibility Policy

- Public APIs should change conservatively within `0.2.x`, but strict compatibility is not promised before `1.0.0`.
- Provider adapters may gain behavior and configuration options as integration coverage increases.
- Historical internal implementations are not compatibility targets.
