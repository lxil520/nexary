# Nexary

<p align="center">
  <img src="docs/assets/nexary-logo.svg" alt="Nexary" width="420">
</p>

Chinese documentation: [README.md](README.md)

Nexary is a Java 17 middleware framework for Spring Boot 3.3 applications. The current `0.2.x` line focuses on cache, messaging, job scheduling, SPI, observation bridging, and governance extension points for future resilience features.

Nexary is a new public-facing framework. It does not promise source or binary compatibility with any previous internal or historical implementation.

## Status

Nexary is pre-`1.0.0`. Public APIs are intentionally small and provider-neutral, and implementation modules are expected to evolve before `1.0.0`.

The currently verified mainline is:

- Java 17 for compilation, Java 21 as the primary runtime verification target
- Spring Boot 3.3 as the mainline platform

This boundary comes from Spring Boot 3's own Java 17+ requirement, not from Nexary's initial development JDK.

To reach more users, Nexary will include Spring Boot 2.7 / Java 8+ and Spring Boot 4.x / Java 21+ in the `0.2.x` compatibility target. Boot 2 is the important path for Java 8 users and takes priority over Boot 4; Boot 4 validation should start after the Boot 2 compatibility gate passes. Support cannot be declared by README alone: it needs independent starters, pinned dependency versions, samples, and CI evidence before it is marked as supported. The dependency snippets below describe only the currently verified mainline.

## Documentation

- Language switch: [docs/README.md](docs/README.md)
- English docs: [docs/en/index.md](docs/en/index.md)
- Chinese docs: [docs/zh/index.md](docs/zh/index.md)

Read by capability:

- Cache: [docs/en/cache.md](docs/en/cache.md)
- Messaging: [docs/en/messaging.md](docs/en/messaging.md)
- Job: [docs/en/job.md](docs/en/job.md)
- Local validation: [docs/en/verification.md](docs/en/verification.md)

General references:

- Architecture: [docs/en/architecture.md](docs/en/architecture.md)
- Contribution and maintenance: [docs/en/collaboration.md](docs/en/collaboration.md)
- Roadmap: [docs/en/roadmap.md](docs/en/roadmap.md)
- Release checklist: [docs/en/release.md](docs/en/release.md)

## Modules

- `nexary-framework/nexary-core`: deadline, traffic tag, retry, fault, and observation primitives
- `nexary-framework/nexary-spi`: ServiceLoader-first extension registry
- `nexary-cache/nexary-cache-api`: provider-neutral cache, cache-aside, batch, TTL, lock, and atomic counter APIs
- `nexary-cache/nexary-cache-redis`: Redis implementation and Spring Boot auto-configuration with an internal Caffeine L1 for tiered cache mode
- `nexary-messaging/nexary-messaging-api`: provider-neutral publisher, consumer, serializer, retry, dead-letter, interceptor, and duplicate-protection APIs
- `nexary-messaging/nexary-messaging-disruptor`: official LMAX Disruptor-based in-process ring-buffer queue
- `nexary-messaging/nexary-messaging-kafka`: Kafka publisher adapter through a Spring `kafkaTemplate` bean
- `nexary-messaging/nexary-messaging-redis`: Redis list-backed lightweight queue adapter, disabled by default and enabled explicitly when needed
- `nexary-messaging/nexary-messaging-rocketmq`: RocketMQ publisher adapter through a Spring `rocketMQTemplate` bean
- `nexary-job/nexary-job-api`: job, schedule, context, execution ID, execution record, execution policy, and listener APIs
- `nexary-job/nexary-job-scheduler`: local Spring `TaskScheduler` implementation with optional cache-backed single-instance locks, worker topology, sharding, and execution lifecycle
- `nexary-job/nexary-job-xxljob`: XXL-JOB bridge that reuses the shared execution lifecycle
- `nexary-boot/nexary-bom`: dependency constraints for Nexary modules
- `nexary-boot/nexary-*-spring-boot-starter`: starter modules for application integration
- `nexary-samples`: focused starter / SPI reference applications by capability

## Where to Start

### 1. Choose the capability first

