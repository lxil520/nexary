# nexary-sample-messaging-spi-redis

这是 Messaging 的 不用 starter 的依赖方式 引入样例：只引入 `nexary-messaging-api` 和 `nexary-messaging-redis`。

业务代码仍然只使用 `org.nexary.messaging.*`，不直接创建 Redis queue。切换到其它 provider 时，新建/使用对应 provider SPI 样例模块并调整依赖，不改 facade、controller、consumer。

样例业务包：`org.nexary.samples.messaging.spi.redis.*`。consumer 只实现 Nexary `NexaryMessageHandler` 并标注 `@NexaryMessageListener`；provider 加载、订阅注册和重复消费保护由 Nexary Redis provider 自动配置完成。

失败处理同样由 Nexary 统一完成：`MessageRetryPolicy` 控制有界重试，耗尽后写入 `MessageDeadLetterRecord`。业务 consumer 不处理 Redis queue 原生对象，也不声明 exactly-once。

Redis queue 使用 ready list -> processing list -> ack 的状态模型。`application.yml` 里可以看到 `queue-prefix`、`processing-prefix`、`processing-lease-prefix`、`visibility-timeout`、`processing-recovery-interval` 和 `deduplication-*` 配置。它支持 stale processing recovery，但不提供 broker 事务、全局有序或 exactly-once。

发送侧核心用法：

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging-spi-redis:run
```

端口：`8093`。

接口：

- `POST /app-error-logs`
- `GET /app-error-logs`
