# nexary-sample-messaging

这是消息能力的 starter selector 引入样例，重点展示业务代码如何简单使用 Nexary messaging。

## 当前可运行 profile

- `disruptor`：默认 profile，零外部依赖，演示发送、消费、去重
- `redis`：真实 Redis queue profile，配合本仓库中间件脚本联调
- `kafka`：真实 Kafka broker profile，配合本仓库中间件脚本联调
- `rocketmq`：真实 RocketMQ broker profile，配合本仓库中间件脚本联调
- `activemq-classic`：ActiveMQ Classic queue profile，连接本地 ActiveMQ Classic broker

## 版本选择

样例业务代码只使用 Nexary messaging API，因此 Boot3、Boot2 Redis-only、Boot4 provider-by-provider 的差异只在依赖和配置入口。当前开发版本示例统一使用 `0.5.0`；发布到 Maven Central 后，把示例中的版本替换成最新 release。

| Spring Boot | JDK | 样例依赖入口 | Provider 边界 |
| --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | `nexary-messaging-spring-boot-starter` | starter selector 选择 `disruptor` / `redis` / `kafka` / `rocketmq` / `activemq-classic` |
| Spring Boot 2.7 | Java 8+ | `nexary-messaging-spring-boot2-starter` | Redis-only 已验证 |
| Spring Boot 4.1 | Java 21 作为 Nexary 主要验证运行时 | `nexary-messaging-spring-boot4-starter` + 恰好一个 `*-spring-boot4` provider | provider-by-provider；不提供 all-provider aggregate starter |

## 主接入方式：starter selector

业务代码只依赖 Nexary messaging API。切换 provider 时改 `nexary.messaging.provider`、对应 provider 配置和依赖入口，不改 facade、controller、consumer 代码。

Spring Boot 3.3 / Java 17+ 主依赖：

```gradle
def nexaryVersion = "0.5.0"

dependencies {
    // 发布后使用 Nexary BOM 锁定当前已验证的 Boot3 / Java17+ Messaging 版本。
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")

    // 本仓库样例用 project(':nexary-boot:nexary-messaging-spring-boot-starter') 开发；
    // 用户项目复制时使用下面这个当前已验证 artifactId。
    implementation 'com.aweimao:nexary-messaging-spring-boot-starter'
}
```

Spring Boot 2.7 / Java 8+ Redis-only starter：

```gradle
def nexaryVersion = "0.5.0"

dependencies {
    implementation "com.aweimao:nexary-messaging-spring-boot2-starter:${nexaryVersion}"
}
```

Spring Boot 4.1 / Java 21 主要验证运行时 starter：

```gradle
def nexaryVersion = "0.5.0"

dependencies {
    implementation "com.aweimao:nexary-messaging-spring-boot4-starter:${nexaryVersion}"

    // 恰好选择一个 provider。
    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot4:${nexaryVersion}"
}
```

可选择的 Boot4 provider artifactId：`nexary-messaging-disruptor-spring-boot4`、`nexary-messaging-redis-spring-boot4`、`nexary-messaging-kafka-spring-boot4`、`nexary-messaging-rocketmq-spring-boot4`。

Boot4 starter 只提供 Nexary 层 core 自动配置，不聚合所有 provider。Spring Boot 4 官方最低 JDK 以 Spring 官方文档为准；这里的 Java 21 是 Nexary 对 Boot4 线的主要验证运行时。

如果使用 Redis profile，需要 Spring Redis 连接工厂；Kafka/RocketMQ/ActiveMQ Classic profile 需要对应 broker。

发送侧保持简单的业务 facade 调用：

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

## 结构

- `org.nexary.samples.messaging.app`：启动类
- `org.nexary.samples.messaging.api`：HTTP/测试触发入口，只依赖 sample facade
- `org.nexary.samples.messaging.facade`：可复制到业务侧的 facade/use case，只依赖 Nexary messaging API
- `org.nexary.samples.messaging.domain`：业务消息、topic 常量和本地查看用的业务收件箱
- `org.nexary.samples.messaging.consumer`：业务消费入口，只实现 Nexary `NexaryMessageHandler` 并标注 `@NexaryMessageListener`

样例里没有 Kafka、RocketMQ、Redis queue、Disruptor、ActiveMQ Classic 的 factory、listener container 或配置加载类。provider 选择和订阅注册由 Nexary messaging starter / provider auto-configuration 加载。

## Copy path 与运行方式

| Provider | Selector | 业务复制路径 | 配置文件 | 前置依赖 | 运行命令 |
| --- | --- | --- | --- | --- | --- |
| Disruptor | `nexary.messaging.provider=disruptor` | `api` + `facade` + `domain` + `consumer` | `application-disruptor.yml` | 无外部中间件 | `./gradlew :nexary-samples:nexary-sample-messaging:run` |
| Redis queue | `nexary.messaging.provider=redis` | `api` + `facade` + `domain` + `consumer` | `application-redis.yml` | `./scripts/middleware/up.sh` 中的 Redis | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=redis'` |
| Kafka | `nexary.messaging.provider=kafka` | `api` + `facade` + `domain` + `consumer` | `application-kafka.yml` | `./scripts/middleware/up.sh` 中的 Kafka | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=kafka'` |
| RocketMQ | `nexary.messaging.provider=rocketmq` | `api` + `facade` + `domain` + `consumer` | `application-rocketmq.yml` | `./scripts/middleware/up.sh` 中的 RocketMQ | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=rocketmq'` |
| ActiveMQ Classic | `nexary.messaging.provider=activemq-classic` | `api` + `facade` + `domain` + `consumer` | `application-activemq-classic.yml` | 本地 ActiveMQ Classic broker：`tcp://127.0.0.1:61616` | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=activemq-classic'` |

