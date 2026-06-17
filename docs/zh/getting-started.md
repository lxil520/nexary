# 快速开始

这个页面尽量保持短小直接，优先解决“怎么装、怎么配、怎么跑”。

## 环境要求

- Java 17 或更高版本
- Spring Boot 3.3.x
- Gradle 8.x，或支持 BOM 的 Maven

## 安装方式

当前开发版本是 `0.2.0-SNAPSHOT`。正式发布到 Maven Central 后，将 `${nexary.version}` 替换为最新 release 版本。

### Gradle

先引入 BOM，再按需要引入 starter。

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

### SPI/provider 依赖模式

不用 starter 时，业务代码仍只依赖 Nexary API。具体 provider 通过运行时依赖和配置选择：

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
    implementation 'org.nexary:nexary-cache-api'
    runtimeOnly 'org.nexary:nexary-cache-redis'
}
```

## 最小配置

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

## 典型用法

缓存：

```java
SampleUser user = cacheClient.cacheAside(
    CacheKey.of("user", "42"),
    SampleUser.class,
    Duration.ofMinutes(5),
    () -> repository.loadUser("42")
);
```

消息：

```java
publisher.publish(MessageEnvelope.of("sample.events", payload));
```

任务：

```java
scheduler.schedule(job, JobSchedule.single("demo-job", "0 */5 * * * *"));
```

## 运行样例

按能力选择专项样例：

```bash
./gradlew :nexary-samples:nexary-sample-cache:bootRun
./gradlew :nexary-samples:nexary-sample-messaging:bootRun
./gradlew :nexary-samples:nexary-sample-job:bootRun
```

下一步建议阅读：[核心概念](concepts.md)
