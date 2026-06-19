# nexary-sample-messaging-spi-activemq-classic

Messaging non-starter dependency sample for ActiveMQ Classic. It depends only on `nexary-messaging-api` and `nexary-messaging-activemq-classic`.

Business code still uses only `org.nexary.messaging.*` and does not create JMS or ActiveMQ Classic objects directly. To switch providers, use the corresponding provider SPI sample module and change dependencies, not facade/controller/consumer code.

Sample business package: `org.nexary.samples.messaging.spi.activemqclassic.*`. The consumer only implements Nexary `NexaryMessageHandler` and uses `@NexaryMessageListener`; provider loading, and subscription registration are supplied by the Nexary ActiveMQ Classic provider auto-configuration.

Failure handling is also provided by Nexary: `MessageRetryPolicy` controls bounded retries, and exhausted messages are recorded as `MessageDeadLetterRecord`. Business consumers do not handle ActiveMQ Classic native objects and this is not an exactly-once claim.

Core sending usage:

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

Run a local ActiveMQ Classic broker first. The default broker URL is `tcp://127.0.0.1:61616`.

```bash
./gradlew :nexary-samples:nexary-sample-messaging-spi-activemq-classic:run
```

Port: `8098`.

Endpoints:

- `POST /app-error-logs`
- `GET /app-error-logs`
