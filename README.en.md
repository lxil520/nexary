# Nexary

<p align="center">
  <img src="docs/assets/nexary-social-preview.png" alt="Nexary" width="720">
</p>

Chinese documentation: [README.md](README.md)

**Keep business code focused on business. Keep middleware replaceable.**

Nexary decouples cache, messaging, job scheduling, and observation from business code through stable provider-neutral APIs. Teams can spend their time on product and business growth first; when Redis, Kafka, RocketMQ, XXL-JOB, or another infrastructure choice needs to be upgraded, replaced, or worked around, the migration cost is kept in the framework adapter layer instead of spreading through every business service.

The current `0.2.x` line uses Spring Boot 3.3 / Java 17+ as the mainline and focuses on cache, messaging, job scheduling, SPI, observation bridging, and governance extension points for future resilience features. Spring Boot 2.7 / Java 8+ and Spring Boot 4.1 / Java 21 entries are provided per verified capability.

## Status

Nexary is pre-`1.0.0`. Public APIs are intentionally small and provider-neutral, and implementation modules are expected to evolve before `1.0.0`.

The currently verified mainline is:

- Spring Boot 3.3 / Java 17+ for Cache, Messaging, Job, and the observation bridge
- Spring Boot 2.7 / Java 8+ for the Cache Redis single-tier starter, `nexary-cache-spring-boot2-starter`
- Spring Boot 2.7 / Java 8+ for the Messaging Redis-only starter, `nexary-messaging-spring-boot2-starter`
- Spring Boot 2.7 / Java 8+ for the Job starter, `nexary-job-spring-boot2-starter`
- Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime for the Cache Redis entry, `nexary-cache-spring-boot4-starter`
- Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime for Messaging provider-by-provider entries, `nexary-messaging-spring-boot4-starter` plus one Boot4 provider artifact
- Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime for the bounded Job entry, `nexary-job-spring-boot4-starter`

This boundary comes from Spring Boot 3's own Java 17+ requirement, not from Nexary's initial development JDK.

To reach more users, Nexary includes Spring Boot 2.7 / Java 8+ and Spring Boot 4.1 / Java 21 in the `0.2.x` compatibility target. Support cannot be declared by README alone: it needs independent starters, pinned dependency versions, samples, and CI evidence before it is marked as supported. The dependency snippets below describe only verified combinations. Java 21 is Nexary's primary validation runtime for the Boot4 line; it is not a statement about Spring Boot 4's official minimum JDK. Spring documentation remains the source for that.

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

The current Boot2 Job entry verifies the provider-neutral Job API, local scheduler, XXL-JOB bridge entry, and optional Redis completed-record execution store. It does not claim real XXL-JOB Admin scheduling, executor registration lifecycle, callback lifecycle, platform-triggered execution, PowerJob, a distributed scheduler control plane, or exactly-once execution.

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

The Boot4 Messaging starter is not an aggregate-all-provider starter. Add the provider-neutral starter, then choose exactly one Boot4 provider artifact. The Redis provider example below is copyable as-is.

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

The Boot4 Job entry currently verifies the provider-neutral Job API, local scheduler, bridge-shaped XXL-JOB entry, and optional Redis completed-record execution store. It does not claim real XXL-JOB Admin scheduling, executor registration lifecycle, callback lifecycle, platform-triggered execution, PowerJob, a distributed scheduler control plane, or exactly-once execution.

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

### 11. SPI/provider dependency mode

If you do not want starters, use the SPI/provider dependency mode. Business code still depends only on Nexary APIs; the provider is selected through runtime dependencies and `nexary.*` configuration:

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

The current recommendation is one outbound messaging provider per service. If a service needs to route across Kafka and RocketMQ, the application should own that routing facade explicitly instead of relying on hidden framework selection.

## Release and Versioning

- complete namespace verification, signing, SCM metadata, and sources/javadocs before Maven Central publication
- stabilize the `0.2.x` mainline and release pipeline while progressing Spring Boot 2.7 / Java 8+ and Spring Boot 4.1 / Java 21 capability entries
- Spring Boot 2 / JDK 8 support should use dedicated provider / starter lines instead of polluting the Boot3 mainline API
- Spring Boot 4.1 / Java 21 support uses dedicated Boot4 provider / starter lines; Messaging does not publish an aggregate-all-provider Boot4 starter

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
