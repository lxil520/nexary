# Nexary Messaging

- 中文入口：[../docs/zh/messaging.md](../docs/zh/messaging.md)
- English entry: [../docs/en/messaging.md](../docs/en/messaging.md)

本目录是 Nexary 的消息模块入口，不是全仓库总览。

## 版本入口

当前开发版本示例统一使用 `0.3.1`。发布到 Maven Central 后，把示例中的版本替换成最新 release。

| Spring Boot | JDK | 状态 | Starter artifactId | SPI/provider artifactId |
| --- | --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | 已验证主线 | `nexary-messaging-spring-boot-starter` | `nexary-messaging-api` + 一个 provider：`nexary-messaging-disruptor` / `nexary-messaging-redis` / `nexary-messaging-kafka` / `nexary-messaging-rocketmq` / `nexary-messaging-activemq-classic` |
| Spring Boot 2.7 | Java 8+ | Redis-only 已验证 | `nexary-messaging-spring-boot2-starter` | `nexary-messaging-api` + `nexary-messaging-redis-spring-boot2` |
| Spring Boot 4.1 | Java 21 作为 Nexary 主要验证运行时 | provider-by-provider 已验证；starter 仅 Nexary 层 core | `nexary-messaging-spring-boot4-starter` + 恰好一个 provider artifact | `nexary-messaging-api` + 一个 Spring Boot 4 provider：`nexary-messaging-disruptor-spring-boot4` / `nexary-messaging-redis-spring-boot4` / `nexary-messaging-kafka-spring-boot4` / `nexary-messaging-rocketmq-spring-boot4` |

仍需独立验证的 Boot2 provider 范围：

| Spring Boot | JDK | Provider | 状态 |
| --- | --- | --- | --- |
| Spring Boot 2.7 | Java 8+ | Disruptor / Kafka / RocketMQ / ActiveMQ Classic | 未验证，不要按已支持能力接入 |

Spring Boot 3.3 / Java 17+ starter：

```groovy
def nexaryVersion = "0.3.1"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation "com.aweimao:nexary-messaging-spring-boot-starter"
}
```

Boot2 / Java8+ 的 Messaging 已验证范围只包括 Redis-only。不要把 Disruptor、Kafka、RocketMQ、ActiveMQ Classic 的 Boot2/JDK8 支持写成已支持；这些 provider 需要各自独立兼容 gate。

Spring Boot 2.7 / Java 8+ Redis-only starter：

```groovy
dependencies {
    implementation "com.aweimao:nexary-messaging-spring-boot2-starter:0.3.1"
}
```

Spring Boot 2.7 / Java 8+ Redis-only SPI/provider：

```groovy
dependencies {
    implementation "com.aweimao:nexary-messaging-api:0.3.1"
    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot2:0.3.1"
}
```

Spring Boot 4.1 / Java 21 主要验证运行时的 starter 入口必须显式选择一个 provider。`nexary-messaging-spring-boot4-starter` 只提供 Nexary 层 core 自动配置，不聚合所有 provider：

```groovy
def nexaryVersion = "0.3.1"

dependencies {
    implementation "com.aweimao:nexary-messaging-spring-boot4-starter:${nexaryVersion}"

    // 只选择一个 provider。不要同时把 Kafka 与 RocketMQ 等所有 provider 放进同一个 Boot4 starter classpath。
    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot4:${nexaryVersion}"
}
```

可选择的 Boot4 provider artifactId：`nexary-messaging-disruptor-spring-boot4`、`nexary-messaging-redis-spring-boot4`、`nexary-messaging-kafka-spring-boot4`、`nexary-messaging-rocketmq-spring-boot4`。

Spring Boot 4.1 / Java 21 主要验证运行时的 SPI/provider：

```groovy
def nexaryVersion = "0.3.1"

dependencies {
    implementation "com.aweimao:nexary-messaging-api:${nexaryVersion}"

    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot4:${nexaryVersion}"
}
```

Spring Boot 4 官方最低 JDK 以 Spring 官方文档为准；这里的 Java 21 是 Nexary 对 Boot4 线的主要验证运行时。Messaging Boot4 starter 不声明 all-provider aggregate readiness。

当前关注：

- `nexary-messaging-api`
- Kafka / RocketMQ / Redis queue / Disruptor
- ActiveMQ Classic queue provider for the Spring Boot 3.3 / Java 17+ mainline
- 重复消费保护抽象
- Nexary 层 retry policy 和 terminal failure record
- Redis queue ready / processing / ack / retry / stale recovery 状态模型
- Nexary 层 observation events for publish / consume / retry / dead-letter / dedup / provider boundaries

ActiveMQ Classic 与 Artemis 不作为同一个 provider 声明。当前 ActiveMQ 能力只指 `nexary-messaging-activemq-classic`；Artemis 需要单独实现和验证。

验收清单：

- 中文：[../docs/zh/messaging-acceptance.md](../docs/zh/messaging-acceptance.md)
- English: [../docs/en/messaging-acceptance.md](../docs/en/messaging-acceptance.md)
