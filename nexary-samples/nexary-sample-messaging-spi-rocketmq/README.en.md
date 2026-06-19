# nexary-sample-messaging-spi-rocketmq

This sample skips the starter and adds the Messaging API plus the RocketMQ provider by hand.

Business code uses only `org.nexary.messaging.*` and does not create RocketMQ Producer or Consumer directly. If you later move to Kafka, Redis, or Disruptor, you usually change dependencies and configuration, not controllers, sending code, or consumers.

Sample business package: `org.nexary.samples.messaging.spi.rocketmq.*`. The consumer implements `NexaryMessageHandler` and uses `@NexaryMessageListener`; provider loading, subscription registration, and duplicate protection are handled by the Nexary RocketMQ provider.

Failure handling stays out of the consumer: `MessageRetryPolicy` controls retries, and exhausted messages are recorded as `MessageDeadLetterRecord`. This sample does not handle RocketMQ native objects and does not claim exactly-once delivery.

Core sending usage:

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging-spi-rocketmq:run
```

Port: `8095`.

Endpoints:

- `POST /app-error-logs`
- `GET /app-error-logs`
