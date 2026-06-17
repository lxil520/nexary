# Nexary Messaging

- 中文入口：[../docs/zh/messaging.md](../docs/zh/messaging.md)
- English entry: [../docs/en/messaging.md](../docs/en/messaging.md)

本目录是 Nexary 的消息能力入口，不是全仓库总览。

## 版本入口

当前已验证入口：

| Spring Boot | JDK | 状态 | Starter artifactId | SPI/provider artifactId |
| --- | --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | 已验证 | `nexary-messaging-spring-boot-starter` | `nexary-messaging-api` + 一个 provider：`nexary-messaging-disruptor` / `nexary-messaging-redis` / `nexary-messaging-kafka` / `nexary-messaging-rocketmq` |

兼容目标，不是已发布支持：

| Spring Boot | JDK | 状态 | 拟定 Starter artifactId | 拟定 SPI/provider artifactId |
| --- | --- | --- | --- | --- |
| Spring Boot 2.7 | Java 8+ | `0.2.x` 目标，待验证，未发布 | `nexary-messaging-spring-boot2-starter` | `nexary-messaging-api-java8` 或 `nexary-messaging8-api` + `nexary-messaging-*-spring5` / `nexary-messaging-disruptor-java8` |
| Spring Boot 4.x | Java 21+ | 后续验证目标，待验证，未发布 | `nexary-messaging-spring-boot4-starter` | 待 Boot4 依赖矩阵确认 |

当前 artifact 命名的最小调整建议：正式发布到 Maven Central 前，把已验证的 Boot3 starter 改成显式 `nexary-messaging-spring-boot3-starter`，或至少在 BOM/README 中把 `nexary-messaging-spring-boot-starter` 明确标记为 Boot3-only。不要把 Boot2 / Boot4 兼容声明混进当前 Boot3 starter。

当前关注：

- `nexary-messaging-api`
- Kafka / RocketMQ / Redis queue / Disruptor
- 重复消费保护抽象
- provider-neutral retry policy 和 terminal failure record
- Redis queue ready / processing / ack / retry / stale recovery 状态模型
- provider-neutral observation events for publish / consume / retry / dead-letter / dedup / provider boundaries

验收清单：

- 中文：[../docs/zh/messaging-acceptance.md](../docs/zh/messaging-acceptance.md)
- English: [../docs/en/messaging-acceptance.md](../docs/en/messaging-acceptance.md)
