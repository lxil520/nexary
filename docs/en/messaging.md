# Messaging Guide

Messaging has the most provider variation and the highest risk of blurred boundaries, so it should be treated as its own standalone capability.

## What to Read First

- module entry: [../../nexary-messaging/README.md](../../nexary-messaging/README.md)
- module guide: [modules.md](modules.md)
- acceptance checklist: [messaging-acceptance.md](messaging-acceptance.md)
- sample guide: [samples.md](samples.md)

## Current Scope

- `nexary-messaging-api`
- Kafka / RocketMQ / Redis queue / Disruptor
- shared envelope, serializer, interceptor, retry, and duplicate-protection abstractions

## Current Boundaries

- in `0.1.x`, one outbound provider per service is the recommended default
- starter selector samples are the main reference direction; the showcase is only for API feel
- duplicate-consumption protection is a core messaging acceptance item, not optional decoration

## Failure Semantics

Messaging failures are handled by the shared Nexary consume path. Business code does not receive Kafka, RocketMQ, Redis queue, or Disruptor native retry objects.

- `MessageRetryPolicy`: provider-neutral bounded retry policy with max attempts, initial delay, backoff strategy, and max backoff.
- `MessageDeadLetterPublisher`: provider-neutral terminal failure publisher.
- `MessageDeadLetterRecord`: terminal failure record after retry exhaustion, including message id, topic, key, consumer group, attempts, error type, and error message.
- `MessageConsumeExecutor`: completes dedup only after handler success or successful terminal record publication; handler failure or DLQ publication failure does not create false success dedup.

Provider mapping boundaries:

- Disruptor: re-publishes in process after Nexary backoff, then writes a terminal record.
- Redis queue: atomically moves a message from the ready list to a processing list; acknowledges and removes it from processing only after handler success, duplicate detection, or successful terminal record publication. `RETRY` requeues after Nexary backoff, and stale processing messages are recovered after their lease expires.
- Kafka: does not commit offsets on `RETRY` and seeks back to the current record; commits after success, duplicate, or terminal record.
- RocketMQ: returns reconsume on `RETRY`; acknowledges after success, duplicate, or terminal record; if any record in a batch needs retry, the RocketMQ batch is reconsumed.

Docs may describe bounded retry, terminal failure records, and duplicate-consumption protection. They must not claim exactly-once, global ordering, or distributed transactions.

Redis queue is still a lightweight queue, not a Kafka or RocketMQ equivalent. It provides a ready / processing / ack / retry / stale recovery state model, but not broker-level transactions, strict ordering, cross-consumer coordination, or exactly-once delivery. Key settings:

- `queue-prefix`: ready list key prefix.
- `processing-prefix`: processing list key prefix, isolated by topic and consumer group.
- `processing-lease-prefix`: processing lease key prefix used to detect stale messages.
- `visibility-timeout`: processing lease duration; expired messages can be recovered to ready.
- `processing-recovery-interval`: interval for the subscription worker to recover stale processing messages.
- `deduplication-prefix` / `deduplication-ttl`: duplicate-consumption protection keys, not an exactly-once proof.

## Observation Events And Metrics

Messaging reuses the provider-neutral observation foundation from `nexary-core`: capability code publishes `NexaryObservationEvent` and does not expose Micrometer, Actuator, Kafka, RocketMQ, Redis, or Disruptor native types from public APIs or business samples. To connect Micrometer, add `nexary-observation-micrometer-spring-boot-starter`; the bridge maps provider-neutral events to meters only in the Spring Boot integration layer.

Recommended metric names:

- `nexary.messaging.operation.total`: event counter.
- `nexary.messaging.operation.duration`: event duration. Most current messaging events are boundary events, so duration is mainly reserved for bridge extensions.

Event operations:

| Operation | Meaning |
| --- | --- |
| `publish` | provider publish entry |
| `consume` | provider consume result |
| `handler` | business handler success/failure |
| `retry.schedule` | retry scheduled by Nexary retry policy |
| `deadletter.publish` | terminal dead-letter record publication |
| `dedup.claim` | duplicate-consumption claim success/duplicate/failure |
| `provider.ack` | Redis processing ack |
| `provider.requeue` | Redis processing requeue |
| `provider.recovery` | Redis stale processing recovery |
| `provider.commit` | Kafka commit boundary |
| `provider.seek` | Kafka retry seek boundary |
| `provider.consume_status` | RocketMQ consume status boundary |
| `dispatch` | Disruptor dispatch boundary |

Allowed bounded tags:

- `capability`: always `messaging`.
- `provider`: `core`, `disruptor`, `redis`, `kafka`, or `rocketmq`.
- `outcome`: `success`, `failure`, `retry`, `duplicate`, `dead_letter`, or `noop`.
- `retry_attempt_bucket`: `none`, `1`, `2_3`, or `4_plus`.
- `terminal_status`: for example `retry_exhausted`.
- `failure_category`: `none`, `application`, `timeout`, or `system`.
- `boundary`: provider boundary such as `commit_offset`, `seek_current`, `consume_success`, or `reconsume_later`.

Forbidden tags:

- message id, payload, raw topic by default, raw consumer group by default
- exception message, stack trace
- arbitrary user input or high-cardinality values

Provider limits:

- Redis queue emits ack, requeue, and recovery boundary events; it remains a lightweight queue and does not provide broker transactions or exactly-once delivery.
- Kafka adapter emits Nexary-layer commit/seek boundaries only; it does not replace production consumer-container metrics.
- RocketMQ adapter emits Nexary-layer consume status boundaries only.
- Disruptor events describe in-process publish/dispatch only, not distributed broker metrics.

Dashboard examples:

- Messaging overview: `nexary.messaging.operation.total` by `provider` and `operation`.
- Failure surface: handler failure, publish failure, and dead-letter publish failure by `provider` and `failure_category`.
- Retry pressure: `retry.schedule` by `provider` and `retry_attempt_bucket`.
- Terminal failures: `deadletter.publish` by `provider` and `terminal_status`.
- Redis queue health: compare `provider.ack`, `provider.requeue`, and `provider.recovery`.

Observation events are operational signals only. They are not audit logs, reliable-delivery proof, exactly-once proof, global-ordering proof, or distributed-transaction proof.

## Current Sample State

`nexary-sample-messaging` is the primary messaging reference sample:

- `disruptor` profile: default runnable zero-infra profile
- `redis` profile: runnable with the repository Redis middleware stack
- `kafka` profile: runnable with the repository Kafka middleware stack
- `rocketmq` profile: runnable with the repository RocketMQ middleware stack

The sample uses a facade-style sending entry, but the facade sends messages through business-friendly calls such as `NexaryMessageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message)`. The consuming entry is a business `NexaryMessageHandler` annotated with `@NexaryMessageListener`, with topic/group defined as business constants. Subscription registration and provider loading are supplied by the Nexary messaging starter / provider auto-configuration. Business code depends only on `org.nexary.messaging.*` and sample DTOs; Kafka / RocketMQ / Redis / Disruptor switching is done with `nexary.messaging.provider` and provider settings.

Key copy paths:

- sending entry: `org.nexary.samples.messaging.facade`
- HTTP/test trigger edge: `org.nexary.samples.messaging.api`
- business messages, topic constants, and local business inbox: `org.nexary.samples.messaging.domain`
- business consumer entry: `org.nexary.samples.messaging.consumer`
- provider wiring: supplied by `nexary-messaging-spring-boot-starter` or a concrete provider dependency / auto-configuration, not copied into business code

SPI/provider dependency samples are split by provider:

- `nexary-sample-messaging-spi-disruptor`
- `nexary-sample-messaging-spi-redis`
- `nexary-sample-messaging-spi-kafka`
- `nexary-sample-messaging-spi-rocketmq`

## Recommended Adoption Order

1. understand the API and provider boundaries
2. inspect the starter selector sample direction
3. review the independent messaging acceptance checklist
4. use the [local validation guide](verification.md) for real middleware validation