- cache only: start at [nexary-cache/README.md](nexary-cache/README.md)
- messaging only: start at [nexary-messaging/README.md](nexary-messaging/README.md)
- job only: start at [nexary-job/README.md](nexary-job/README.md)
- local validation only: start at [docs/en/verification.md](docs/en/verification.md)

### 2. Run the focused reference apps

```bash
./gradlew :nexary-samples:nexary-sample-cache:bootRun
./gradlew :nexary-samples:nexary-sample-messaging:bootRun
./gradlew :nexary-samples:nexary-sample-job:bootRun
```

The sample suite now documents what each app is meant to teach and what should be copied into a production service. See [nexary-samples/README.md](nexary-samples/README.md).

### 3. Validate against real middleware

The repository ships with local Docker workflows for Redis, Kafka, RocketMQ, MySQL, and XXL-JOB Admin:

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
./scripts/middleware/run-integration-tests.sh
```

## Add Nexary to a Spring Boot service

Current development version: `0.2.0-SNAPSHOT`. After the first Maven Central release, replace `${nexary.version}` with the latest release version.

### Version Matrix

| Spring Boot | JDK | Status | Dependency Entry |
| --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | currently verified | current starters / BOM |
| Spring Boot 2.7 | Java 8+ | `0.2.x` target, priority compatibility audit and adaptation | independent Boot2 starters / BOM, not published |
| Spring Boot 4.x | Java 21+ primary verification target | `0.2.x` target after the Boot2 gate | independent Boot4 starters / BOM candidate; official minimum JDK remains defined by Spring documentation |

Before the first public release, the compatibility audit will decide whether starter artifacts should use explicit `spring-boot3` / `spring-boot2` suffixes. Unverified combinations must not appear as supported dependency snippets.

### Maven

Import the BOM first, then choose the starters you need:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.nexary</groupId>
      <artifactId>nexary-bom</artifactId>
      <version>${nexary.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-cache-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-messaging-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-job-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-observation-micrometer-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

### Gradle

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
    implementation 'org.nexary:nexary-cache-spring-boot-starter'
    implementation 'org.nexary:nexary-messaging-spring-boot-starter'
    implementation 'org.nexary:nexary-job-spring-boot-starter'
    implementation 'org.nexary:nexary-observation-micrometer-spring-boot-starter'
}
```

If you do not want starters, use the SPI/provider dependency mode. Business code still depends only on Nexary APIs; the provider is selected through runtime dependencies and `nexary.*` configuration:

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
    implementation 'org.nexary:nexary-messaging-api'
    runtimeOnly 'org.nexary:nexary-messaging-kafka'
    // Switch to RocketMQ by changing dependency/configuration, not business publisher/consumer code.
    // runtimeOnly 'org.nexary:nexary-messaging-rocketmq'
}
```

The current recommendation is one outbound messaging provider per service. If a service needs to route across Kafka and RocketMQ, the application should own that routing facade explicitly instead of relying on hidden framework selection.

## Release and Versioning

- complete namespace verification, signing, SCM metadata, and sources/javadocs before Maven Central publication
- stabilize the `0.2.x` mainline and release pipeline while progressing the Spring Boot 2.7 / Java 8+ compatibility gap audit
- Spring Boot 2 / JDK 8 support should use a dedicated compatibility line or adapter instead of polluting the mainline API
- Spring Boot 4.x / Java 21+ is a later `0.2.x` validation target after the Boot2 compatibility gate passes

See [docs/en/release.md](docs/en/release.md) and [docs/en/roadmap.md](docs/en/roadmap.md) for details.

## Contribution and Maintenance

Nexary is maintained by capability:

- Cache, Messaging, and Job keep clear boundaries with their own samples, tests, and docs.
- Local validation is centralized through Docker, smoke checks, integration tests, and `publishToMavenLocal`.
- Governance capabilities should become separate modules only after their scope is clear.
- Public discussion happens through GitHub issues and pull requests; internal task records are not user documentation.

Entry point: [docs/en/collaboration.md](docs/en/collaboration.md).

## Development

```bash
./gradlew check
./gradlew publishToMavenLocal
```
