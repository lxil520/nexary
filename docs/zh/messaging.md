# Messaging 指南

Messaging 是 provider 最多、边界最容易被做乱的一项能力，所以必须单独看。

## 你应该先看什么

- 模块入口：[../../nexary-messaging/README.md](../../nexary-messaging/README.md)
- 模块说明：[modules.md](modules.md)
- 验收清单：[messaging-acceptance.md](messaging-acceptance.md)
- 样例说明：[samples.md](samples.md)

## 已经支持

- `nexary-messaging-api`
- Kafka / RocketMQ / Redis queue / Disruptor / ActiveMQ Classic
- 统一 envelope、serializer、interceptor、retry、重复消费保护抽象

## 版本与接入入口

先按你的 Spring Boot 和 JDK 版本选入口。当前开发版本示例统一使用 `0.11.0`；发布到 Maven Central 后，把示例中的版本替换成最新 release。

| Spring Boot | JDK | Messaging 状态 | Starter 模式 | 单 provider 模式 |
| --- | --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | 当前已验证主线 | `nexary-messaging-spring-boot-starter` | `nexary-messaging-api` + 一个 provider runtime 依赖 |
| Spring Boot 2.7 | Java 8+ | Redis-only provider / starter 当前已验证；Disruptor/Kafka/RocketMQ/ActiveMQ Classic 待独立验证 | `nexary-messaging-spring-boot2-starter` | `nexary-messaging-api` + `nexary-messaging-redis-spring-boot2` |
| Spring Boot 4.1 | Java 21 作为 Nexary 主要验证运行时 | 按 provider 已验证；starter 仅 Nexary 层 core | `nexary-messaging-spring-boot4-starter` + 恰好一个 Boot4 provider | `nexary-messaging-api` + 一个 Boot4 provider runtime 依赖 |

Spring Boot 3.3 / Java 17+ starter 模式：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    // 使用 Nexary BOM 锁定当前已验证的 Boot3 / Java17+ Messaging 依赖版本。
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")

    // 当前已验证组合：Spring Boot 3.3 + Java 17+。
    // 这个 starter 聚合 Messaging API 和当前 provider 自动配置。
    // 用 nexary.messaging.provider 选择 disruptor / redis / kafka / rocketmq / activemq-classic。
    implementation 'com.aweimao:nexary-messaging-spring-boot-starter'
}
```

`nexary-messaging-spring-boot-starter` 是当前 Spring Boot 3.3 / Java 17+ 主线入口，不要把它用于 Boot2 或 Boot4。

Spring Boot 2.7 / Java 8+ 当前只验证 Redis-only Messaging starter：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    implementation "com.aweimao:nexary-messaging-spring-boot2-starter:${nexaryVersion}"
}
```

推荐配置：

```yaml
nexary:
  messaging:
    provider: redis
    redis:
      enabled: true
```

Spring Boot 4.1 的 Messaging starter 只提供 Nexary 层 core 自动配置。使用时必须显式增加一个 provider artifact；不要把四个 provider 都放进同一个 Boot4 starter classpath：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    implementation "com.aweimao:nexary-messaging-spring-boot4-starter:${nexaryVersion}"

    // 恰好选择一个 provider。
    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot4:${nexaryVersion}"
}
```

可选择的 Boot4 provider artifactId：`nexary-messaging-disruptor-spring-boot4`、`nexary-messaging-redis-spring-boot4`、`nexary-messaging-kafka-spring-boot4`、`nexary-messaging-rocketmq-spring-boot4`。

Spring Boot 4 官方最低 JDK 以 Spring 官方文档为准；这里的 Java 21 是 Nexary 对 Boot4 线的主要验证运行时。Boot4 Messaging 不声明 all-provider aggregate starter readiness。

单 provider 模式适合不想引入聚合 starter、只想引入一个具体 provider 的服务。业务代码仍只依赖 Nexary messaging API，不直接 import Kafka、RocketMQ、Redis、Disruptor、JMS 或 ActiveMQ 原生类型。

Spring Boot 3.3 / Java 17+ 单 provider 模式按 provider 选择。每个代码块都是可直接复制的完整依赖入口。

| Provider | ArtifactId |
| --- | --- |
| Disruptor | `nexary-messaging-disruptor` |
| Redis queue | `nexary-messaging-redis` |
| Kafka | `nexary-messaging-kafka` |
| RocketMQ | `nexary-messaging-rocketmq` |
| ActiveMQ Classic | `nexary-messaging-activemq-classic` |

Disruptor：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-disruptor'
}
```

