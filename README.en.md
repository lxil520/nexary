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
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
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

### 1. Choose the Nexary version first

Nexary does not have a Maven Central release yet. Today, only the locally built `0.2.0-SNAPSHOT` is available:

```bash
./gradlew publishToMavenLocal
```

After the first public release, choose a version in one of two ways:

- Use the Latest Version shown by Maven Central.
- Use a GitHub Releases / Tags version. For example, tag `v0.2.0` maps to dependency version `0.2.0`.

Do not use a `main` branch commit hash or an unpublished `0.2.0-SNAPSHOT` as a production dependency version.

### 2. Choose the Spring Boot / JDK entry

| Spring Boot | JDK | Status | Version Choice | BOM | Starter artifactId |
| --- | --- | --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | currently verified | local `0.2.0-SNAPSHOT`; after release, use Latest Version or a tag version | `nexary-bom` | `nexary-cache-spring-boot-starter`<br>`nexary-messaging-spring-boot-starter`<br>`nexary-job-spring-boot-starter`<br>`nexary-observation-micrometer-spring-boot-starter` |
| Spring Boot 2.7 | Java 8+ | `0.2.x` target, priority compatibility audit and adaptation, not published | official version only after the gate passes | planned `nexary-spring-boot2-bom` | planned `nexary-cache-spring-boot2-starter`<br>planned `nexary-messaging-spring-boot2-starter`<br>planned `nexary-job-spring-boot2-starter` |
| Spring Boot 4.x | Java 21+ primary verification target | `0.2.x` target after the Boot2 gate, not published | official version only after the gate passes | planned `nexary-spring-boot4-bom` | planned `nexary-cache-spring-boot4-starter`<br>planned `nexary-messaging-spring-boot4-starter`<br>planned `nexary-job-spring-boot4-starter` |

Only the currently verified combination should be copied from the snippets below. ArtifactIds marked as planned are `0.2.x` compatibility targets, not published or supported artifacts.

### 3. Spring Boot 3.3 / Java 17+: Maven

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
  <!-- Cache: CacheClient, locks, atomic counters, and Redis provider auto-configuration. -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-cache-spring-boot-starter</artifactId>
  </dependency>
  <!-- Messaging: provider-neutral publisher/consumer APIs; provider selected by configuration. -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-messaging-spring-boot-starter</artifactId>
  </dependency>
  <!-- Job: shared job API, local scheduler, and XXL-JOB bridge extension points. -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-job-spring-boot-starter</artifactId>
  </dependency>
  <!-- Optional: Micrometer observation bridge. -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-observation-micrometer-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

### 4. Spring Boot 3.3 / Java 17+: Gradle

```groovy
dependencies {
    // Use the BOM to keep Nexary modules on one version. After release, set nexaryVersion to Latest Version or a tag version.
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")

    // Starter mode: add the capabilities you need. Business code depends on Nexary APIs, not native middleware SDKs.
    implementation 'org.nexary:nexary-cache-spring-boot-starter'
    implementation 'org.nexary:nexary-messaging-spring-boot-starter'
    implementation 'org.nexary:nexary-job-spring-boot-starter'

    // Optional: bridge Nexary observation events to Micrometer.
    implementation 'org.nexary:nexary-observation-micrometer-spring-boot-starter'
}
```

### 5. SPI/provider dependency mode

If you do not want starters, use the SPI/provider dependency mode. Business code still depends only on Nexary APIs; the provider is selected through runtime dependencies and `nexary.*` configuration:

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")

    // Business code compiles against the Nexary API only.
    implementation 'org.nexary:nexary-messaging-api'

    // Select one provider at runtime. Switch to RocketMQ by changing dependency/configuration, not business publisher/consumer code.
    runtimeOnly 'org.nexary:nexary-messaging-kafka'
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
