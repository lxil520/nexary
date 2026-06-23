# nexary-sample-messaging

Starter selector integration sample for Nexary messaging, focused on simple business usage.

## Runnable profiles

- `disruptor`: default, no external middleware, shows publish, consume, and dedup
- `redis`: real Redis queue profile for local middleware validation
- `kafka`: real Kafka broker profile for local middleware validation
- `rocketmq`: real RocketMQ broker profile for local middleware validation
- `activemq-classic`: ActiveMQ Classic queue profile for a local ActiveMQ Classic broker

## Version Selection

The sample business code uses only the Nexary messaging API, so Boot3, Boot2 Redis-only, and Boot4 provider-by-provider differences are dependency and configuration entry choices. Examples use the current development version `0.5.1`; after Maven Central publication, replace it with the latest release.

| Spring Boot | JDK | Sample dependency entry | Provider boundary |
| --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | `nexary-messaging-spring-boot-starter` | starter selector chooses `disruptor` / `redis` / `kafka` / `rocketmq` / `activemq-classic` |
| Spring Boot 2.7 | Java 8+ | `nexary-messaging-spring-boot2-starter` | Redis-only verified |
| Spring Boot 4.1 | Java 21 as Nexary's primary validation runtime | `nexary-messaging-spring-boot4-starter` + exactly one `*-spring-boot4` provider | provider-by-provider; no all-provider aggregate starter |

## Main Adoption Mode: Starter Selector

Business code depends only on the Nexary messaging API. Switching providers means changing `nexary.messaging.provider`, provider settings, and dependency entry, not facade, controller, or consumer code.

Spring Boot 3.3 / Java 17+ main dependency:

```gradle
def nexaryVersion = "0.5.1"

dependencies {
    // After publication, use the Nexary BOM to lock the currently verified Boot3 / Java17+ Messaging versions.
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")

    // This repository sample develops against project(':nexary-boot:nexary-messaging-spring-boot-starter');
    // user projects should copy the current verified artifactId below.
    implementation 'com.aweimao:nexary-messaging-spring-boot-starter'
}
```

Spring Boot 2.7 / Java 8+ Redis-only starter:

```gradle
def nexaryVersion = "0.5.1"

dependencies {
    implementation "com.aweimao:nexary-messaging-spring-boot2-starter:${nexaryVersion}"
}
```

Spring Boot 4.1 / Java 21 primary validation runtime starter:

```gradle
def nexaryVersion = "0.5.1"

dependencies {
    implementation "com.aweimao:nexary-messaging-spring-boot4-starter:${nexaryVersion}"

    // Choose exactly one provider.
    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot4:${nexaryVersion}"
}
```

Available Boot4 provider artifactIds: `nexary-messaging-disruptor-spring-boot4`, `nexary-messaging-redis-spring-boot4`, `nexary-messaging-kafka-spring-boot4`, and `nexary-messaging-rocketmq-spring-boot4`.

The Boot4 starter provides Nexary-level core auto-configuration only and does not aggregate all providers. The official minimum JDK for Spring Boot 4 is defined by Spring's documentation; Java 21 here is Nexary's primary validation runtime for the Boot4 line.

The Redis profile needs a Spring Redis connection factory. Kafka, RocketMQ, and ActiveMQ Classic profiles need their brokers.

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

The sample does not contain Kafka, RocketMQ, Redis queue, Disruptor, or ActiveMQ Classic factory, listener container, or configuration loading classes. Provider selection and subscription registration are loaded by the Nexary messaging starter / provider auto-configuration.

## Copy Path and Run Commands

| Provider | Selector | Business copy path | Config file | Prerequisite | Run command |
| --- | --- | --- | --- | --- | --- |
| Disruptor | `nexary.messaging.provider=disruptor` | `api` + `facade` + `domain` + `consumer` | `application-disruptor.yml` | no external middleware | `./gradlew :nexary-samples:nexary-sample-messaging:run` |
| Redis queue | `nexary.messaging.provider=redis` | `api` + `facade` + `domain` + `consumer` | `application-redis.yml` | Redis from `./scripts/middleware/up.sh` | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=redis'` |
| Kafka | `nexary.messaging.provider=kafka` | `api` + `facade` + `domain` + `consumer` | `application-kafka.yml` | Kafka from `./scripts/middleware/up.sh` | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=kafka'` |
| RocketMQ | `nexary.messaging.provider=rocketmq` | `api` + `facade` + `domain` + `consumer` | `application-rocketmq.yml` | RocketMQ from `./scripts/middleware/up.sh` | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=rocketmq'` |
| ActiveMQ Classic | `nexary.messaging.provider=activemq-classic` | `api` + `facade` + `domain` + `consumer` | `application-activemq-classic.yml` | local ActiveMQ Classic broker at `tcp://127.0.0.1:61616` | `./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=activemq-classic'` |