Redis queue：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-redis'
}
```

Kafka：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-kafka'
}
```

RocketMQ：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-rocketmq'
}
```

ActiveMQ Classic：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-activemq-classic'
}
```

Spring Boot 2.7 / Java 8+ 的单 provider 模式当前只验证 Redis-only：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    implementation "com.aweimao:nexary-messaging-api:${nexaryVersion}"
    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot2:${nexaryVersion}"
}
```

Spring Boot 4.1 / Java 21 主要验证运行时的单 provider 模式：

```gradle
def nexaryVersion = "0.11.0"

dependencies {
    implementation "com.aweimao:nexary-messaging-api:${nexaryVersion}"

    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot4:${nexaryVersion}"
}
```

Provider 运行时选择：

| Provider | Runtime 依赖 | 配置选择 | 外部依赖 | 说明 |
| --- | --- | --- | --- | --- |
| Disruptor | `nexary-messaging-disruptor` | `nexary.messaging.provider=disruptor` | 无 | 进程内 ring-buffer，适合本地事件分发 |
| Redis queue | `nexary-messaging-redis` | `nexary.messaging.provider=redis` | Redis + Spring Redis 连接工厂 | 轻量 ready / processing / ack queue，不等同 Kafka/RocketMQ |
| Redis queue for Boot2 / Java8+ | `nexary-messaging-redis-spring-boot2` | `nexary.messaging.provider=redis` | Redis + Spring Data Redis 2.7 连接工厂 | Boot2/JDK8 当前唯一已验证 Messaging provider |
| Disruptor for Boot4 / Java21 validation runtime | `nexary-messaging-disruptor-spring-boot4` | `nexary.messaging.provider=disruptor` | 无 | Boot4 线每次只引入一个 provider |
| Redis queue for Boot4 / Java21 validation runtime | `nexary-messaging-redis-spring-boot4` | `nexary.messaging.provider=redis` | Redis + Spring Data Redis 4.1 连接工厂 | Boot4 线每次只引入一个 provider |
| Kafka for Boot4 / Java21 validation runtime | `nexary-messaging-kafka-spring-boot4` | `nexary.messaging.provider=kafka` | Kafka broker | Boot4 线每次只引入一个 provider |
| RocketMQ for Boot4 / Java21 validation runtime | `nexary-messaging-rocketmq-spring-boot4` | `nexary.messaging.provider=rocketmq` | RocketMQ NameServer/Broker | Boot4 线每次只引入一个 provider |
| Kafka | `nexary-messaging-kafka` | `nexary.messaging.provider=kafka` | Kafka broker | Nexary 负责 Nexary 层 publish/consume/retry/dedup 映射 |
| RocketMQ | `nexary-messaging-rocketmq` | `nexary.messaging.provider=rocketmq` | RocketMQ NameServer/Broker | Nexary 负责 Nexary 层 publish/consume/retry/dedup 映射 |
| ActiveMQ Classic | `nexary-messaging-activemq-classic` | `nexary.messaging.provider=activemq-classic` | ActiveMQ Classic broker | Nexary topic 映射为 JMS queue 名称；Artemis 不包含在这个 artifact 里 |

## Messaging publish 治理

`0.7.x` 给 publish 路径补齐本地治理说明。接入 starter 后，publish 使用稳定资源：

| 字段 | 值 |
| --- | --- |
| `kind` | `messaging` |
| `name` | `message-publish` |
| `operation` | `publish` |
| `provider` | `disruptor` / `redis` / `kafka` / `rocketmq` / `activemq_classic` |

可以先用下面的策略限制 publish 启动次数和并发：

```yaml
nexary:
  governance:
    resources:
      message-publish:
        kind: messaging
        name: message-publish
        operation: publish
        max-requests-per-window: 50
        rate-limit-window: 1s
        max-concurrency: 16
```

publish 会传递 `nexary-deadline-epoch-millis`。如果调用进入 provider 前 deadline 已经过期，返回会是失败的 `MessagePublishResult`，`result.status` 为 `FAILED`，`result.detail` 会说明 publish deadline 已过期。

用样例确认：

```bash
./gradlew :nexary-samples:nexary-sample-messaging:run
curl -s -X POST http://localhost:8082/app-error-logs \
  -H 'Content-Type: application/json' \
  -d '{"appId":"billing","messageId":"m-1001","level":"ERROR","message":"payment timeout"}'
