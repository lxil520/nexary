# nexary-sample-messaging-spi-redis

This sample skips the starter and adds the Messaging API plus the Redis queue provider by hand.

Business code uses only `org.nexary.messaging.*` and does not create a Redis queue directly. If you later move to Kafka, RocketMQ, or Disruptor, you usually change dependencies and configuration, not controllers, sending code, or consumers.

Sample business package: `org.nexary.samples.messaging.spi.redis.*`. The consumer implements `NexaryMessageHandler` and uses `@NexaryMessageListener`; provider loading, subscription registration, and duplicate protection are handled by the Nexary Redis provider.

Failure handling stays out of the consumer: `MessageRetryPolicy` controls retries, and exhausted messages are recorded as `MessageDeadLetterRecord`. This sample does not handle Redis queue native objects and does not claim exactly-once delivery.

Redis queue uses a ready list -> processing list -> ack state model. `application.yml` shows `queue-prefix`, `processing-prefix`, `processing-lease-prefix`, `visibility-timeout`, `processing-recovery-interval`, and `deduplication-*` settings. It can move timed-out processing messages back to ready, but it does not provide broker transactions, global ordering, or exactly-once delivery.

Core sending usage:

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging-spi-redis:run
```

Port: `8093`.

Endpoints:

- `POST /app-error-logs`
- `GET /app-error-logs`
