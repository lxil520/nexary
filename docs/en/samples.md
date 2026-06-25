# Sample Guide

The sample suite is meant to be copied from, not merely executed.

Nexary currently ships focused starter selector and SPI provider reference applications by capability. They are intentionally kept as real project shapes instead of single-file demos.

## Sample Matrix

| Module | Port | Scenario | What You Should Reuse |
| --- | --- | --- | --- |
| `nexary-sample-cache` | `8081` | profile reads and cache warmup | cache-aside, batch fetch, invalidation patterns |
| `nexary-sample-cache-spi-redis` | `8091` | Cache SPI Redis integration | API + Redis provider only |
| `nexary-sample-messaging` | `8082` | business event publishing edge | sending entry and consumer shape; provider switching through `nexary.messaging.provider` |
| `nexary-sample-messaging-spi-disruptor` | `8092` | Messaging SPI Disruptor integration | API + Disruptor provider only |
| `nexary-sample-messaging-spi-redis` | `8093` | Messaging SPI Redis queue integration | API + Redis provider only |
| `nexary-sample-messaging-spi-kafka` | `8094` | Messaging SPI Kafka integration | API + Kafka provider only |
| `nexary-sample-messaging-spi-rocketmq` | `8095` | Messaging SPI RocketMQ integration | API + RocketMQ provider only |
| `nexary-sample-messaging-spi-activemq-classic` | `8098` | Messaging SPI ActiveMQ Classic integration | API + ActiveMQ Classic provider only |
| `nexary-sample-job` | `8083` | reconciliation and compensation jobs | `NexaryJob`, schedule registration, execution-state tracking |
| `nexary-sample-job-spi-scheduler` | `8096` | Job SPI local scheduler integration | API + local scheduler provider only |
| `nexary-sample-job-spi-xxljob` | `8097` | Job SPI XXL-JOB bridge integration | API + XXL-JOB bridge provider only |
| `nexary-sample-job-spi-powerjob` | no fixed HTTP port | Job PowerJob trigger integration | API + PowerJob provider only |
| `nexary-sample-governance` | `8080` | local governance, circuit state, request cancellation, and read-only diagnostics page | stable resource names, `GovernanceContext`, fallback, local diagnostics, and cooperative cancellation checks |
| `nexary-sample-governance-gateway` | `28090` | Spring Cloud Gateway entry cancellation notification | deadline / cancellation header propagation and downstream receiver wiring |

Full details live in [../../nexary-samples/README.md](../../nexary-samples/README.md).

## Recommended Reading Order

1. choose the cache, messaging, job, or governance starter selector sample for the capability you need
2. move to the matching SPI provider sample when you need tighter provider dependency control
3. use [integration.md](integration.md) to run the real middleware stack locally

## Focused Samples

```bash
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
./gradlew :nexary-samples:nexary-sample-governance:run
./gradlew :nexary-samples:nexary-sample-governance-gateway:run
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

Governance sample:

- `GET /governance/profiles/{id}`
- `GET /governance/degraded/{id}`
- `GET /governance/cancellation/slow/{id}?durationMillis=3000`
- `POST /governance/circuit/reset`
- `GET /governance/circuit/profiles/{id}?mode=success|failure|slow`
- `GET /nexary/governance/summary`
- `GET /nexary/governance/resources`
- `GET /nexary/governance/events`
- `GET /nexary/console`

Gateway cancellation sample:

- `GET /gateway/governance/cancellation/slow/{id}?durationMillis=5000`

Verify downstream cancellation diagnostics directly:

```bash
NEXARY_GOVERNANCE_CANCELLATION_BASE_URL=http://localhost:8080 \
  ./scripts/governance-cancellation/smoke.sh
```

## Current Sample Boundaries

- the dedicated samples are closer to copyable service structure
- the governance sample verifies local state and request cancellation in the current JVM only; it does not replace Sentinel or provide remote policy push
- the Gateway sample covers the Spring Boot 3.3 mainline; the Boot2 Gateway sample is verified in `nexary-sample-governance-gateway-boot2`; the Boot4 Gateway sample should be added to the matrix only after its version passes
- the messaging sample's main path is starter selector based; business code does not directly use Kafka/RocketMQ/Redis/Disruptor native SDKs
- real middleware validation comes from `scripts/middleware/*` plus integration tests, not from controller output alone

## Next Sample Steps

- add fuller business-oriented samples instead of multiplying toy endpoints
- keep extending messaging broker validation scripts and failure scenarios
- show local scheduler and XXL-JOB bridge side by side in the job references
- add the Boot4 Gateway sample and gate in v0.11.2, then update the README support matrix
