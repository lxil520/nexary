# 快速开始

这个页面尽量保持短小直接，优先解决“怎么装、怎么配、怎么跑”。

## 环境要求

- 当前已验证：Java 17 或更高版本 + Spring Boot 3.3.x
- 当前已验证：Spring Boot 2.7 + Java 8+ 的 Cache Redis 单级缓存入口
- 当前已验证：Spring Boot 2.7 + Java 8+ 的 Messaging Redis-only 入口
- 当前已验证：Spring Boot 2.7 + Java 8+ 的 Job 受限边界入口
- 当前已验证：Spring Boot 4.1 + Java 21 主验证运行时的 Cache、Messaging provider-by-provider 和 Job 受限边界入口
- Gradle 8.x，或支持 BOM 的 Maven

## 安装方式

### 1. 选择版本

Nexary 当前还没有 Maven Central 正式版本。现在只能先本地安装 `0.2.0-SNAPSHOT`：

```bash
./gradlew publishToMavenLocal
```

正式发布后，版本选择规则和成熟框架一致：

- 优先使用 Maven Central 显示的 Latest Version。
- 也可以使用 GitHub Releases / Tags 中的版本号，例如 `v0.2.0` 对应依赖版本 `0.2.0`。

不要把 `main` 分支提交号或未发布的 `0.2.0-SNAPSHOT` 当作生产依赖版本。

### 2. 选择 Spring Boot / JDK 入口

| Spring Boot | JDK | 状态 | BOM | Starter artifactId |
| --- | --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | 当前已验证 | `nexary-bom` | `nexary-cache-spring-boot-starter`<br>`nexary-messaging-spring-boot-starter`<br>`nexary-job-spring-boot-starter`<br>`nexary-observation-micrometer-spring-boot-starter` |
| Spring Boot 2.7 | Java 8+ | Cache Redis 单级缓存已验证；Messaging Redis-only 已验证；Job 受限边界已验证 | 当前入口使用直接版本；专用 BOM 另行发布时再切换 | 已验证：`nexary-cache-spring-boot2-starter`<br>已验证：`nexary-messaging-spring-boot2-starter`<br>已验证：`nexary-job-spring-boot2-starter` |
| Spring Boot 4.1 | Java 21 主验证运行时 | Cache Redis 已验证；Messaging provider-by-provider 已验证；Job 受限边界已验证；不是全仓库 Boot4 支持 | 当前入口使用直接版本；专用 BOM 另行发布时再切换 | 已验证：`nexary-cache-spring-boot4-starter`<br>已验证：`nexary-messaging-spring-boot4-starter` 加一个 Boot4 provider artifact<br>已验证：`nexary-job-spring-boot4-starter` |

下面展示当前已验证的 Spring Boot 3.3 / Java 17+ 全能力入口、Spring Boot 2.7 / Java 8+ 入口，以及 Spring Boot 4.1 / Java 21 主验证运行时入口。

### 3. Gradle

先引入 BOM，再按需要引入 starter。

```groovy
// 当前只能本地使用 0.2.0-SNAPSHOT；正式发布后替换为 Maven Central Latest Version 或 tag 版本。
def nexaryVersion = "0.2.0-SNAPSHOT"

dependencies {
    // 使用 BOM 锁定 Nexary 模块版本；正式发布后使用 Latest Version 或 tag 版本。
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")

    // Starter 模式：按能力引入，业务代码只依赖 Nexary API。
    implementation 'org.nexary:nexary-cache-spring-boot-starter'
    implementation 'org.nexary:nexary-messaging-spring-boot-starter'
    implementation 'org.nexary:nexary-job-spring-boot-starter'

    // 可选：Micrometer observation bridge。
    implementation 'org.nexary:nexary-observation-micrometer-spring-boot-starter'
}
```

### 4. Maven

```xml
<properties>
  <!-- 当前只能本地使用 0.2.0-SNAPSHOT；正式发布后替换为 Maven Central Latest Version 或 tag 版本。 -->
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
  <!-- 缓存能力 -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-cache-spring-boot-starter</artifactId>
  </dependency>
  <!-- 消息能力 -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-messaging-spring-boot-starter</artifactId>
  </dependency>
  <!-- 任务能力 -->
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-job-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

### 5. Spring Boot 2.7 / Java 8+ Cache

Boot2 当前只验证 Cache Redis 单级缓存，不包含 tiered local cache。

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

Boot2 Messaging 当前只验证 Redis-only provider/starter。Disruptor、Kafka、RocketMQ 的 Boot2/JDK8 入口仍待独立验证。

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

Boot2 Job 当前验证 provider-neutral Job API、本地 scheduler、XXL-JOB bridge 入口，以及可选 Redis completed-record execution store。不声明真实 XXL-JOB Admin 调度、executor 注册生命周期、回调生命周期、平台触发执行、PowerJob、分布式调度控制面或 exactly-once 执行。

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

Boot4 Messaging starter 只提供 provider-neutral core。必须再选择恰好一个 Boot4 provider artifact。下面是 Redis provider 示例，可直接复制使用。

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

Boot4 Messaging provider artifactId：`nexary-messaging-disruptor-boot4`、`nexary-messaging-redis-boot4`、`nexary-messaging-kafka-boot4`、`nexary-messaging-rocketmq-boot4`。每个服务默认只选择一个。

### 10. Spring Boot 4.1 / Java 21 Job

Boot4 Job 验证本地 scheduler、XXL-JOB bridge-shaped 入口和可选 Redis completed-record execution store，不声明完整 XXL-JOB Admin 生命周期、PowerJob、分布式调度控制面或 exactly-once。

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

### SPI/provider 依赖模式

不用 starter 时，业务代码仍只依赖 Nexary API。具体 provider 通过运行时依赖和配置选择：

```groovy
// 当前只能本地使用 0.2.0-SNAPSHOT；正式发布后替换为 Maven Central Latest Version 或 tag 版本。
def nexaryVersion = "0.2.0-SNAPSHOT"

dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")

    // 业务代码只依赖 Nexary API。
    implementation 'org.nexary:nexary-cache-api'

    // provider 作为运行时依赖选择；切换 provider 不应改业务代码。
    runtimeOnly 'org.nexary:nexary-cache-redis'
}
```

Boot2 / Java8+ 的 Messaging SPI/provider 当前只验证 Redis-only：

```groovy
dependencies {
    implementation 'org.nexary:nexary-messaging-api:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-messaging-redis-spring-boot2:0.2.0-SNAPSHOT'
}
```

Boot2 / Java8+ 的 Job SPI/provider 当前验证本地 scheduler、XXL-JOB bridge 和 Redis completed-record store。只需要本地调度时使用下面这段：

```groovy
dependencies {
    implementation 'org.nexary:nexary-job-api:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-job-scheduler-spring-boot2:0.2.0-SNAPSHOT'
}
```

需要 XXL-JOB bridge 或 Redis completed-record store 时，再分别加入：

```groovy
dependencies {
    runtimeOnly 'org.nexary:nexary-job-xxljob-spring-boot2:0.2.0-SNAPSHOT'
    runtimeOnly 'org.nexary:nexary-job-execution-store-redis-spring-boot2:0.2.0-SNAPSHOT'
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
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
```

下一步建议阅读：[核心概念](concepts.md)
