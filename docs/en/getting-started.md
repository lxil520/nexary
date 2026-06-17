# Getting Started

This page is intentionally short and task-oriented, similar to the best parts of Spring and MyBatis documentation.

## Requirements

- Java 17 or newer
- Spring Boot 3.3.x
- Gradle 8.x or Maven with BOM support

## Installation

Current development version: `0.2.0-SNAPSHOT`. After the first Maven Central release, replace `${nexary.version}` with the latest release version.

### Gradle

Use the BOM first, then add only the starters you need.

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
    implementation 'org.nexary:nexary-cache-spring-boot-starter'
    implementation 'org.nexary:nexary-messaging-spring-boot-starter'
    implementation 'org.nexary:nexary-job-spring-boot-starter'
    implementation 'org.nexary:nexary-observation-micrometer-spring-boot-starter'
}
```

### Maven

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
</dependencies>
```

### SPI/provider Dependency Mode

Without starters, business code still depends only on Nexary APIs. The concrete provider is selected through runtime dependencies and configuration:

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
    implementation 'org.nexary:nexary-cache-api'
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
