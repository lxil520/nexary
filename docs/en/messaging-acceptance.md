# Messaging Acceptance Checklist

Messaging acceptance is based on the focused messaging sample, provider adapter tests, and real middleware integration. The demo is not the acceptance surface.

## Public API

- the API does not expose Kafka, RocketMQ, Redis queue, or Disruptor native types
- send, consume, serialization, interceptor, Nexary-level retry policy, and terminal failure record abstractions are clearly covered
- message id, headers, topic, key, and payload boundaries are clear
- in `0.1.x`, one outbound provider per service is the default recommendation; the framework does not provide hidden multi-provider routing

## Provider Boundaries

| Provider | Current framework capability | Current sample state | Acceptance focus |
| --- | --- | --- | --- |
| Disruptor | in-process LMAX Disruptor provider | default profile is runnable | publish, consume, replay, dedup |
| Redis queue | Redis List ready/processing queues, ack/requeue/recovery, and Redis dedup store | `redis` profile is runnable | real Redis publish, consume, ack, retry, stale recovery, replay, dedup |
| Kafka | Kafka publish / consume adapter | `kafka` profile is runnable | real Kafka header propagation, listener adapter, dedup |
| RocketMQ | RocketMQ publish / consume adapter | `rocketmq` profile is runnable | real RocketMQ header propagation, listener adapter, dedup |

Disruptor is an in-process provider, not a distributed broker replacement. Redis queue is a lightweight queue and is not equivalent to Kafka or RocketMQ.

## Duplicate-Consumption Protection

- duplicate-consumption protection is a primary acceptance item
- consumer paths need to pass through the shared dedup-aware execution chain
- the sample must provide a replay path that publishes the same message id again
- replay publish may succeed, but dedup should prevent a second consumed record
- docs must describe duplicate-consumption protection and must not promise exactly-once behavior

## Failure Handling

- the API must provide Nexary-level `MessageRetryPolicy` with max attempts, initial delay, backoff strategy, and max backoff
- the API must provide a terminal failure path such as `MessageDeadLetterPublisher` and `MessageDeadLetterRecord`
- `MessageConsumeExecutor` returns `RETRY` while policy allows it and writes a terminal record after exhaustion
- dedup is completed only after handler success; handler failure or terminal record publication failure must not create false success dedup
- after terminal record publication succeeds, re-delivering the same message id should not invoke the business handler again
- Disruptor / Redis queue / Kafka / RocketMQ must pass through the same Nexary failure semantics
- docs must state provider mapping limits and must not claim exactly-once, global ordering, or distributed transactions

## Redis Queue Processing State

- Redis queue must use a ready list -> processing list -> ack state model, not only app-level requeue after `leftPop`.
- processing messages are acknowledged only after handler success, duplicate detection, or successful terminal record publication.
- retry must preserve message id and headers, then requeue to ready after Nexary backoff.
- stale processing recovery must use `visibility-timeout` / processing lease or an equivalent mechanism to move timed-out unacked messages back to ready.
- terminal failure must publish exactly one terminal record for the same message id; later duplicate delivery must not invoke the business handler again.
- real Redis integration tests must be repeatable against a dirty local middleware stack.

## Observation And Metrics

- Messaging must reuse `NexaryObservationEvent` / `NexaryObservationPublisher` from `nexary-core`; it must not create a messaging-only observation foundation.
- Public APIs and user-copyable samples must not expose Micrometer, Actuator, Redis, Kafka, RocketMQ, Disruptor, or other native types.
- Events must cover publish, consume, handler success/failure, retry scheduling, terminal dead-letter publication, dedup claim success/duplicate/failure, Redis ack/requeue/recovery, Kafka commit/seek boundary, RocketMQ consume status boundary, and Disruptor publish/dispatch.
- Tags must be bounded and limited to low-cardinality fields such as capability, operation, provider, outcome, retry attempt bucket, terminal status, failure category, and provider boundary.
- Tags must not include message id, payload, raw topic, raw consumer group, exception message, stack trace, or arbitrary user input.
- The no-op publisher must preserve existing messaging behavior when no observation listener is configured.
- The Micrometer bridge must prove bounded metric names and tags, and must not leak Micrometer types into messaging public APIs or business samples.

## Sample

- provider-backed sample structure is clear and not replaced by the demo
- `nexary-sample-messaging` must remain a Spring Boot project
- the sending entry uses a facade structure, with the controller acting only as the sample HTTP edge
- the consuming entry uses a business `NexaryMessageHandler` annotated with `@NexaryMessageListener`; Nexary framework code creates provider listeners and registers subscriptions
- the sample package structure must be split by integration responsibility:
  - `org.nexary.samples.messaging.app`: application entry
  - `org.nexary.samples.messaging.api`: HTTP/test trigger edge
  - `org.nexary.samples.messaging.facade`: facade/use case
  - `org.nexary.samples.messaging.domain`: business message, topic constants, and sample inbox
  - `org.nexary.samples.messaging.consumer`: business consumer entry
- the starter selector sample must not contain sample-owned provider factories, listener containers, subscriber configuration, or configuration loading classes
- non-starter dependency samples must be split into provider-specific modules, for example `nexary-sample-messaging-spi-kafka`, with packages named `org.nexary.samples.messaging.spi.<provider>.*`
- provider configuration, adapters, subscribers, diagnostics, and provider-only fixtures must live in Nexary provider modules or provider-specific SPI sample boundaries, not in the starter business sample
- Chinese docs are primary and English docs mirror them

## Local Validation Evidence

Messaging changes should include:

- changed modules
- runnable profiles and commands
- profiles that require real middleware
- completed and incomplete provider boundaries
- retry/dead-letter self-check evidence
- `./gradlew check` result

Minimum commands:

```bash
./gradlew :nexary-samples:nexary-sample-messaging:test
./gradlew :nexary-messaging:nexary-messaging-disruptor:test
./gradlew :nexary-messaging:nexary-messaging-redis:test
./gradlew :nexary-messaging:nexary-messaging-kafka:test
./gradlew :nexary-messaging:nexary-messaging-rocketmq:test
```

Broker profile validation commands:

```bash
./scripts/middleware/up.sh
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=kafka'
./gradlew :nexary-samples:nexary-sample-messaging:run --args='--spring.profiles.active=rocketmq'
```

Provider sample structure validation commands:

```bash
find nexary-samples/nexary-sample-messaging/src/main/java/org/nexary/samples/messaging -maxdepth 3 -type f | sort
./gradlew :nexary-samples:nexary-sample-messaging:check
```