curl -s http://localhost:8082/app-error-logs
```

先看 POST 返回的 `result.status`；再看 GET 返回的 `published[].publishStatus`、`published[].providerMessageId`、`published[].detail`、`consumed[]`。切到 Redis / Kafka / RocketMQ / ActiveMQ Classic 时，curl 不变，只换启动 profile：

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=redis'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=kafka'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=rocketmq'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=activemq-classic'
```

这只保护当前 JVM 发起的 publish 调用，不共享跨实例窗口，不做 broker 级熔断，也不会自动切换 provider。

## 限制

- 每个服务在 `0.1.x` 建议只启用一个出站 provider
- starter selector sample 是主要参考方向，综合演示 只负责展示 API 手感
- 重复消费保护是 messaging 的主验收项之一，不是可选点缀

## 失败语义

Messaging 的失败处理由 Nexary consume path 统一收口，不要求业务代码接触 Kafka、RocketMQ、Redis queue、Disruptor、JMS 或 ActiveMQ Classic 的原生重试对象。

- `MessageRetryPolicy`：Nexary 层 有界重试策略，包含最大尝试次数、初始延迟、backoff 策略、backoff 上限。
- `MessageDeadLetterPublisher`：Nexary 层 终态失败发布接口。
- `MessageDeadLetterRecord`：重试耗尽后的终态失败记录，包含 message id、topic、key、consumer group、attempts、错误类型和错误信息。
- `MessageConsumeExecutor`：只在 handler 成功或 terminal record 写入成功后完成 dedup；handler 失败、DLQ 写入失败不会产生 false success dedup。

Provider 映射边界：

- Disruptor：进程内按 Nexary backoff 重新投递，耗尽后写 terminal record。
- Redis queue：消息先从 ready list 原子转入 processing list；handler 成功、重复消息或 terminal record 写入成功后 ack 并移出 processing。`RETRY` 按 Nexary backoff 放回 ready；processing lease 过期后由 stale recovery 放回 ready。
- Kafka：`RETRY` 时不提交 offset 并 seek 当前记录；成功、重复或 terminal record 后提交 offset。
- RocketMQ：`RETRY` 时返回 reconsume；成功、重复或 terminal record 后确认消费成功；批量消息里任一条需要 retry 时本批次会走 RocketMQ reconsume。
- ActiveMQ Classic：Nexary topic 映射为 JMS queue 名称；`RETRY` 时调用 `Session.recover()` 触发 broker 重新投递，成功、重复或 terminal record 后执行 client acknowledge。当前不声明 ActiveMQ topic/pub-sub 或 Artemis 支持。

文档只能描述 bounded retry、terminal failure record、duplicate-consumption protection，不声明 exactly-once、全局有序或分布式事务。

Redis queue 仍是轻量队列，不等同 Kafka / RocketMQ。它提供 ready / processing / ack / retry / stale recovery 的消费状态模型，但不提供 broker 级事务、严格顺序、跨消费者协调或 exactly-once。关键配置：

- `queue-prefix`：ready list key 前缀。
- `processing-prefix`：processing list key 前缀，按 topic 和 consumer group 隔离。
- `processing-lease-prefix`：processing lease key 前缀，用于判断 stale message。
- `visibility-timeout`：processing lease 有效期；超时后消息可被 recovery 放回 ready。
- `processing-recovery-interval`：订阅 worker 扫描并恢复 stale processing 消息的间隔。
- `deduplication-prefix` / `deduplication-ttl`：重复消费保护 key，不是 exactly-once 证明。

## 观测事件与指标

Messaging 复用 `nexary-core` 的 Nexary 层 observation foundation：能力代码只发布 `NexaryObservationEvent`，不在 public API 或业务样例里暴露 Micrometer、Actuator、Kafka、RocketMQ、Redis 或 Disruptor 原生类型。需要接入 Micrometer 时，引入 `nexary-observation-micrometer-spring-boot-starter`；bridge 只在 Spring Boot 集成层把 Nexary 层 event 映射成指标。

推荐指标名：

- `nexary.messaging.operation.total`：按事件计数。
- `nexary.messaging.operation.duration`：按事件持续时间统计；当前多数 messaging event 是边界事件，duration 主要用于后续 bridge 扩展。

事件 operation：

