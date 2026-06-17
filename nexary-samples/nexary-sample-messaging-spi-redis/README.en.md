# nexary-sample-messaging-spi-redis

Messaging SPI/provider dependency sample for Redis queue. It depends only on `nexary-messaging-api` and `nexary-messaging-redis`.

Business code still uses only `org.nexary.messaging.*` and does not create a Redis queue directly. To switch providers, use the corresponding provider SPI sample module and change dependencies, not facade/controller/consumer code.

Sample business package: `org.nexary.samples.messaging.spi.redis.*`. The consumer only implements Nexary `NexaryMessageHandler` and uses `@NexaryMessageListener`; provider loading, and subscription registration are supplied by the Nexary Redis provider auto-configuration.

Failure handling is also provided by Nexary: `MessageRetryPolicy` controls bounded retries, and exhausted messages are recorded as `MessageDeadLetterRecord`. Business consumers do not handle Redis queue native objects and this is not an exactly-once claim.

Redis queue uses a ready list -> processing list -> ack state model. `application.yml` shows `queue-prefix`, `processing-prefix`, `processing-lease-prefix`, `visibility-timeout`, `processing-recovery-interval`, and `deduplication-*` settings. It supports stale processing recovery, but not broker transactions, global ordering, or exactly-once delivery.

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
