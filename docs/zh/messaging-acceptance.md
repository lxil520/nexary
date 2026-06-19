# Messaging 验收清单

Messaging 的验收不以 综合演示 为准，而以独立 messaging sample、provider adapter 测试和真实中间件联调为准。

## 公共 API

- API 不暴露 Kafka、RocketMQ、Redis queue、Disruptor 原生类型。
- 发送、消费、序列化、拦截器、Nexary 层 retry policy、terminal failure record 抽象可说明。
- message id、headers、topic、key、payload 的边界清楚。
- 每个服务在 `0.1.x` 默认只选择一个出站 provider；框架不做隐式多 provider 路由。

## Provider 边界

| Provider | 当前框架能力 | 当前样例状态 | 验收重点 |
| --- | --- | --- | --- |
| Disruptor | 进程内 LMAX Disruptor provider | 默认 profile 可运行 | 发送、消费、replay、去重 |
| Redis queue | Redis List ready/processing 队列、ack/requeue/recovery 与 Redis 去重存储 | `redis` profile 可运行 | 真实 Redis 发布、消费、ack、retry、stale recovery、replay、去重 |
| Kafka | Kafka publish / consume adapter | `kafka` profile 可运行 | 真实 Kafka header 透传、listener adapter、去重 |
| RocketMQ | RocketMQ publish / consume adapter | `rocketmq` profile 可运行 | 真实 RocketMQ header 透传、listener adapter、去重 |

Disruptor 是进程内 provider，不是分布式 broker 替代品。Redis queue 是轻量队列，不等同于 Kafka 或 RocketMQ。

## 重复消费保护

- 重复消费保护是主验收项之一。
- 消费路径需要经过统一 dedup-aware 执行链。
- 样例必须提供 replay 路径，用相同 message id 再发布一次。
- replay 发布可以成功，但去重开启时不应新增第二条消费记录。
- 文档只能描述 duplicate-consumption protection，不能承诺 exactly-once。

## 失败处理

- API 必须提供 Nexary 层 `MessageRetryPolicy`，包含最大尝试次数、初始延迟、backoff 策略和 backoff 上限。
- API 必须提供 terminal failure path，例如 `MessageDeadLetterPublisher` 和 `MessageDeadLetterRecord`。
- `MessageConsumeExecutor` 负责按策略返回 `RETRY`，耗尽后写 terminal record。
- handler 成功后才完成 dedup；handler 失败或 terminal record 写入失败不能产生 false success dedup。
- terminal record 写入成功后，重复投递相同 message id 不应再次执行业务 handler。
- Disruptor / Redis queue / Kafka / RocketMQ 都必须经过同一 Nexary failure semantics。
- 文档必须写清 provider 映射限制，不声明 exactly-once、全局有序或分布式事务。

## Redis queue processing state

- Redis queue 必须使用 ready list -> processing list -> ack 的状态模型，不能只依赖 `leftPop` 后应用级重投。
- handler 成功、重复消息或 terminal record 写入成功后才 ack processing message。
- retry path 必须保留 message id 和 headers，并按 Nexary backoff 放回 ready。
- stale processing recovery 必须基于 `visibility-timeout` / processing lease 或等价机制，把超时未 ack 的消息放回 ready。
- terminal failure 对同一 message id 只能产生一次 terminal record；后续重复投递不能再次执行业务 handler。
- 真实 Redis integration test 必须能在 dirty local middleware stack 下重复运行。

## 观测与指标

- Messaging 必须复用 `nexary-core` 的 `NexaryObservationEvent` / `NexaryObservationPublisher`，不能创建 messaging-only observation foundation。
- public API 与用户可复制 sample 不得暴露 Micrometer、Actuator、Redis、Kafka、RocketMQ、Disruptor 等 native 类型。
- 事件至少覆盖 publish、consume、handler success/failure、retry scheduling、dead-letter terminal publication、dedup claim success/duplicate/failure、Redis ack/requeue/recovery、Kafka commit/seek boundary、RocketMQ consume status boundary、Disruptor publish/dispatch。
- tag 必须有界，只允许 capability、operation、provider、outcome、retry attempt bucket、terminal status、failure category、provider boundary 等低基数字段。
- 禁止把 message id、payload、raw topic、raw consumer group、exception message、stack trace 或任意用户输入放进 tag。
- 未启用 observation listener 时 no-op publisher 不应改变 messaging 行为。
- Micrometer bridge 必须证明 metric name 和 tags 有界，并且不能把 Micrometer 类型泄漏到 messaging public API 或业务样例。

## 样例

- provider-backed 样例结构清楚，不靠 综合演示 代替。
- `nexary-sample-messaging` 必须是 Spring Boot 工程。
- 发送入口使用 facade 结构，controller 只作为样例 HTTP 入口。
- 消费入口通过业务 `NexaryMessageHandler` + `@NexaryMessageListener` 进入 sample inbox；订阅注册和 provider listener 创建由 Nexary 框架完成。
- 样例包结构必须按接入职责拆分：
  - `org.nexary.samples.messaging.app`：启动类。
  - `org.nexary.samples.messaging.api`：HTTP/测试触发入口。
  - `org.nexary.samples.messaging.facade`：facade/use case。
  - `org.nexary.samples.messaging.domain`：业务消息、topic 常量和 sample inbox。
  - `org.nexary.samples.messaging.consumer`：业务消费入口。
- starter selector 样例不得包含 sample-owned provider factory、listener container、subscriber configuration 或配置加载类。
- 不用 starter 的依赖方式 样例必须按 provider 拆成独立模块，例如 `nexary-sample-messaging-spi-kafka`，包名使用 `org.nexary.samples.messaging.spi.<provider>.*`。
- provider 的 configuration、adapter、subscriber、diagnostics、provider-only fixture 必须属于 Nexary provider 模块或 provider-specific SPI 样例边界，不能放回 starter 业务样例。
- 中文说明优先，英文说明保持镜像。

## 本地验证证据

Messaging 相关变更至少应提供：

- 改动模块清单。
- 可运行 profile 和命令。
- 需要真实中间件的 profile。
- 已完成和未完成 provider 的边界。
- retry/dead-letter 语义的自检证据。
- `./gradlew check` 结果。

最小命令：

```bash
./gradlew :nexary-samples:nexary-sample-messaging:test
./gradlew :nexary-messaging:nexary-messaging-disruptor:test
./gradlew :nexary-messaging:nexary-messaging-redis:test
./gradlew :nexary-messaging:nexary-messaging-kafka:test
./gradlew :nexary-messaging:nexary-messaging-rocketmq:test
```

Broker profile 验收命令：

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=kafka'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=rocketmq'
```

Provider sample 结构验收命令：

```bash
find nexary-samples/nexary-sample-messaging/src/main/java/org/nexary/samples/messaging -maxdepth 3 -type f | sort
./gradlew :nexary-samples:nexary-sample-messaging:check
```
