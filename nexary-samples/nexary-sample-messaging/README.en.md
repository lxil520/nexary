# nexary-sample-messaging

Starter selector adoption sample for Nexary messaging, focused on simple business usage.

## Runnable profiles

- `disruptor`: default, no external middleware, shows publish, consume, and dedup
- `redis`: real Redis queue profile for local middleware validation
- `kafka`: real Kafka broker profile for local middleware validation
- `rocketmq`: real RocketMQ broker profile for local middleware validation

## Main Adoption Mode: Starter Selector

Business code depends on `nexary-messaging-spring-boot-starter` and the `org.nexary.messaging.*` API. Switching providers means changing `nexary.messaging.provider` and provider settings, not facade, controller, or consumer code.

Main `build.gradle` dependency:

```gradle
implementation project(':nexary-boot:nexary-messaging-spring-boot-starter')
```

The Redis profile also needs Spring Boot's Redis connection factory. Kafka and RocketMQ clients are aggregated by the messaging starter.

The sending side stays as a simple business facade call:

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

## Structure

- `org.nexary.samples.messaging.app`: application entry
- `org.nexary.samples.messaging.api`: HTTP/test trigger edge, depending only on the sample facade
- `org.nexary.samples.messaging.facade`: copyable facade/use-case code, depending only on Nexary messaging API
- `org.nexary.samples.messaging.domain`: business message, topic constants, and a local business inbox
- `org.nexary.samples.messaging.consumer`: business consumer entry, implementing Nexary `NexaryMessageHandler` and annotated with `@NexaryMessageListener`

The sample does not contain Kafka, RocketMQ, Redis queue, or Disruptor factory, listener container, or configuration loading classes. Provider selection and subscription registration are loaded by the Nexary messaging starter / provider auto-configuration.

## Copy Path and Run Commands

| Provider | Selector | Business copy path | Config file | Prerequisite | Run command |
| --- | --- | --- | --- | --- | --- |
| Disruptor | `nexary.messaging.provider=disruptor` | `api` + `facade` + `domain` + `consumer` | `application-disruptor.yml` | no external middleware | `./gradlew :nexary-samples:nexary-sample-messaging:run` |
| Redis queue | `nexary.messaging.provider=redis` | `api` + `facade` + `domain` + `consumer` | `application-redis.yml` | Redis from `./scripts/middleware/up.sh` | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=redis'` |
| Kafka | `nexary.messaging.provider=kafka` | `api` + `facade` + `domain` + `consumer` | `application-kafka.yml` | Kafka from `./scripts/middleware/up.sh` | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=kafka'` |
| RocketMQ | `nexary.messaging.provider=rocketmq` | `api` + `facade` + `domain` + `consumer` | `application-rocketmq.yml` | RocketMQ from `./scripts/middleware/up.sh` | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=rocketmq'` |

## Failure Semantics

When a business consumer throws, sample code does not handle provider-native retry objects. Nexary messaging uses `MessageRetryPolicy` to control `retry-max-attempts`, `retry-initial-delay`, `retry-backoff-strategy`, and `retry-max-backoff`. After retry exhaustion, Nexary writes a provider-neutral `MessageDeadLetterRecord`, recorded by the default in-memory `MessageDeadLetterPublisher`.

Provider mappings:

- Disruptor: re-publishes in process after Nexary backoff, then writes a terminal record.
- Redis queue: atomically moves a message from ready to processing; ack happens after success, duplicate detection, or terminal record publication. `RETRY` requeues to ready after backoff, and expired processing leases can be recovered to ready.
- Kafka: does not commit the offset on `RETRY` and seeks back to the current record; commits after success, duplicate, or terminal record.
- RocketMQ: returns reconsume on `RETRY`; acknowledges success after success, duplicate, or terminal record.

This is not an exactly-once, global ordering, or distributed transaction guarantee. It is bounded retry, terminal record, and duplicate-consumption protection in the Nexary consume path.

Redis queue remains a lightweight queue. `application-redis.yml` exposes `queue-prefix`, `processing-prefix`, `processing-lease-prefix`, `visibility-timeout`, `processing-recovery-interval`, and `deduplication-*` to make the ready / processing / ack / stale recovery boundary explicit for local validation.

## SPI / Provider Isolation Mode

If you do not want the aggregate starter, use the provider-split SPI samples:

- `nexary-sample-messaging-spi-disruptor`
- `nexary-sample-messaging-spi-redis`
- `nexary-sample-messaging-spi-kafka`
- `nexary-sample-messaging-spi-rocketmq`

Those modules depend on `nexary-messaging-api` plus exactly one provider module. Business code still uses only the Nexary messaging API; switching providers means changing the provider dependency and configuration, not facade/controller/consumer code. SPI sample packages are provider-scoped, for example `org.nexary.samples.messaging.spi.kafka.*`.

## Run

```bash
./gradlew :nexary-samples:nexary-sample-messaging:run
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=redis'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=kafka'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=rocketmq'
./gradlew :nexary-samples:nexary-sample-messaging:test
```

Real middleware validation:

```bash
./scripts/middleware/up.sh
NEXARY_RUN_INFRA_TESTS=true \
NEXARY_INFRA_REDIS_HOST=127.0.0.1 \
NEXARY_INFRA_REDIS_PORT=16379 \
NEXARY_INFRA_KAFKA_BOOTSTRAP=127.0.0.1:19092 \
NEXARY_INFRA_ROCKETMQ_NAMESRV=127.0.0.1:19876 \
./gradlew :nexary-messaging:nexary-messaging-redis:test \
  :nexary-messaging:nexary-messaging-kafka:test \
  :nexary-messaging:nexary-messaging-rocketmq:test
```

All four profiles are single-provider modes.

The built-in consumer subscribes to the business constant `MessagingSampleTopics.APP_ERROR_LOG`, not to a provider setting. The topic uses the RocketMQ-safe value `sample_messaging_app_error_log`, so switching Kafka / RocketMQ / Redis / Disruptor does not change business code.

## Boundaries

- no automatic multi-provider routing
- no exactly-once claim
- no cross-provider transaction consistency
- no broker high availability or fallback chain

RocketMQ profile `auto-create-topic` is only for the local sample. Production topic administration should live outside application startup.
