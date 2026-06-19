# nexary-sample-messaging-spi-kafka

This sample skips the starter and adds the Messaging API plus the Kafka provider by hand.

Business code uses only `org.nexary.messaging.*` and does not create KafkaProducer or KafkaConsumer directly. If you later move to Redis, RocketMQ, or Disruptor, you usually change dependencies and configuration, not controllers, sending code, or consumers.

Sample business package: `org.nexary.samples.messaging.spi.kafka.*`. The consumer implements `NexaryMessageHandler` and uses `@NexaryMessageListener`; provider loading, subscription registration, and duplicate protection are handled by the Nexary Kafka provider.

Failure handling stays out of the consumer: `MessageRetryPolicy` controls retries, and exhausted messages are recorded as `MessageDeadLetterRecord`. This sample does not handle Kafka native objects and does not claim exactly-once delivery.

Core sending usage:

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging-spi-kafka:run
```

Port: `8094`.

Endpoints:

- `POST /app-error-logs`
- `GET /app-error-logs`
