# Getting Started

This page is intentionally short and task-oriented, similar to the best parts of Spring and MyBatis documentation.

## Requirements

- currently verified: Java 17 or newer + Spring Boot 3.3.x
- currently verified: Spring Boot 2.7 + Java 8+ for the Cache Redis single-tier entry
- currently verified: Spring Boot 2.7 + Java 8+ for the Messaging Redis-only entry
- currently verified: Spring Boot 2.7 + Java 8+ for the bounded Job entry
- currently verified: Spring Boot 4.1 + Java 21 as Nexary's primary validation runtime for Cache, Messaging provider-by-provider, and bounded Job entries
- Gradle 8.x or Maven with BOM support

## Installation

### 1. Choose the version

Nexary does not have a Maven Central release yet. Today, only the locally built `0.2.0-SNAPSHOT` is available:

```bash
./gradlew publishToMavenLocal
```

After the first public release, choose a version the same way you would for mature Java frameworks:

- Prefer the Latest Version shown by Maven Central.
- Or use a GitHub Releases / Tags version. For example, tag `v0.2.0` maps to dependency version `0.2.0`.

Do not use a `main` branch commit hash or an unpublished `0.2.0-SNAPSHOT` as a production dependency version.

### 2. Choose the Spring Boot / JDK entry

| Spring Boot | JDK | Status | BOM | Starter artifactId |
| --- | --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | currently verified | `nexary-bom` | `nexary-cache-spring-boot-starter`<br>`nexary-messaging-spring-boot-starter`<br>`nexary-job-spring-boot-starter`<br>`nexary-observation-micrometer-spring-boot-starter` |
| Spring Boot 2.7 | Java 8+ | Cache Redis single-tier is verified; Messaging Redis-only is verified; Job is verified with a bounded scope | current entries use direct versions; switch only when a dedicated BOM is released | verified: `nexary-cache-spring-boot2-starter`<br>verified: `nexary-messaging-spring-boot2-starter`<br>verified: `nexary-job-spring-boot2-starter` |
| Spring Boot 4.1 | Java 21 primary validation runtime | Cache Redis is verified; Messaging is verified provider-by-provider; Job is verified with a bounded scope; this is not whole-repository Boot4 support | current entries use direct versions; switch only when a dedicated BOM is released | verified: `nexary-cache-spring-boot4-starter`<br>verified: `nexary-messaging-spring-boot4-starter` plus one Boot4 provider artifact<br>verified: `nexary-job-spring-boot4-starter` |

The snippets below cover the currently verified Spring Boot 3.3 / Java 17+ full-capability entry, Spring Boot 2.7 / Java 8+ entries, and Spring Boot 4.1 / Java 21 primary-validation-runtime entries.

### 3. Gradle

Use the BOM first, then add only the starters you need.

```groovy
// Today only local 0.2.0-SNAPSHOT is available. After release, replace it with the Maven Central Latest Version or a tag version.
def nexaryVersion = "0.2.0-SNAPSHOT"

dependencies {
    // Use the BOM to keep Nexary modules on one version. After release, use Latest Version or a tag version.
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")

    // Starter mode: add capabilities as needed. Business code depends on Nexary APIs only.
    implementation 'org.nexary:nexary-cache-spring-boot-starter'
    implementation 'org.nexary:nexary-messaging-spring-boot-starter'
    implementation 'org.nexary:nexary-job-spring-boot-starter'

    // Optional: Micrometer observation bridge.
    implementation 'org.nexary:nexary-observation-micrometer-spring-boot-starter'
}
```

### 4. Maven

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
  <!-- Cache capability -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-cache-spring-boot-starter</artifactId>
  </dependency>
  <!-- Messaging capability -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-messaging-spring-boot-starter</artifactId>
  </dependency>
  <!-- Job capability -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-job-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

### 5. Spring Boot 2.7 / Java 8+ Cache

Boot2 currently verifies only Cache Redis single-tier mode. It does not include tiered local cache.

