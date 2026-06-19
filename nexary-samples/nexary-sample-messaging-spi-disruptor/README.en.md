# nexary-sample-messaging-spi-disruptor

Messaging non-starter dependency sample for Disruptor. It depends only on `nexary-messaging-api` and `nexary-messaging-disruptor`.

Business code still uses only `org.nexary.messaging.*` and does not create a Disruptor bus directly. To switch providers, use the corresponding provider SPI sample module and change dependencies, not facade/controller/consumer code.

Sample business package: `org.nexary.samples.messaging.spi.disruptor.*`. The consumer only implements Nexary `NexaryMessageHandler` and uses `@NexaryMessageListener`; provider loading, and subscription registration are supplied by the Nexary Disruptor provider auto-configuration.

Failure handling is also provided by Nexary: `MessageRetryPolicy` controls bounded retries, and exhausted messages are recorded as `MessageDeadLetterRecord`. Business consumers do not handle Disruptor native objects and this is not an exactly-once claim.

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
