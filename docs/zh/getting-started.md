# 快速开始

这个页面尽量保持短小直接，优先解决“怎么装、怎么配、怎么跑”。

## 环境要求

- 当前已验证：Java 17 或更高版本 + Spring Boot 3.3.x
- `0.2.x` 兼容目标：Spring Boot 2.7 + Java 8+，未发布
- `0.2.x` 后续验证目标：Spring Boot 4.x + Java 21+，未发布
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
| Spring Boot 2.7 | Java 8+ | `0.2.x` 目标，未发布 | 拟定 `nexary-spring-boot2-bom` | 拟定 `nexary-cache-spring-boot2-starter`<br>拟定 `nexary-messaging-spring-boot2-starter`<br>拟定 `nexary-job-spring-boot2-starter` |
| Spring Boot 4.x | Java 21+ 主验证目标 | `0.2.x` 目标，Boot2 gate 通过后推进，未发布 | 拟定 `nexary-spring-boot4-bom` | 拟定 `nexary-cache-spring-boot4-starter`<br>拟定 `nexary-messaging-spring-boot4-starter`<br>拟定 `nexary-job-spring-boot4-starter` |

下面只展示当前已验证的 Spring Boot 3.3 / Java 17+ 入口。

### 3. Gradle

先引入 BOM，再按需要引入 starter。

```groovy
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

### SPI/provider 依赖模式

不用 starter 时，业务代码仍只依赖 Nexary API。具体 provider 通过运行时依赖和配置选择：

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")

    // 业务代码只依赖 Nexary API。
    implementation 'org.nexary:nexary-cache-api'

    // provider 作为运行时依赖选择；切换 provider 不应改业务代码。
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
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
```

下一步建议阅读：[核心概念](concepts.md)
