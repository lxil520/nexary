# Nexary

<p align="center">
  <img src="docs/assets/nexary-social-preview.png" alt="Nexary" width="720">
</p>

Chinese documentation: [README.md](README.md)

[![build](https://github.com/lxil520/nexary/actions/workflows/build.yml/badge.svg)](https://github.com/lxil520/nexary/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/lxil520/nexary)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B%20%7C%2021-007396)](README.en.md)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%20mainline-6DB33F)](README.en.md)

**Keep business code focused on business. Keep middleware replaceable.**

Nexary adds a thin Java API between business code and middleware SDKs. Services call stable entries such as `CacheClient`, `MessagePublisher`, and `NexaryJob`; Redis, Valkey, Kafka, RocketMQ, ActiveMQ Classic, XXL-JOB, and PowerJob wiring stays in framework modules. When infrastructure needs to be upgraded, replaced, or worked around, most changes stay in dependencies and configuration instead of spreading through business code.

The current `0.13.0` line uses Spring Boot 3.3 / Java 17+ as the mainline. The verified capabilities include cache, messaging, jobs, observation bridging, the local governance runtime, the read-only governance diagnostic Console, the Boot3 Sentinel provider, and stop-retry propagation after governance rejection, deadline expiry, cancellation, and timeout. The governance sample shows resources, policy snapshots, runtime snapshots, recent events, slow-call records, open circuit, half-open probes, recovery, Sentinel block reasons, retry stop reasons, and the active governance engine. Spring Boot 2.7 / Java 8+ and Spring Boot 4.1 / Java 21 entries are still provided per verified module.

The `0.13.0` line does not replace Sentinel Dashboard, cluster flow control, or remote rule platforms. It lets Nexary local governance resources execute through Sentinel when explicitly configured and carries useless-request stop signals into messaging and job retry loops: business code still calls Nexary APIs, while Sentinel handles QPS flow control, thread-count isolation, slow-call circuit breaking, and exception circuit breaking. Nexary owns the Java integration boundary, low-cardinality diagnostics, samples, and the read-only Console. Boot2 / Boot4 Sentinel provider support is not in the support matrix yet.

## When to Use It

- Teams maintaining multiple Spring Boot services that do not want cache, messaging, and jobs hard-wired to native middleware SDKs.
- Developers who need room to move between Redis, Kafka, RocketMQ, XXL-JOB, and similar infrastructure without copying a heavy internal platform into every service.
- Users who want copyable dependencies, configuration, and samples instead of a pile of abstract interfaces.

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

To inspect the local governance Console first:

```bash
./scripts/console/up.sh
./scripts/console/smoke.sh
open http://127.0.0.1:18090/nexary/console
```

To try the Boot3 Sentinel provider:

```bash
./gradlew :nexary-samples:nexary-sample-governance-sentinel:run
NEXARY_GOVERNANCE_SENTINEL_BASE_URL=http://localhost:8080 ./scripts/governance-sentinel/smoke.sh
open http://localhost:8080/nexary/console
```

## Status

Nexary is pre-`1.0.0`. Public APIs are intentionally small and Nexary-level, and implementation modules are expected to evolve before `1.0.0`.

The currently verified mainline is:

- Spring Boot 3.3 / Java 17+ for Cache, Messaging, Job, the observation bridge, local governance, the read-only Console, and the Sentinel provider
- Spring Boot 2.7 / Java 8+ for the Cache Redis single-tier starter, `nexary-cache-spring-boot2-starter`
- Spring Boot 2.7 / Java 8+ for the Messaging Redis-only starter, `nexary-messaging-spring-boot2-starter`
- Spring Boot 2.7 / Java 8+ for the Job starter, `nexary-job-spring-boot2-starter`
- Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime for the Cache Redis entry, `nexary-cache-spring-boot4-starter`
- Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime for Messaging single-provider entries, `nexary-messaging-spring-boot4-starter` plus one Boot4 provider artifact
- Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime for the bounded Job entry, `nexary-job-spring-boot4-starter`

This boundary comes from Spring Boot 3's own Java 17+ requirement, not from Nexary's initial development JDK.

To reach more users, Nexary includes Spring Boot 2.7 / Java 8+ and Spring Boot 4.1 / Java 21 in the `0.9.x` validation target. README snippets are only added after samples and tests pass for that combination. Java 21 is Nexary's primary validation runtime for the Boot4 line; it is not a statement about Spring Boot 4's official minimum JDK. Spring documentation remains the source for that.

## Documentation

- Language switch: [docs/README.md](docs/README.md)
- English docs: [docs/en/index.md](docs/en/index.md)
- Chinese docs: [docs/zh/index.md](docs/zh/index.md)

Read by capability:

- Cache: [docs/en/cache.md](docs/en/cache.md)
- Messaging: [docs/en/messaging.md](docs/en/messaging.md)
- Job: [docs/en/job.md](docs/en/job.md)
- Governance: [docs/en/governance.md](docs/en/governance.md)
- Local validation: [docs/en/verification.md](docs/en/verification.md)

General references:

- Architecture: [docs/en/architecture.md](docs/en/architecture.md)
- Contribution and maintenance: [docs/en/collaboration.md](docs/en/collaboration.md)
- Roadmap: [docs/en/roadmap.md](docs/en/roadmap.md)
- Release checklist: [docs/en/release.md](docs/en/release.md)

## Modules

- `nexary-framework/nexary-core`: deadline, traffic tag, retry, fault, and observation primitives
- `nexary-framework/nexary-spi`: ServiceLoader-first extension registry
- `nexary-cache/nexary-cache-api`: Nexary-level cache, cache-aside, batch, TTL, lock, and atomic counter APIs
- `nexary-cache/nexary-cache-redis`: Redis implementation and Spring Boot auto-configuration with an internal Caffeine L1 for tiered cache mode
- `nexary-messaging/nexary-messaging-api`: Nexary-level publisher, consumer, serializer, retry, dead-letter, interceptor, and duplicate-protection APIs
- `nexary-messaging/nexary-messaging-disruptor`: official LMAX Disruptor-based in-process ring-buffer queue
- `nexary-messaging/nexary-messaging-kafka`: Kafka publisher adapter through a Spring `kafkaTemplate` bean
- `nexary-messaging/nexary-messaging-redis`: Redis list-backed lightweight queue adapter, disabled by default and enabled explicitly when needed
- `nexary-messaging/nexary-messaging-rocketmq`: RocketMQ publisher adapter through a Spring `rocketMQTemplate` bean
- `nexary-messaging/nexary-messaging-activemq-classic`: ActiveMQ Classic queue adapter without exposing JMS types to business code
- `nexary-job/nexary-job-api`: job, schedule, context, execution ID, execution record, execution policy, and listener APIs
- `nexary-job/nexary-job-scheduler`: local Spring `TaskScheduler` implementation with optional cache-backed single-instance locks, worker topology, sharding, and execution lifecycle
- `nexary-job/nexary-job-xxljob`: XXL-JOB bridge that reuses the shared execution lifecycle
- `nexary-job/nexary-job-powerjob`: PowerJob bridge that reuses the shared execution lifecycle
- `nexary-governance/nexary-governance-runtime`: deadline, traffic, rate limit, bulkhead, degrade, and retry-stop primitives
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

The current source version is `0.13.0`. If Maven Central has not synced this version yet, build from GitHub tag `v0.13.0` and install it locally:

```bash
./gradlew publishToMavenLocal
```

After Maven Central publication, choose a version in one of two ways:

- Use the Latest Version shown by Maven Central.
- Use a GitHub Releases / Tags version. For example, tag `v0.13.0` maps to dependency version `0.13.0`.

Do not use a `main` branch commit hash as a production dependency version.

### 2. Choose the Spring Boot / JDK entry

| Spring Boot | JDK | Status | Version Choice | BOM | Starter artifactId |
| --- | --- | --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | currently verified | `0.13.0`; use Latest Version after Maven Central sync | `nexary-bom` | `nexary-cache-spring-boot-starter`<br>`nexary-messaging-spring-boot-starter`<br>`nexary-job-spring-boot-starter`<br>`nexary-observation-micrometer-spring-boot-starter`<br>`nexary-governance-spring-boot-starter`<br>`nexary-governance-gateway-spring-boot-starter`<br>`nexary-governance-sentinel-spring-boot-starter` |
| Spring Boot 2.7 | Java 8+ | Cache Redis single-tier, Messaging Redis-only, Job local/XXL-JOB/PowerJob bridge, and Gateway cancellation are verified | `0.13.0`; use Latest Version after Maven Central sync | current entries use direct versions; switch only when a dedicated BOM is released | `nexary-cache-spring-boot2-starter`<br>`nexary-messaging-spring-boot2-starter`<br>`nexary-job-spring-boot2-starter`<br>`nexary-governance-gateway-spring-boot2-starter` |
| Spring Boot 4.1 | Java 21 primary validation runtime | Cache Redis, Messaging by provider, and Job local/XXL-JOB/PowerJob bridge are verified; this is not whole-repository Boot4 support | `0.13.0`; use Latest Version after Maven Central sync | current entries use direct versions; switch only when a dedicated BOM is released | `nexary-cache-spring-boot4-starter`<br>`nexary-messaging-spring-boot4-starter` plus one Boot4 provider artifact<br>`nexary-job-spring-boot4-starter` |

Only verified artifactIds should be copied from the snippets below. Production services should not depend on a `main` branch snapshot.

### 3. Spring Boot 3.3 / Java 17+: Maven

Import the BOM first, then choose the starters you need:

```xml
<properties>
  <!-- After Maven Central sync, this can also use the Latest Version. -->
  <nexary.version>0.13.0</nexary.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.aweimao</groupId>
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
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-cache-spring-boot-starter</artifactId>
  </dependency>
  <!-- Messaging: Nexary-level publisher/consumer APIs; provider selected by configuration. -->
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-messaging-spring-boot-starter</artifactId>
  </dependency>
  <!-- Job: shared job API, local scheduler, and XXL-JOB bridge extension points. -->
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-job-spring-boot-starter</artifactId>
  </dependency>
  <!-- Optional: Micrometer observation bridge. -->
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-observation-micrometer-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

### 4. Spring Boot 3.3 / Java 17+: Gradle

```groovy
// After Maven Central sync, this can also use the Latest Version.
def nexaryVersion = "0.13.0"

dependencies {
    // Use the BOM to keep Nexary modules on one version. After release, set nexaryVersion to Latest Version or a tag version.
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")

    // Starter mode: add the capabilities you need. Business code depends on Nexary APIs, not native middleware SDKs.
    implementation 'com.aweimao:nexary-cache-spring-boot-starter'
    implementation 'com.aweimao:nexary-messaging-spring-boot-starter'
    implementation 'com.aweimao:nexary-job-spring-boot-starter'

    // Optional: bridge Nexary observation events to Micrometer.
    implementation 'com.aweimao:nexary-observation-micrometer-spring-boot-starter'
}
```

### 5. Spring Boot 3.3 / Java 17+: Optional Sentinel Provider

If you already use Sentinel, or want Nexary governance resources to execute flow control, thread isolation, and circuit decisions through Sentinel, add:

```groovy
dependencies {
    implementation 'com.aweimao:nexary-governance-spring-boot-starter'
    implementation 'com.aweimao:nexary-governance-sentinel-spring-boot-starter'
    implementation 'com.aweimao:nexary-console-spring-boot-starter'
}
```

Then choose Sentinel explicitly:

```yaml
nexary:
  governance:
    provider: sentinel
    sentinel:
      enabled: true
      transport:
        enabled: false
    diagnostics:
      enabled: true
  console:
    enabled: true
```

Run the local sample:

```bash
./gradlew :nexary-samples:nexary-sample-governance-sentinel:run
NEXARY_GOVERNANCE_SENTINEL_BASE_URL=http://localhost:8080 ./scripts/governance-sentinel/smoke.sh
```

This starter does not start Sentinel transport by default and does not require Sentinel Dashboard. The dashboard server is used only when `nexary.governance.sentinel.transport.enabled=true` and a server address is configured.

### 6. Spring Boot 2.7 / Java 8+: Cache Redis Single-Tier

The current Boot2 entry verifies only Cache Redis single-tier mode. It does not include tiered local cache; if `nexary.cache.redis.tiered-enabled=true` is set explicitly, the Boot2 starter fails fast with an unsupported-path message.

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-cache-spring-boot2-starter</artifactId>
    <version>0.13.0</version>
  </dependency>
</dependencies>
```

Gradle:

```groovy
dependencies {
    implementation 'com.aweimao:nexary-cache-spring-boot2-starter:0.13.0'
}
```

Recommended configuration:

```yaml
nexary:
  cache:
    redis:
      tiered-enabled: false
```

### 7. Spring Boot 2.7 / Java 8+: Messaging Redis-only

The current Boot2 Messaging entry verifies only the Redis-only provider/starter. Disruptor, Kafka, RocketMQ, and ActiveMQ Classic Boot2/JDK8 entries still require independent verification.

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-messaging-spring-boot2-starter</artifactId>
    <version>0.13.0</version>
  </dependency>
</dependencies>
```

Gradle:

```groovy
dependencies {
    implementation 'com.aweimao:nexary-messaging-spring-boot2-starter:0.13.0'
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

### 8. Spring Boot 2.7 / Java 8+: Job

The current Boot2 Job entry verifies the Job API, local scheduler, XXL-JOB trigger mapping, PowerJob trigger mapping, and optional Redis completed-record execution store. This entry does not claim real XXL-JOB / PowerJob platform scheduling, worker/executor registration lifecycle, complete callback flow, platform-managed trigger execution, a distributed scheduler control plane, exactly-once execution, or running cancellation.

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-job-spring-boot2-starter</artifactId>
    <version>0.13.0</version>
  </dependency>
</dependencies>
```

Gradle:

```groovy
dependencies {
    implementation 'com.aweimao:nexary-job-spring-boot2-starter:0.13.0'
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

### 9. Spring Boot 2.7 / Java 8+: Gateway cancellation

This entry only covers request cancellation propagation in Spring Cloud Gateway: it writes deadline / cancellation headers and notifies the downstream cancellation receiver when the client disconnects. It does not add a remote console, policy writes, or cross-instance state sync.

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-governance-gateway-spring-boot2-starter</artifactId>
    <version>0.13.0</version>
  </dependency>
</dependencies>
```

Gradle:

```groovy
dependencies {
    implementation 'com.aweimao:nexary-governance-gateway-spring-boot2-starter:0.13.0'
}
```

Gateway sample:

```bash
./gradlew :nexary-samples:nexary-sample-governance-gateway-boot2:run
```

### 10. Spring Boot 4.1 / Java 21: Cache

The Boot4 Cache entry currently verifies the Redis provider/starter. This is not whole-repository Boot4 support, and it does not claim Java 21 is Spring Boot 4's official minimum JDK.

```groovy
dependencies {
    implementation 'com.aweimao:nexary-cache-spring-boot4-starter:0.13.0'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-cache-spring-boot4-starter</artifactId>
    <version>0.13.0</version>
  </dependency>
</dependencies>
```

### 11. Spring Boot 4.1 / Java 21: Messaging

The Boot4 Messaging starter is not an aggregate-all-provider starter. Add the Nexary-level starter, then choose exactly one Boot4 provider artifact. The Redis provider example below is copyable as-is.

```groovy
dependencies {
    implementation 'com.aweimao:nexary-messaging-spring-boot4-starter:0.13.0'
    runtimeOnly 'com.aweimao:nexary-messaging-redis-spring-boot4:0.13.0'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-messaging-spring-boot4-starter</artifactId>
    <version>0.13.0</version>
  </dependency>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-messaging-redis-spring-boot4</artifactId>
    <version>0.13.0</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

Boot4 Messaging provider choices:

| Provider | Gradle runtime artifactId | Maven runtime artifactId |
| --- | --- | --- |
| Disruptor | `nexary-messaging-disruptor-spring-boot4` | `nexary-messaging-disruptor-spring-boot4` |
| Redis | `nexary-messaging-redis-spring-boot4` | `nexary-messaging-redis-spring-boot4` |
| Kafka | `nexary-messaging-kafka-spring-boot4` | `nexary-messaging-kafka-spring-boot4` |
| RocketMQ | `nexary-messaging-rocketmq-spring-boot4` | `nexary-messaging-rocketmq-spring-boot4` |

### 12. Spring Boot 4.1 / Java 21: Job

The Boot4 Job entry currently verifies the Job API, local scheduler, XXL-JOB trigger mapping, PowerJob trigger mapping, and optional Redis completed-record execution store. It does not claim real XXL-JOB / PowerJob platform scheduling, worker/executor registration lifecycle, complete callback flow, platform-managed trigger execution, a distributed scheduler control plane, exactly-once execution, or running cancellation.

```groovy
dependencies {
    implementation 'com.aweimao:nexary-job-spring-boot4-starter:0.13.0'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-job-spring-boot4-starter</artifactId>
    <version>0.13.0</version>
  </dependency>
</dependencies>
```

### 13. non-starter dependency mode

If you do not want starters, choose one provider dependency manually. Business code still depends only on Nexary APIs; the provider is selected through runtime dependencies and `nexary.*` configuration:

```groovy
// After Maven Central sync, this can also use the Latest Version.
def nexaryVersion = "0.13.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")

    // Business code compiles against the Nexary API only.
    implementation 'com.aweimao:nexary-messaging-api'

    // Boot3 / Java17+: this example selects the Kafka provider.
    runtimeOnly 'com.aweimao:nexary-messaging-kafka'
}
```

For Boot3 / Java17+ Messaging, replace the runtime dependency with exactly one of `nexary-messaging-disruptor`, `nexary-messaging-redis`, `nexary-messaging-kafka`, `nexary-messaging-rocketmq`, or `nexary-messaging-activemq-classic`. Switching providers should change dependencies and `nexary.*` configuration, not business publisher/consumer code.

Boot2 / Java8+ Messaging is currently verified only for Redis-only:

```groovy
dependencies {
    implementation 'com.aweimao:nexary-messaging-api:0.13.0'
    runtimeOnly 'com.aweimao:nexary-messaging-redis-spring-boot2:0.13.0'
}
```

Boot2 / Java8+ Job without a starter currently verifies the local scheduler, XXL-JOB trigger mapping, PowerJob trigger mapping, and Redis completed-record store. Use this block when the service only needs the local scheduler:

```groovy
dependencies {
    implementation 'com.aweimao:nexary-job-api:0.13.0'
    runtimeOnly 'com.aweimao:nexary-job-scheduler-spring-boot2:0.13.0'
}
```

Add these artifacts when the service needs XXL-JOB, PowerJob, or Redis completed-record store:

```groovy
dependencies {
    runtimeOnly 'com.aweimao:nexary-job-xxljob-spring-boot2:0.13.0'
    runtimeOnly 'com.aweimao:nexary-job-powerjob-spring-boot2:0.13.0'
    runtimeOnly 'com.aweimao:nexary-job-execution-store-redis-spring-boot2:0.13.0'
}
```

Boot4 / Java21 validation-runtime Messaging uses one provider dependency at a time. This is the Redis provider example:

```groovy
dependencies {
    implementation 'com.aweimao:nexary-messaging-api:0.13.0'
    runtimeOnly 'com.aweimao:nexary-messaging-redis-spring-boot4:0.13.0'
}
```

Boot4 / Java21 validation-runtime Messaging provider artifactIds are `nexary-messaging-disruptor-spring-boot4`, `nexary-messaging-redis-spring-boot4`, `nexary-messaging-kafka-spring-boot4`, and `nexary-messaging-rocketmq-spring-boot4`. Select only one by default per service.

Boot4 / Java21 validation-runtime Job local scheduler example:

```groovy
dependencies {
    implementation 'com.aweimao:nexary-job-api:0.13.0'
    runtimeOnly 'com.aweimao:nexary-job-scheduler-spring-boot4:0.13.0'
}
```

Add these artifacts when the service needs XXL-JOB, PowerJob, or Redis completed-record store:

```groovy
dependencies {
    runtimeOnly 'com.aweimao:nexary-job-xxljob-spring-boot4:0.13.0'
    runtimeOnly 'com.aweimao:nexary-job-powerjob-spring-boot4:0.13.0'
    runtimeOnly 'com.aweimao:nexary-job-execution-store-redis-spring-boot4:0.13.0'
}
```

The current recommendation is one outbound messaging provider per service. If a service needs to route across Kafka and RocketMQ, the application should own that routing facade explicitly instead of relying on hidden framework selection.

## Release and Versioning

- complete namespace verification, signing, SCM metadata, and sources/javadocs before Maven Central publication
- before v0.5, follow the [release checklist](docs/en/release.md) to build and inspect the Central Portal bundle; for a bundle-only GitHub Actions check, run the manual workflow with `publish_to_central=false`
- real Central publication must run from the commit behind a `vX.Y.Z` tag; a missing Central token fails the publish step, and manual publication requires the entered version to match the selected tag
- update user-facing dependency versions only after Maven Central has synced
- `0.13.0` includes verified entries for Spring Boot 2.7 / Java 8+ and Spring Boot 4.1 / Java 21
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
