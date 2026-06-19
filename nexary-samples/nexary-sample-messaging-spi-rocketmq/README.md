# nexary-sample-messaging-spi-rocketmq

这个样例不走 starter，只手动加入 Messaging API 和 RocketMQ provider。

业务代码只使用 `org.nexary.messaging.*`，不直接创建 RocketMQ Producer 或 Consumer。以后要换 Kafka、Redis 或 Disruptor，通常改依赖和配置就够了，controller、发送入口和 consumer 不需要跟着重写。

样例业务包：`org.nexary.samples.messaging.spi.rocketmq.*`。consumer 只实现 `NexaryMessageHandler` 并标注 `@NexaryMessageListener`；provider 加载、订阅注册和重复消费保护由 Nexary RocketMQ provider 接上。

失败处理也不用写到 consumer 里：`MessageRetryPolicy` 控制重试次数，耗尽后写入 `MessageDeadLetterRecord`。这个样例不处理 RocketMQ 原生对象，也不承诺 exactly-once。

发送侧核心用法：

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging-spi-rocketmq:run
```

端口：`8095`。

接口：

- `POST /app-error-logs`
- `GET /app-error-logs`
