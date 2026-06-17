# Job Acceptance Checklist

## API

- `NexaryJob` is the only public business job abstraction.
- `JobContext` represents job name, scheduled time, shard metadata, and optional parameters.
- `JobResult` uses enum status values for success or failure instead of magic strings.
- `JobSchedule` represents cron, single-instance execution, shard total, worker metadata, and load-balancing strategy.
- `JobExecutionId`, `JobExecutionRecord`, and `JobExecutionStatus` represent provider-neutral execution identity and state.
- `JobExecutionPolicy` represents timeout, retry attempts/backoff, concurrency behavior, misfire behavior, and single-instance lock lease.
- `JobExecutionStore` is the provider-neutral execution record storage abstraction, with in-memory default and durable provider replacement.
- the load-balancing abstraction covers at least `round_robin`, `random`, `consistent_hash`, `least_active`, and `first_available`.
- Public API does not expose XXL-JOB or future PowerJob native types.

## Local Scheduler

- direct execution is supported.
- cron schedule registration is supported.
- local schedule cancellation is supported.
- execution listeners are supported.
- direct trigger and cron schedule must enter the same execution lifecycle pipeline.
- single-instance lock lease must not be hardcoded; it must come from configuration or schedule execution policy.
- skipped single-instance, skipped shard assignment, timeout, success after retry, and final failure should all produce `JobExecutionRecord`.
- direct trigger, cron schedule, and skipped paths must all save records through `JobExecutionStore`.
- single-instance semantics document whether they depend on external lock support.
- distributed sharding semantics document where worker topology comes from and how the provider degrades when no workers are configured.
- when a Nexary `CacheClient` is available, the local scheduler registers the current worker through heartbeats and removes expired workers by TTL.
- local sharding guarantees only Nexary scheduler shard assignment and does not replace external platform scheduling.

## XXL-JOB Bridge

- the bridge reuses `NexaryJob` and does not create a second public job API.
- the bridge maps external platform shard metadata into `JobContext`.
- bridge execution still invokes the shared listeners.
- bridge triggers must enter the same execution lifecycle pipeline, including listener, retry, timeout, result mapping, and execution record.
- bridge triggers must save records through the same `JobExecutionStore` and retain bridge trigger plus shard metadata.
- XXL-JOB Admin scheduling, routing strategies, sharding broadcast, and executor lifecycle belong to the XXL-JOB platform and are not reimplemented inside the Nexary local scheduler.
- documentation must not describe Admin health as complete executor lifecycle validation.

## Sample

- `nexary-sample-job` is a Spring Boot application.
- `nexary-sample-job` is a starter selector sample and does not contain provider wiring packages.
- sample main code treats the business job handler as the first-copy user code.
- business job handlers depend only on Nexary job API, not on `mode.local`, `mode.xxljob`, or provider wiring.
- switching from local scheduler to the XXL-JOB bridge does not change business job code.
- starter selector mode chooses the provider through `nexary.job.provider` and profile configuration.
- SPI/provider samples are split into one module per provider instead of mixing multiple providers into one SPI module.
- `nexary-sample-job-spi-scheduler` shows only API + local provider adoption.
- `nexary-sample-job-spi-xxljob` shows only API + XXL-JOB bridge provider adoption.
- the `local` profile demonstrates job-name mapping, direct execution, and local schedule registration through tests.
- the `xxljob` profile demonstrates bridge trigger shape and shard mapping through tests.
- sample docs clearly distinguish local scheduling from bridge-triggered execution.
- sample config comments explain that execution records are in-memory by default and Redis durable store must be enabled explicitly.

## Durable Execution Store

- the in-memory execution store remains the default local development path.
- after Redis durable store is enabled, records remain queryable by `JobExecutionId` after runner/store object recreation.
- Redis durable store must cover success, failure, retry success, timeout, skip, shard metadata, and bridge metadata.
- retention / TTL semantics must be explicit and verified by a real Redis integration test.
- durable store must not change the v0.1 running-cancellation non-goal.

## Processor-Style Integration

- the processor sample starts with `WebApplicationType.NONE` and does not start a web server.
- job handlers are Spring `@Component` beans that implement `NexaryJob`.
- execution receives job name, scheduled time, shard metadata, and runtime context through `JobContext`.
- the processor sample main path only shows non-web startup and the business job handler, without extra receipt stores or collaborator simulation classes.

## PowerJob Boundary

- PowerJob is a future bridge direction, not a current implemented capability.
- a future PowerJob bridge should reuse `NexaryJob`, `JobContext`, `JobResult`, and `JobExecutionListener`.
- PowerJob native types should not enter the current public API.

## Integration Validation

- baseline validation should cover job API, local scheduler, XXL-JOB bridge, and job sample tests.
- durable store validation should include a real middleware test for the Redis-backed store.
- processor-style skeleton validation should cover non-web startup, component scanning, and bridge trigger mapping.
- external platform validation must distinguish "bridge trigger mapping validated" from "real executor registration and platform trigger validated".
- v0.1 running cancellation is an explicit non-goal; `cancelExecution(executionId)` returns `false`, and documentation must not claim it is supported.
