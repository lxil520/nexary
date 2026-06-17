# Module Guide

## Framework

- `nexary-framework/nexary-core`: cross-cutting primitives shared by every module.
- `nexary-framework/nexary-spi`: JDK `ServiceLoader` registry and composite registry abstractions.

## Cache

- `nexary-cache/nexary-cache-api`: cache client, namespaced keys, TTL, batch operations, cache-aside, lock handle, and atomic counter abstractions.
- `nexary-cache/nexary-cache-redis`: Redis adapter and Spring Boot auto-configuration. Tiered cache mode uses an internal Caffeine L1 and does not expose Caffeine as a peer backend.

## Messaging

- `nexary-messaging/nexary-messaging-api`: envelope, publisher, consumer, serializer, interceptor, retry policy, dead-letter, publish result, consume result, and duplicate-protection abstractions.
- `nexary-messaging/nexary-messaging-disruptor`: official LMAX Disruptor-based in-process ring-buffer queue for low-latency local dispatch.
- `nexary-messaging/nexary-messaging-kafka`: Kafka publisher adapter and duplicate-aware consume bridge through a Spring `kafkaTemplate` bean.
- `nexary-messaging/nexary-messaging-redis`: Redis list-backed queue and Redis-backed message deduplication store. Disabled by default and enabled explicitly when a Redis queue is desired.
- `nexary-messaging/nexary-messaging-rocketmq`: RocketMQ publisher adapter through a Spring `rocketMQTemplate` bean.

## Job

- `nexary-job/nexary-job-api`: job, schedule, execution context, result, listener, execution id, execution record, and execution policy APIs.
- `nexary-job/nexary-job-scheduler`: local Spring `TaskScheduler` implementation with optional cache-backed single-instance locks, worker heartbeat, shard load-balancing, and unified execution lifecycle.
- `nexary-job/nexary-job-xxljob`: XXL-JOB bridge that maps external triggers and shard metadata to `NexaryJob` and reuses the unified execution lifecycle.
- `nexary-job/nexary-job-execution-store-redis`: optional Redis durable execution store for completed execution records with TTL retention.

## Boot

- `nexary-boot/nexary-bom`: dependency constraints.
- `nexary-boot/nexary-cache-spring-boot-starter`: cache starter.
- `nexary-boot/nexary-messaging-spring-boot-starter`: messaging starter.
- `nexary-boot/nexary-job-spring-boot-starter`: job starter.
- `nexary-boot/nexary-observation-micrometer-spring-boot-starter`: standalone Spring Boot integration that bridges `NexaryObservationEvent` to Micrometer.

## Samples

- `nexary-samples/nexary-sample-cache`: focused cache starter selector sample.
- `nexary-samples/nexary-sample-cache-spi-redis`: cache SPI Redis provider sample.
- `nexary-samples/nexary-sample-messaging`: focused messaging starter selector sample.
- `nexary-samples/nexary-sample-messaging-spi-*`: messaging SPI provider samples.
- `nexary-samples/nexary-sample-job`: focused job starter selector sample.
- `nexary-samples/nexary-sample-job-spi-*`: job SPI provider samples.