| Operation | 含义 |
| --- | --- |
| `publish` | provider publish 入口 |
| `consume` | provider consume 结果 |
| `handler` | 业务 handler success/failure |
| `retry.schedule` | Nexary retry policy 安排重试 |
| `deadletter.publish` | terminal dead-letter record 发布 |
| `dedup.claim` | duplicate-consumption claim success/duplicate/failure |
| `provider.ack` | Redis processing ack |
| `provider.requeue` | Redis processing requeue |
| `provider.recovery` | Redis stale processing recovery |
| `provider.commit` | Kafka commit boundary |
| `provider.seek` | Kafka retry seek boundary |
| `provider.consume_status` | RocketMQ consume status boundary |
| `provider.recover` | ActiveMQ Classic session recovery boundary |
| `dispatch` | Disruptor dispatch boundary |

允许的有界 tag：

- `capability`：固定为 `messaging`。
- `provider`：`core`、`disruptor`、`redis`、`kafka`、`rocketmq`、`activemq_classic`。
- `outcome`：`success`、`failure`、`retry`、`duplicate`、`dead_letter`、`noop`。
- `retry_attempt_bucket`：`none`、`1`、`2_3`、`4_plus`。
- `terminal_status`：例如 `retry_exhausted`。
- `failure_category`：`none`、`application`、`timeout`、`system`。
- `boundary`：provider 边界，例如 `commit_offset`、`seek_current`、`consume_success`、`reconsume_later`。

禁止作为 tag：

- message id、payload、默认 raw topic、默认 raw consumer group
- exception message、stack trace
- 任意用户输入或高基数字段

Provider 边界：

- Redis queue 会发布 ack、requeue、recovery 边界事件；它仍是 lightweight queue，不提供 broker 事务或 exactly-once。
- Kafka adapter 只发布 Nexary 层的 commit/seek boundary，不替代生产级 consumer container 指标。
- RocketMQ adapter 只发布 Nexary 层的 consume status boundary。
- ActiveMQ Classic adapter 只发布 Nexary 层的 publish/consume/ack/recover boundary，不替代 broker 监控。
- Disruptor 只代表进程内 publish/dispatch，不是分布式 broker 指标。

Dashboard 示例：

- Messaging 总览：按 `provider` + `operation` 看 `nexary.messaging.operation.total`。
- 失败面：按 `provider` + `failure_category` 看 handler failure、publish failure、dead-letter publish failure。
- 重试压力：按 `provider` + `retry_attempt_bucket` 看 `retry.schedule`。
- 终态失败：按 `provider` + `terminal_status` 看 `deadletter.publish`。
- Redis queue 健康：看 `provider.ack`、`provider.requeue`、`provider.recovery` 的比例。

观测事件只是运行信号，不是审计日志、可靠投递证明、exactly-once 证明、全局有序证明或分布式事务证明。

## 当前 sample 状态

`nexary-sample-messaging` 是 messaging 的主参考样例：

- `disruptor` profile：默认可运行，零外部依赖
- `redis` profile：可配合本仓库 Redis 中间件联调
- `kafka` profile：可配合本仓库 Kafka 中间件联调
- `rocketmq` profile：可配合本仓库 RocketMQ 中间件联调
- `activemq-classic` profile：连接本地 ActiveMQ Classic broker，默认 `tcp://127.0.0.1:61616`

样例发送入口使用 facade 结构，但 facade 里发送消息只调用 `NexaryMessageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message)` 这一类业务友好 API。消费入口是业务 `NexaryMessageHandler` + `@NexaryMessageListener`，topic/group 用业务常量定义。订阅注册和 provider 加载由 Nexary messaging starter / provider auto-configuration 完成。业务代码只依赖 `org.nexary.messaging.*` 与 sample DTO；Kafka / RocketMQ / Redis / Disruptor 的切换通过 `nexary.messaging.provider` 和 provider 配置完成。

关键复制路径：

- 发送入口：`org.nexary.samples.messaging.facade`
- HTTP/测试触发：`org.nexary.samples.messaging.api`
- 公共 inbox、诊断：`org.nexary.samples.messaging.domain`
- 业务消费入口：`org.nexary.samples.messaging.consumer`
- Provider 接线：由 `nexary-messaging-spring-boot-starter` 或具体 provider dependency / auto-configuration 提供，不复制到业务代码

不用 starter 的依赖方式 样例按 provider 拆分：

- `nexary-sample-messaging-spi-disruptor`
- `nexary-sample-messaging-spi-redis`
- `nexary-sample-messaging-spi-kafka`
- `nexary-sample-messaging-spi-rocketmq`
- `nexary-sample-messaging-spi-activemq-classic`

## 推荐接入顺序

1. 先看 API 和 provider 边界
2. 再看 starter selector 样例方向
3. 再看消息能力独立验收清单
4. 需要真实中间件联调时，按 [本地验证指南](verification.md) 运行对应命令
