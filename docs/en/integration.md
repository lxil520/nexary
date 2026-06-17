# Middleware Integration

This page covers only what the repository can validate today: containerized Redis, Kafka, and RocketMQ checks, plus local dependency bring-up for XXL-JOB Admin.

## Current Boundary

- `nexary-cache-tiered-internal` is internal support for Redis tiered cache mode, not a peer backend beside Redis.
- Redis, Kafka, and RocketMQ now have smoke checks plus JUnit integration tests that run against real containers.
- XXL-JOB is still bridge-only in this repository. `nexary-job-xxljob` maps external triggers to `NexaryJob`, but it does not yet expose executor registration or handler publishing. The current validation therefore covers Admin + MySQL startup, not a full end-to-end execution path.
- The published `xuxueli/xxl-job-admin` tags are currently `linux/amd64` only. On Apple Silicon the container runs through Docker's amd64 emulation rather than a native arm64 image.

## Layout

- Compose: `deploy/middleware/docker-compose.yml`
- Environment template: `deploy/middleware/.env.example`
- Start/stop scripts: `scripts/middleware/up.sh`, `scripts/middleware/down.sh`
- Smoke checks: `scripts/middleware/smoke.sh`
- JUnit integration tests: `scripts/middleware/run-integration-tests.sh`

## Start the Stack

```bash
cp deploy/middleware/.env.example deploy/middleware/.env
./scripts/middleware/up.sh
```

## Run Smoke Checks

```bash
./scripts/middleware/smoke.sh
```

The smoke script validates:

- Redis `PING`
- Kafka topic creation, produce, and consume
- RocketMQ NameServer/Broker cluster visibility
- MySQL initialization of the `xxl_job` schema
- HTTP reachability of XXL-JOB Admin

## Run Real JUnit Integration Tests

```bash
./scripts/middleware/run-integration-tests.sh
```

The current suite covers:

- `nexary-cache-redis` TTL and lock renewal against real Redis
- `nexary-messaging-redis` publish and deduplication against real Redis Queue
- `nexary-messaging-kafka` header propagation and dedup bridge against real Kafka
- `nexary-messaging-rocketmq` header propagation and dedup bridge against real RocketMQ

## Stop and Clean Up

```bash
./scripts/middleware/down.sh
```
