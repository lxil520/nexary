# nexary-sample-messaging-spi-kafka

Messaging non-starter dependency sample for Kafka. It depends only on `nexary-messaging-api` and `nexary-messaging-kafka`.

Business code still uses only `org.nexary.messaging.*` and does not create KafkaProducer or KafkaConsumer directly. To switch providers, use the corresponding provider SPI sample module and change dependencies, not facade/controller/consumer code.

Sample business package: `org.nexary.samples.messaging.spi.kafka.*`. The consumer only implements Nexary `NexaryMessageHandler` and uses `@NexaryMessageListener`; provider loading, and subscription registration are supplied by the Nexary Kafka provider auto-configuration.

Failure handling is also provided by Nexary: `MessageRetryPolicy` controls bounded retries, and exhausted messages are recorded as `MessageDeadLetterRecord`. Business consumers do not handle Kafka native objects and this is not an exactly-once claim.

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
