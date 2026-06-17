# Sample Guide

The sample suite is meant to be copied from, not merely executed.

Nexary currently ships focused starter selector and SPI provider reference applications by capability. They are intentionally kept as real project shapes instead of single-file demos.

## Sample Matrix

| Module | Port | Scenario | What You Should Reuse |
| --- | --- | --- | --- |
| `nexary-sample-cache` | `8081` | profile reads and cache warmup | cache-aside, batch fetch, invalidation patterns |
| `nexary-sample-cache-spi-redis` | `8091` | Cache SPI Redis adoption | API + Redis provider only |
| `nexary-sample-messaging` | `8082` | business event publishing edge | facade/consumer shape; provider switching through `nexary.messaging.provider` |
| `nexary-sample-messaging-spi-disruptor` | `8092` | Messaging SPI Disruptor adoption | API + Disruptor provider only |
| `nexary-sample-messaging-spi-redis` | `8093` | Messaging SPI Redis queue adoption | API + Redis provider only |
| `nexary-sample-messaging-spi-kafka` | `8094` | Messaging SPI Kafka adoption | API + Kafka provider only |
| `nexary-sample-messaging-spi-rocketmq` | `8095` | Messaging SPI RocketMQ adoption | API + RocketMQ provider only |
| `nexary-sample-job` | `8083` | reconciliation and compensation jobs | `NexaryJob`, schedule registration, execution-state tracking |
| `nexary-sample-job-spi-scheduler` | `8096` | Job SPI local scheduler adoption | API + local scheduler provider only |
| `nexary-sample-job-spi-xxljob` | `8097` | Job SPI XXL-JOB bridge adoption | API + XXL-JOB bridge provider only |

Full details live in [../../nexary-samples/README.md](../../nexary-samples/README.md).

## Recommended Reading Order

1. choose the cache, messaging, or job starter selector sample for the capability you need
2. move to the matching SPI provider sample when you need tighter provider dependency control
3. use [integration.md](integration.md) to run the real middleware stack locally

## Focused Samples

```bash
./gradlew :nexary-samples:nexary-sample-cache:bootRun
./gradlew :nexary-samples:nexary-sample-messaging:bootRun
./gradlew :nexary-samples:nexary-sample-job:bootRun
```

These answer the question: how should a service be structured if I only adopt one capability?

## Endpoints

Cache sample:

- `GET /examples/cache/profiles/{id}`
- `POST /examples/cache/warmup`
- `GET /examples/cache/batch?ids=101,102`
- `DELETE /examples/cache/profiles/{id}`

Messaging sample:

- `POST /examples/messages`
- `POST /examples/messages/replay/{messageId}`
- `GET /examples/messages`
- `GET /examples/messages/provider`

Job sample:

- `POST /examples/jobs/run-once`
- `POST /examples/jobs/schedule`
- `GET /examples/jobs/state`

## Current Sample Boundaries

- the focused samples are closer to copyable service structure
- the messaging sample's main path is starter selector based; business code does not directly use Kafka/RocketMQ/Redis/Disruptor native SDKs
- real middleware validation comes from `scripts/middleware/*` plus integration tests, not from controller output alone

## Next Sample Steps

- add fuller business-oriented samples instead of multiplying toy endpoints
- keep extending messaging broker validation scripts and failure scenarios
- show local scheduler and XXL-JOB bridge side by side in the job references
