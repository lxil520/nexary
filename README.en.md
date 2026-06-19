# Nexary

<p align="center">
  <img src="docs/assets/nexary-social-preview.png" alt="Nexary" width="720">
</p>

Chinese documentation: [README.md](README.md)

[![build](https://github.com/lxil520/nexary/actions/workflows/build.yml/badge.svg)](https://github.com/lxil520/nexary/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/lxil520/nexary)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B%20%7C%2021-007396)](README.en.md)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%20mainline-6DB33F)](README.en.md)

**Keep middleware wiring in the framework, not in business code.**

Many services start by calling `RedisTemplate`, `KafkaTemplate`, RocketMQ SDKs, or XXL-JOB annotations directly. That works until the middleware has to be upgraded, replaced, or split and those SDK calls are spread through service code.

Nexary keeps that wiring behind small Java APIs. Business code calls `CacheClient`, `MessagePublisher`, and `NexaryJob`; Redis, Kafka, RocketMQ, and XXL-JOB live in provider modules. If the middleware changes later, the first place to edit is the provider, not every service.

`0.2.x` covers cache, messaging, and scheduled jobs first. Spring Boot 3.3 / Java 17+ is the mainline; Spring Boot 2.7 / Java 8+ and Spring Boot 4.1 / Java 21 entries are shown only after verification.

## When It Helps

- You do not want Redis, Kafka, RocketMQ, or XXL-JOB code scattered through controllers, services, handlers, or consumers.
- You have picked a middleware today but may need to replace it later.
- You want to run a sample first, then copy the relevant shape into your own service.

## 10-Minute Trial

```bash
git clone https://github.com/lxil520/nexary.git
cd nexary
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
```

To validate against real middleware directly:

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
./scripts/middleware/run-integration-tests.sh
```

## Project Status

Nexary is still pre-`1.0.0`. Public APIs are kept small; provider implementations will keep moving before the first stable line.

Verified combinations:

- Spring Boot 3.3 / Java 17+ for Cache, Messaging, Job, and the observation bridge
- Spring Boot 2.7 / Java 8+ for the Cache Redis single-tier starter, `nexary-cache-spring-boot2-starter`
- Spring Boot 2.7 / Java 8+ for the Messaging Redis-only starter, `nexary-messaging-spring-boot2-starter`
- Spring Boot 2.7 / Java 8+ for the Job starter, `nexary-job-spring-boot2-starter`
- Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime for the Cache Redis entry, `nexary-cache-spring-boot4-starter`
- Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime for Messaging provider-by-provider entries, `nexary-messaging-spring-boot4-starter` plus one Boot4 provider artifact
- Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime for the bounded Job entry, `nexary-job-spring-boot4-starter`

The Boot3 boundary comes from Spring Boot 3's own Java 17+ requirement, not from Nexary's initial development JDK. Boot2 / Java 8+ and Boot4 / Java 21 are verified module by module. A combination is not documented as supported until its starter, dependency set, sample, and CI gate have passed. Java 21 is Nexary's Boot4 validation runtime, not a claim about Spring Boot 4's official minimum JDK.

## Documentation

- Language switch: [docs/README.md](docs/README.md)
- English docs: [docs/en/index.md](docs/en/index.md)
- Chinese docs: [docs/zh/index.md](docs/zh/index.md)

Read by what you want to wire:

- Configuration: [docs/en/configuration.md](docs/en/configuration.md)
- Cache: [docs/en/cache.md](docs/en/cache.md)
- Messaging: [docs/en/messaging.md](docs/en/messaging.md)
- Job: [docs/en/job.md](docs/en/job.md)
- Local validation: [docs/en/verification.md](docs/en/verification.md)

Maintenance and release:

- Architecture: [docs/en/architecture.md](docs/en/architecture.md)
- Contribution and maintenance: [docs/en/collaboration.md](docs/en/collaboration.md)
- Roadmap: [docs/en/roadmap.md](docs/en/roadmap.md)
- Release checklist: [docs/en/release.md](docs/en/release.md)

## Modules

- `nexary-framework/nexary-core`: deadline, traffic tag, retry, fault, and observation primitives
- `nexary-framework/nexary-spi`: ServiceLoader-first extension registry
- `nexary-cache/nexary-cache-api`: cache APIs for TTL, batch operations, cache-aside, locks, and atomic counters
- `nexary-cache/nexary-cache-redis`: Redis implementation and Spring Boot auto-configuration with an internal Caffeine L1 for tiered cache mode
- `nexary-messaging/nexary-messaging-api`: messaging APIs for envelopes, publishers, consumers, serializers, retries, dead letters, interceptors, and duplicate protection
- `nexary-messaging/nexary-messaging-disruptor`: official LMAX Disruptor-based in-process ring-buffer queue
- `nexary-messaging/nexary-messaging-kafka`: Kafka publisher adapter through a Spring `kafkaTemplate` bean
- `nexary-messaging/nexary-messaging-redis`: Redis list-backed lightweight queue adapter, disabled by default and enabled explicitly when needed
- `nexary-messaging/nexary-messaging-rocketmq`: RocketMQ publisher adapter through a Spring `rocketMQTemplate` bean
- `nexary-job/nexary-job-api`: job, schedule, context, execution ID, execution record, execution policy, and listener APIs
- `nexary-job/nexary-job-scheduler`: local Spring `TaskScheduler` implementation with optional cache-backed single-instance locks, worker topology, sharding, and execution lifecycle
- `nexary-job/nexary-job-xxljob`: XXL-JOB bridge that reuses the shared execution lifecycle
- `nexary-boot/nexary-bom`: dependency constraints for Nexary modules
- `nexary-boot/nexary-*-spring-boot-starter`: starter modules for application integration
- `nexary-samples`: starter / SPI reference applications split by cache, messaging, and job

## Where to Start

### 1. Pick what you want to wire

- cache only: start at [nexary-cache/README.md](nexary-cache/README.md)
- messaging only: start at [nexary-messaging/README.md](nexary-messaging/README.md)
- job only: start at [nexary-job/README.md](nexary-job/README.md)
- local validation only: start at [docs/en/verification.md](docs/en/verification.md)

### 2. Run a small sample first

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
| Spring Boot 2.7 | Java 8+ | Cache Redis single-tier is verified; Messaging Redis-only is verified; Job is verified with a bounded scope | local `0.2.0-SNAPSHOT`; after release, use Latest Version or a tag version | current entries use direct versions; switch only when a dedicated BOM is released | verified: `nexary-cache-spring-boot2-starter`<br>verified: `nexary-messaging-spring-boot2-starter`<br>verified: `nexary-job-spring-boot2-starter` |
| Spring Boot 4.1 | Java 21 primary validation runtime | Cache Redis is verified; Messaging is verified provider-by-provider; Job is verified with a bounded scope; this is not whole-repository Boot4 support | local `0.2.0-SNAPSHOT`; after release, use Latest Version or a tag version | current entries use direct versions; switch only when a dedicated BOM is released | verified: `nexary-cache-spring-boot4-starter`<br>verified: `nexary-messaging-spring-boot4-starter` plus one Boot4 provider artifact<br>verified: `nexary-job-spring-boot4-starter` |

Only verified artifactIds should be copied from the snippets below. There is no Maven Central release yet, so production services should wait for a release version.

### 3. Spring Boot 3.3 / Java 17+: Maven

Import the BOM first, then choose the starters you need:

```xml
<properties>
  <!-- Today only local 0.2.0-SNAPSHOT is available. After release, replace it with the Maven Central Latest Version or a tag version. -->
  <nexary.version>0.2.0-SNAPSHOT</nexary.version>
</properties>

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
  <!-- Messaging: publisher/consumer APIs; provider selected by configuration. -->
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
// Today only local 0.2.0-SNAPSHOT is available. After release, replace it with the Maven Central Latest Version or a tag version.
def nexaryVersion = "0.2.0-SNAPSHOT"

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

### 5. Spring Boot 2.7 / Java 8+: Cache Redis Single-Tier

The current Boot2 entry verifies only Cache Redis single-tier mode. It does not include tiered local cache; if `nexary.cache.redis.tiered-enabled=true` is set explicitly, the Boot2 starter fails fast with an unsupported-path message.

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-cache-spring-boot2-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

Gradle:

```groovy
dependencies {
    implementation 'org.nexary:nexary-cache-spring-boot2-starter:0.2.0-SNAPSHOT'
}
```

Recommended configuration:

```yaml
nexary:
  cache:
    redis:
      tiered-enabled: false
```

### 6. Spring Boot 2.7 / Java 8+: Messaging Redis-only

The current Boot2 Messaging entry verifies only the Redis-only provider/starter. Disruptor, Kafka, and RocketMQ Boot2/JDK8 entries still require independent verification.

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-messaging-spring-boot2-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

Gradle:

```groovy
dependencies {
    implementation 'org.nexary:nexary-messaging-spring-boot2-starter:0.2.0-SNAPSHOT'
}
```

Recommended configuration:

```yaml
nexary:
  messaging:
    provider: redis
    redis:
      enabled: true
```

### 7. Spring Boot 2.7 / Java 8+: Job

The Boot2 Job entry verifies the shared Job API, local scheduler, XXL-JOB bridge entry, and optional Redis storage for completed execution records. It does not claim real XXL-JOB Admin scheduling, executor registration lifecycle, callback lifecycle, platform-triggered execution, PowerJob, a distributed scheduler control plane, or exactly-once execution.

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-job-spring-boot2-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

Gradle:

```groovy
dependencies {
    implementation 'org.nexary:nexary-job-spring-boot2-starter:0.2.0-SNAPSHOT'
}
```

Optional Redis completed-record store:

```yaml
nexary:
  job:
    execution:
      store:
        redis:
          enabled: true
          retention: 1d
```

### 8. Spring Boot 4.1 / Java 21: Cache

The Boot4 Cache entry currently verifies the Redis provider/starter. This is not whole-repository Boot4 support, and it does not claim Java 21 is Spring Boot 4's official minimum JDK.

```groovy
dependencies {
    implementation 'org.nexary:nexary-cache-spring-boot4-starter:0.2.0-SNAPSHOT'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-cache-spring-boot4-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

### 9. Spring Boot 4.1 / Java 21: Messaging

The Boot4 Messaging starter does not bring every provider onto the classpath. Add the base starter, then choose one Boot4 provider artifact. The Redis provider example below is copyable as-is.

```groovy
dependencies {
    implementation 'org.nexary:nexary-messaging-spring-boot4-starter:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-messaging-redis-boot4:0.2.0-SNAPSHOT'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-messaging-spring-boot4-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-messaging-redis-boot4</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

Boot4 Messaging provider choices:

| Provider | Gradle runtime artifactId | Maven runtime artifactId |
| --- | --- | --- |
| Disruptor | `nexary-messaging-disruptor-boot4` | `nexary-messaging-disruptor-boot4` |
| Redis | `nexary-messaging-redis-boot4` | `nexary-messaging-redis-boot4` |
| Kafka | `nexary-messaging-kafka-boot4` | `nexary-messaging-kafka-boot4` |
| RocketMQ | `nexary-messaging-rocketmq-boot4` | `nexary-messaging-rocketmq-boot4` |

### 10. Spring Boot 4.1 / Java 21: Job

The Boot4 Job entry verifies the shared Job API, local scheduler, XXL-JOB bridge trigger mapping, and optional Redis storage for completed execution records. It does not claim real XXL-JOB Admin scheduling, executor registration lifecycle, callback lifecycle, platform-triggered execution, PowerJob, a distributed scheduler control plane, or exactly-once execution.

```groovy
dependencies {
    implementation 'org.nexary:nexary-job-spring-boot4-starter:0.2.0-SNAPSHOT'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-job-spring-boot4-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

### 11. Using API + provider dependencies

If you do not want starters, add the API at compile time and choose a provider as a runtime dependency. Business code still depends only on Nexary APIs; the concrete provider is selected through dependencies and `nexary.*` configuration:

```groovy
// Today only local 0.2.0-SNAPSHOT is available. After release, replace it with the Maven Central Latest Version or a tag version.
def nexaryVersion = "0.2.0-SNAPSHOT"

dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")

    // Business code compiles against the Nexary API only.
    implementation 'org.nexary:nexary-messaging-api'

    // Boot3 / Java17+: this example selects the Kafka provider.
    runtimeOnly 'org.nexary:nexary-messaging-kafka'
}
```

For Boot3 / Java17+ Messaging, replace the runtime dependency with exactly one of `nexary-messaging-disruptor`, `nexary-messaging-redis`, `nexary-messaging-kafka`, or `nexary-messaging-rocketmq`. Switching providers should change dependencies and `nexary.*` configuration, not business publisher/consumer code.

Boot2 / Java8+ Messaging SPI/provider mode is currently verified only for Redis-only:

```groovy
dependencies {
    implementation 'org.nexary:nexary-messaging-api:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-messaging-redis-boot2:0.2.0-SNAPSHOT'
}
```

Boot2 / Java8+ Job SPI/provider mode currently verifies the local scheduler, XXL-JOB bridge, and Redis completed-record store. Use this block when the service only needs the local scheduler:

```groovy
dependencies {
    implementation 'org.nexary:nexary-job-api:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-job-scheduler-spring-boot2:0.2.0-SNAPSHOT'
}
```

Add these artifacts when the service needs the XXL-JOB bridge or Redis completed-record store:

```groovy
dependencies {
    runtimeOnly 'org.nexary:nexary-job-xxljob-spring-boot2:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-job-execution-store-redis-spring-boot2:0.2.0-SNAPSHOT'
}
```

Boot4 / Java21 validation-runtime Messaging SPI/provider mode is provider-by-provider. This is the Redis provider example:

```groovy
dependencies {
    implementation 'org.nexary:nexary-messaging-api:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-messaging-redis-boot4:0.2.0-SNAPSHOT'
}
```

Boot4 / Java21 validation-runtime Messaging provider artifactIds are `nexary-messaging-disruptor-boot4`, `nexary-messaging-redis-boot4`, `nexary-messaging-kafka-boot4`, and `nexary-messaging-rocketmq-boot4`. Select only one by default per service.

Boot4 / Java21 validation-runtime Job SPI/provider local scheduler example:

```groovy
dependencies {
    implementation 'org.nexary:nexary-job-api:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-job-scheduler-spring-boot4:0.2.0-SNAPSHOT'
}
```

Add these artifacts when the service needs the XXL-JOB bridge or Redis completed-record store:

```groovy
dependencies {
    runtimeOnly 'org.nexary:nexary-job-xxljob-spring-boot4:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-job-execution-store-redis-spring-boot4:0.2.0-SNAPSHOT'
}
```

The practical default is one outbound messaging provider per service. If a service needs to route across Kafka and RocketMQ, put that routing in application code explicitly instead of relying on hidden framework selection.

## Release and Versioning

- complete namespace verification, signing, SCM metadata, and sources/javadocs before Maven Central publication
- stabilize the `0.2.x` mainline and release pipeline while progressing Spring Boot 2.7 / Java 8+ and Spring Boot 4.1 / Java 21 entries
- Spring Boot 2 / JDK 8 support should use dedicated provider / starter lines instead of polluting the Boot3 mainline API
- Spring Boot 4.1 / Java 21 support uses dedicated Boot4 provider / starter lines; Messaging does not publish an aggregate-all-provider Boot4 starter

See [docs/en/release.md](docs/en/release.md) and [docs/en/roadmap.md](docs/en/roadmap.md) for details.

## Contribution and Maintenance

Nexary is maintained by module:

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