```groovy
dependencies {
    implementation 'org.nexary:nexary-cache-spring-boot2-starter:0.2.0-SNAPSHOT'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-cache-spring-boot2-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

```yaml
nexary:
  cache:
    redis:
      tiered-enabled: false
```

### 6. Spring Boot 2.7 / Java 8+ Messaging Redis-only

Boot2 Messaging currently verifies only the Redis-only provider/starter. Disruptor, Kafka, and RocketMQ Boot2/JDK8 entries still require independent verification.

```groovy
dependencies {
    implementation 'org.nexary:nexary-messaging-spring-boot2-starter:0.2.0-SNAPSHOT'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-messaging-spring-boot2-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

```yaml
nexary:
  messaging:
    provider: redis
    redis:
      enabled: true
```

### 7. Spring Boot 2.7 / Java 8+ Job

Boot2 Job currently verifies the provider-neutral Job API, local scheduler, XXL-JOB bridge entry, and optional Redis completed-record execution store. It does not claim real XXL-JOB Admin scheduling, executor registration lifecycle, callback lifecycle, platform-triggered execution, PowerJob, a distributed scheduler control plane, or exactly-once execution.

```groovy
dependencies {
    implementation 'org.nexary:nexary-job-spring-boot2-starter:0.2.0-SNAPSHOT'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-job-spring-boot2-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

```yaml
nexary:
  job:
    execution:
      store:
        redis:
          enabled: true
          retention: 1d
```

### 8. Spring Boot 4.1 / Java 21 Cache

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

### 9. Spring Boot 4.1 / Java 21 Messaging

The Boot4 Messaging starter provides provider-neutral core only. Choose exactly one Boot4 provider artifact. The Redis provider example below is copyable as-is.

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

Boot4 Messaging provider artifactIds are `nexary-messaging-disruptor-boot4`, `nexary-messaging-redis-boot4`, `nexary-messaging-kafka-boot4`, and `nexary-messaging-rocketmq-boot4`. Select only one by default per service.

### 10. Spring Boot 4.1 / Java 21 Job

Boot4 Job verifies the local scheduler, bridge-shaped XXL-JOB entry, and optional Redis completed-record execution store. It does not claim the full XXL-JOB Admin lifecycle, PowerJob, a distributed scheduler control plane, or exactly-once execution.

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

### SPI/provider Dependency Mode

Without starters, business code still depends only on Nexary APIs. The concrete provider is selected through runtime dependencies and configuration:

```groovy
// Today only local 0.2.0-SNAPSHOT is available. After release, replace it with the Maven Central Latest Version or a tag version.
def nexaryVersion = "0.2.0-SNAPSHOT"

dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")

    // Business code depends on the Nexary API only.
    implementation 'org.nexary:nexary-cache-api'

    // Choose the provider as a runtime dependency. Switching providers should not change business code.
    runtimeOnly 'org.nexary:nexary-cache-redis'
}
```

Boot2 / Java8+ Messaging SPI/provider mode is currently verified only for Redis-only:

```groovy
dependencies {
    implementation 'org.nexary:nexary-messaging-api:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-messaging-redis-spring-boot2:0.2.0-SNAPSHOT'
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

## Minimal Configuration

```yaml
nexary:
  cache:
    redis:
      default-ttl: 10m
  messaging:
    kafka:
      enabled: true
    rocketmq:
      enabled: false
```

## Typical Usage

Cache:

```java
SampleUser user = cacheClient.cacheAside(
    CacheKey.of("user", "42"),
    SampleUser.class,
    Duration.ofMinutes(5),
    () -> repository.loadUser("42")
);
```

Messaging:

```java
publisher.publish(MessageEnvelope.of("sample.events", payload));
```

Job:

```java
scheduler.schedule(job, JobSchedule.single("demo-job", "0 */5 * * * *"));
```

## Run Samples

Choose the focused sample for the capability you need:

```bash
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
```

Next: [Core Concepts](concepts.md)