## 失败语义

业务 consumer 抛出异常时，样例代码不处理 provider 原生 retry。Nexary messaging 通过 `MessageRetryPolicy` 统一控制 `retry-max-attempts`、`retry-initial-delay`、`retry-backoff-strategy`、`retry-max-backoff`。重试耗尽后写入 Nexary 层 `MessageDeadLetterRecord`，默认由 `MessageDeadLetterPublisher` 的内存实现记录。

各 provider 的底层映射：

- Disruptor：按 Nexary backoff 在进程内重新投递，耗尽后写 terminal record。
- Redis queue：消息从 ready list 原子转入 processing list；成功、重复或 terminal record 后 ack。`RETRY` 按 backoff 放回 ready，processing lease 超时后可被 recovery 放回 ready。
- Kafka：`RETRY` 时不提交 offset 并 seek 回当前记录；成功、重复或 terminal record 后提交 offset。
- RocketMQ：`RETRY` 时返回 reconsume，成功、重复或 terminal record 后确认消费成功。
- ActiveMQ Classic：Nexary topic 映射为 JMS queue 名称；`RETRY` 时通过 `Session.recover()` 重新投递，成功、重复或 terminal record 后执行 client acknowledge。

这不是 exactly-once、全局有序或分布式事务承诺；它只是 Nexary consume path 的有界重试、终态记录和重复消费保护。

Redis queue 仍是轻量队列。`application-redis.yml` 暴露 `queue-prefix`、`processing-prefix`、`processing-lease-prefix`、`visibility-timeout`、`processing-recovery-interval` 和 `deduplication-*`，用于本地联调 ready / processing / ack / stale recovery 边界。

## SPI / provider 隔离模式

如果不想引入聚合 starter，可以使用 provider 拆分样例：

- `nexary-sample-messaging-spi-disruptor`
- `nexary-sample-messaging-spi-redis`
- `nexary-sample-messaging-spi-kafka`
- `nexary-sample-messaging-spi-rocketmq`
- `nexary-sample-messaging-spi-activemq-classic`

这些模块只依赖 `nexary-messaging-api` 和一个具体 provider 模块。业务代码仍然只使用 Nexary messaging API；切换 provider 是切换依赖模块和配置，不改 facade/controller/consumer。SPI 样例包名按 provider 隔离，例如 `org.nexary.samples.messaging.spi.kafka.*`。

发布后 SPI/provider 依赖形态：

Spring Boot 3.3 / Java 17+ SPI/provider 依赖四选一，每个代码块都可以直接复制。

Disruptor：

```gradle
def nexaryVersion = "0.5.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-disruptor'
}
```

Redis queue：

```gradle
def nexaryVersion = "0.5.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-redis'
}
```

Kafka：

```gradle
def nexaryVersion = "0.5.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-kafka'
}
```

RocketMQ：

```gradle
def nexaryVersion = "0.5.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-rocketmq'
}
```

ActiveMQ Classic：

```gradle
def nexaryVersion = "0.5.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-activemq-classic'
}
```

Spring Boot 4.1 / Java 21 主要验证运行时的 SPI/provider 形态：

```gradle
def nexaryVersion = "0.5.0"

dependencies {
    implementation "com.aweimao:nexary-messaging-api:${nexaryVersion}"

    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot4:${nexaryVersion}"
}
```

## 跑法

默认 Disruptor：

```bash
./gradlew :nexary-samples:nexary-sample-messaging:run
```

Redis queue：

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=redis'
```

Kafka：

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=kafka'
```

RocketMQ：

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=rocketmq'
```

ActiveMQ Classic：

```bash
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=activemq-classic'
```

基础测试：

```bash
./gradlew :nexary-samples:nexary-sample-messaging:test
```

真实中间件验证：

```bash
./scripts/middleware/up.sh
NEXARY_RUN_INFRA_TESTS=true \
NEXARY_INFRA_REDIS_HOST=127.0.0.1 \
NEXARY_INFRA_REDIS_PORT=16379 \
NEXARY_INFRA_KAFKA_BOOTSTRAP=127.0.0.1:19092 \
NEXARY_INFRA_ROCKETMQ_NAMESRV=127.0.0.1:19876 \
./gradlew :nexary-messaging:nexary-messaging-redis:test \
  :nexary-messaging:nexary-messaging-kafka:test \
  :nexary-messaging:nexary-messaging-rocketmq:test
```

## 接口

- `POST /app-error-logs`
- `GET /app-error-logs`

消费端订阅的是业务常量 `MessagingSampleTopics.APP_ERROR_LOG`，不是 provider 配置项。这个 topic 使用 RocketMQ 也合法的 `sample_messaging_app_error_log`，因此 Kafka / RocketMQ / Redis / Disruptor 切换时不改业务代码。

## 边界

- 多 provider 自动切换
- exactly-once 叙述
- 跨 provider 事务一致性
- broker 高可用或 fallback chain

四种 profile 都是单 provider 模式。RocketMQ profile 的 `auto-create-topic` 仅服务本地样例，生产 topic 管理应放在应用启动之外。
