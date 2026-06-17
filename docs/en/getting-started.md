# Getting Started

This page is intentionally short and task-oriented, similar to the best parts of Spring and MyBatis documentation.

## Requirements

- currently verified: Java 17 or newer + Spring Boot 3.3.x
- `0.2.x` compatibility target: Spring Boot 2.7 + Java 8+, not published
- later `0.2.x` validation target: Spring Boot 4.x + Java 21+, not published
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
| Spring Boot 2.7 | Java 8+ | `0.2.x` target, not published | planned `nexary-spring-boot2-bom` | planned `nexary-cache-spring-boot2-starter`<br>planned `nexary-messaging-spring-boot2-starter`<br>planned `nexary-job-spring-boot2-starter` |
| Spring Boot 4.x | Java 21+ primary verification target | `0.2.x` target after the Boot2 gate, not published | planned `nexary-spring-boot4-bom` | planned `nexary-cache-spring-boot4-starter`<br>planned `nexary-messaging-spring-boot4-starter`<br>planned `nexary-job-spring-boot4-starter` |

The snippets below are only for the currently verified Spring Boot 3.3 / Java 17+ entry.

### 3. Gradle

Use the BOM first, then add only the starters you need.

```groovy
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

### SPI/provider Dependency Mode

Without starters, business code still depends only on Nexary APIs. The concrete provider is selected through runtime dependencies and configuration:

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")

    // Business code depends on the Nexary API only.
    implementation 'org.nexary:nexary-cache-api'

    // Choose the provider as a runtime dependency. Switching providers should not change business code.
    runtimeOnly 'org.nexary:nexary-cache-redis'
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
./gradlew :nexary-samples:nexary-sample-cache:bootRun
./gradlew :nexary-samples:nexary-sample-messaging:bootRun
./gradlew :nexary-samples:nexary-sample-job:bootRun
```

Next: [Core Concepts](concepts.md)
