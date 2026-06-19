# Sample Guide

The samples are meant to show where code goes in a real service, not just prove that an endpoint returns something.

The samples are split by cache, messaging, and job. Each one is a small Spring Boot project rather than a single-file demo.

## Sample Matrix

| Module | Port | Scenario | What You Should Reuse |
| --- | --- | --- | --- |
| `nexary-sample-cache` | `8081` | profile reads and cache warmup | cache-aside, batch fetch, invalidation patterns |
| `nexary-sample-cache-spi-redis` | `8091` | manual Cache Redis setup | API + Redis provider only |
| `nexary-sample-messaging` | `8082` | business event publishing | sending/consumer shape; provider switching through `nexary.messaging.provider` |
| `nexary-sample-messaging-spi-disruptor` | `8092` | manual Messaging Disruptor setup | API + Disruptor provider only |
| `nexary-sample-messaging-spi-redis` | `8093` | manual Messaging Redis queue setup | API + Redis provider only |
| `nexary-sample-messaging-spi-kafka` | `8094` | manual Messaging Kafka setup | API + Kafka provider only |
| `nexary-sample-messaging-spi-rocketmq` | `8095` | manual Messaging RocketMQ setup | API + RocketMQ provider only |
| `nexary-sample-job` | `8083` | reconciliation and compensation jobs | `NexaryJob`, schedule registration, execution-state tracking |
| `nexary-sample-job-spi-scheduler` | `8096` | manual Job local scheduler setup | API + local scheduler provider only |
| `nexary-sample-job-spi-xxljob` | `8097` | manual Job XXL-JOB bridge setup | API + XXL-JOB bridge provider only |

Full details live in [../../nexary-samples/README.md](../../nexary-samples/README.md).

## Recommended Reading Order

1. choose the cache, messaging, or job starter sample you need
2. move to the matching provider sample when you want to manage dependencies yourself
3. use [integration.md](integration.md) to run the real middleware stack locally

## Main Samples

```bash
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
```

They answer a practical question: if I only add cache, messaging, or jobs, what should my service look like?

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

- the main samples are closer to copyable service structure
- the messaging sample's main path is starter selector based; business code does not directly use Kafka/RocketMQ/Redis/Disruptor native SDKs
- real middleware validation comes from `scripts/middleware/*` plus integration tests, not from controller output alone

## Next Sample Steps

- add fuller business-oriented samples instead of multiplying toy endpoints
- keep extending messaging broker validation scripts and failure scenarios
- show local scheduler and XXL-JOB bridge side by side in the job references
