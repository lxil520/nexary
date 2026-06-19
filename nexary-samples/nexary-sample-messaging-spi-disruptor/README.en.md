# nexary-sample-messaging-spi-disruptor

This sample skips the starter and adds the Messaging API plus the Disruptor provider by hand.

Business code uses only `org.nexary.messaging.*` and does not create a Disruptor bus directly. If you later move to Kafka, Redis, or RocketMQ, you usually change dependencies and configuration, not controllers, sending code, or consumers.

Sample business package: `org.nexary.samples.messaging.spi.disruptor.*`. The consumer implements `NexaryMessageHandler` and uses `@NexaryMessageListener`; provider loading, subscription registration, and duplicate protection are handled by the Nexary Disruptor provider.

Failure handling stays out of the consumer: `MessageRetryPolicy` controls retries, and exhausted messages are recorded as `MessageDeadLetterRecord`. This sample does not handle Disruptor native objects and does not claim exactly-once delivery.

Core sending usage:

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

```bash
./gradlew :nexary-samples:nexary-sample-messaging-spi-disruptor:run
```

Port: `8092`.

Endpoints:

- `POST /app-error-logs`
- `GET /app-error-logs`