## Failure Semantics

When a business consumer throws, sample code does not handle provider-native retry objects. Nexary messaging uses `MessageRetryPolicy` to control `retry-max-attempts`, `retry-initial-delay`, `retry-backoff-strategy`, and `retry-max-backoff`. After retry exhaustion, Nexary writes a Nexary-level `MessageDeadLetterRecord`, recorded by the default in-memory `MessageDeadLetterPublisher`.

Provider mappings:

- Disruptor: re-publishes in process after Nexary backoff, then writes a terminal record.
- Redis queue: atomically moves a message from ready to processing; ack happens after success, duplicate detection, or terminal record publication. `RETRY` requeues to ready after backoff, and expired processing leases can be recovered to ready.
- Kafka: does not commit the offset on `RETRY` and seeks back to the current record; commits after success, duplicate, or terminal record.
- RocketMQ: returns reconsume on `RETRY`; acknowledges success after success, duplicate, or terminal record.
- ActiveMQ Classic: maps the Nexary topic to a JMS queue name; calls `Session.recover()` on `RETRY`, and client-acknowledges after success, duplicate, or terminal record.

This is not an exactly-once, global ordering, or distributed transaction guarantee. It is bounded retry, terminal record, and duplicate-consumption protection in the Nexary consume path.

Redis queue remains a lightweight queue. `application-redis.yml` exposes `queue-prefix`, `processing-prefix`, `processing-lease-prefix`, `visibility-timeout`, `processing-recovery-interval`, and `deduplication-*` to make the ready / processing / ack / stale recovery boundary explicit for local validation.

## SPI / Provider Isolation Mode

If you do not want the starter selector, use the provider-split SPI samples:

- `nexary-sample-messaging-spi-disruptor`
- `nexary-sample-messaging-spi-redis`
- `nexary-sample-messaging-spi-kafka`
- `nexary-sample-messaging-spi-rocketmq`
- `nexary-sample-messaging-spi-activemq-classic`

Those modules depend on `nexary-messaging-api` plus exactly one provider module. Business code still uses only the Nexary messaging API; switching providers means changing the provider dependency and configuration, not facade/controller/consumer code. SPI sample packages are provider-scoped, for example `org.nexary.samples.messaging.spi.kafka.*`.

After publication, SPI/provider dependencies look like:

Spring Boot 3.3 / Java 17+ SPI/provider dependencies are one-provider choices. Every block is copyable as-is.

Disruptor:

```gradle
def nexaryVersion = "0.5.1"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-disruptor'
}
```

Redis queue:

```gradle
def nexaryVersion = "0.5.1"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-redis'
}
```

Kafka:

```gradle
def nexaryVersion = "0.5.1"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-kafka'
}
```

RocketMQ:

```gradle
def nexaryVersion = "0.5.1"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-rocketmq'
}
```

ActiveMQ Classic:

```gradle
def nexaryVersion = "0.5.1"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation 'com.aweimao:nexary-messaging-api'
    runtimeOnly 'com.aweimao:nexary-messaging-activemq-classic'
}
```

Spring Boot 4.1 / Java 21 primary validation runtime SPI/provider shape:

```gradle
def nexaryVersion = "0.5.1"

dependencies {
    implementation "com.aweimao:nexary-messaging-api:${nexaryVersion}"

    runtimeOnly "com.aweimao:nexary-messaging-redis-spring-boot4:${nexaryVersion}"
}
```

## Run

```bash
./gradlew :nexary-samples:nexary-sample-messaging:run
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=redis'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=kafka'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=rocketmq'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=activemq-classic'
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
