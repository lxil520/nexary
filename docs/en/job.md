# Job Guide

Read the job capability independently because local scheduling and external platform bridging are different concerns.

## What to Read First

- module entry: [../../nexary-job/README.md](../../nexary-job/README.md)
- acceptance checklist: [job-acceptance.md](job-acceptance.md)
- focused job sample: [../../nexary-samples/nexary-sample-job/README.en.md](../../nexary-samples/nexary-sample-job/README.en.md)
- processor-style integration: [job-processor-style.md](job-processor-style.md)
- module guide: [modules.md](modules.md)

## Current Scope

- `nexary-job-api`: shared job API
- `nexary-job-scheduler`: local scheduler implementation
- `nexary-job-xxljob`: XXL-JOB bridge
- `nexary-job-execution-store-redis`: optional Redis durable execution store
- PowerJob: future bridge direction, not a current implemented capability

## Core Boundaries

- `NexaryJob` is the shared job abstraction.
- Local scheduling is the framework-native execution mode.
- XXL-JOB is a bridge from external platform triggers back into `NexaryJob`, not a second public job API.
- Future PowerJob support should also be a bridge and must not force changes into the current public API.

## Version Entry and Dependency Choice

The Job capability declares only verified combinations as supported. Target combinations are marked as target, pending verification, and unpublished.

| Spring Boot | JDK | Status | Recommended Entry |
| --- | --- | --- | --- |
| Spring Boot 3.3.x | Java 17+ | currently verified | `org.nexary:nexary-job-spring-boot-starter` |
| Spring Boot 2.7.x | Java 8+ | v0.2 compatibility target, pending verification, unpublished | planned `org.nexary:nexary-job-spring-boot2-starter` |
| Spring Boot 4.x | Java 21+ primary validation target | later v0.2 validation target, pending verification, unpublished | planned `org.nexary:nexary-job-spring-boot4-starter` |

The currently verified starter artifactId is `nexary-job-spring-boot-starter`. If Nexary publishes Boot2 / Boot3 / Boot4 lines side by side, the smallest release-facing naming adjustment should make the current Boot3 entry explicit as `nexary-job-spring-boot3-starter`, while Boot2 and Boot4 remain separate target artifacts until verified. This avoids users applying the Boot3 starter to Boot2 applications by mistake.

Maven starter mode:

```xml
<!-- Currently verified: Spring Boot 3.3.x + Java 17+ -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.nexary</groupId>
      <artifactId>nexary-bom</artifactId>
      <version>${nexaryVersion}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependency>
  <groupId>org.nexary</groupId>
  <artifactId>nexary-job-spring-boot-starter</artifactId>
</dependency>
```

Gradle starter mode:

```groovy
// Currently verified: Spring Boot 3.3.x + Java 17+
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
// The starter aggregates Nexary job API, local scheduler, XXL-JOB bridge,
// and the Redis execution store provider. Select the runtime provider with
// nexary.job.provider.
implementation 'org.nexary:nexary-job-spring-boot-starter'
```

SPI/provider mode is for applications that want exactly one concrete provider dependency. Business jobs still depend only on `NexaryJob`, `JobContext`, and `JobResult`:

```groovy
// Business code needs only the Nexary job API at compile time.
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-api'

// Local scheduler provider. Choose either local scheduler or XXL-JOB bridge.
runtimeOnly 'org.nexary:nexary-job-scheduler'

// XXL-JOB bridge provider. Choose either this or local scheduler.
// runtimeOnly 'org.nexary:nexary-job-xxljob'

// Optional: Redis durable execution store.
// Add and enable it only when completed execution records must be looked up
// across process restarts or provider/store object recreation.
// runtimeOnly 'org.nexary:nexary-job-execution-store-redis'
```

Target but unpublished compatibility artifact names:

| Target | Planned artifactId | Status |
| --- | --- | --- |
| Boot2 starter | `nexary-job-spring-boot2-starter` | target / pending verification / unpublished |
| Boot2 Java8 API | `nexary-job-api-java8` | target / pending verification / unpublished |
| Boot2 local scheduler | `nexary-job-scheduler-spring5` | target / pending verification / unpublished |
| Boot2 XXL-JOB bridge | `nexary-job-xxljob-spring5` | target / pending verification / unpublished |
| Boot2 Redis execution store | `nexary-job-execution-store-redis-spring5` | target / pending verification / unpublished |
| Boot4 starter | `nexary-job-spring-boot4-starter` | target / pending verification / unpublished |

## Adoption Modes

Business code should implement or call only Nexary job API:

- job handlers implement `NexaryJob`
- execution context uses `JobContext`
- outcomes use `JobResult`
- execution receipts use `JobExecutionListener`
- callers that need one execution trace use `NexaryJobOperations.triggerExecution(...)` and read `JobExecutionRecord`

Local scheduler, XXL-JOB bridge, and processor startup belong to starter/provider infrastructure. Applications can start with `nexary-job-spring-boot-starter`, which aggregates the current providers, or depend on `nexary-job-api` and add exactly one concrete provider for the selected runtime mode.

Switching between local scheduler and the XXL-JOB bridge should not change business job handlers. Starter mode changes `nexary.job.provider` and related settings; SPI/provider mode changes the provider dependency and related settings.

Current samples:

- starter selector: `nexary-samples/nexary-sample-job`
- SPI local provider: `nexary-samples/nexary-sample-job-spi-scheduler`
- SPI XXL-JOB bridge provider: `nexary-samples/nexary-sample-job-spi-xxljob`

## Execution Lifecycle

The Nexary v0.1 job execution lifecycle is provider-neutral:

- `JobExecutionId`: unique id for one execution
- `JobExecutionRecord`: trigger source, context, status, attempts, start/end timestamps, duration, message, and error
- `JobExecutionStatus`: shared `SUCCESS`, `FAILED`, `SKIPPED`, `TIMEOUT`, and `CANCELLED` states
- `JobExecutionPolicy`: timeout, retry attempts/backoff, concurrency behavior, misfire behavior, and single-instance lock lease

`triggerExecution(...)` returns the full execution record. The older `trigger(...)` method remains available for simple callers that only need `JobResult`. Direct triggers, local cron schedules, and XXL-JOB bridge triggers enter the same execution pipeline, so listener, retry, timeout, result mapping, and execution record semantics stay consistent.

Execution records use an in-memory store by default, which fits local development and lightweight tests. Enable the Redis durable store when records must be looked up across process or store object recreation:

```yaml
nexary:
  job:
    execution:
      store:
        redis:
          enabled: true
          key-prefix: "nexary:job:execution:"
          retention: 1d
```

The Redis store saves completed execution records and applies Redis TTL from `retention`. It does not provide running cancellation and it is not a replacement for a business audit database. If long-term audit is required, forward `JobExecutionListener` receipts into your own audit store.

Cancellation semantics are split intentionally:

- `cancel(jobName)` cancels future scheduled executions registered inside the active provider. The local scheduler supports it. The XXL-JOB bridge does not because its schedule is owned by the external platform.
- `cancelExecution(executionId)` is running-execution cancellation. v0.1 explicitly does not support it; providers return `false`.

## Observation Events and Metrics

Job providers reuse `NexaryObservationPublisher` / `NexaryObservationListener` from `nexary-framework:nexary-core`. Business `NexaryJob` code does not depend on Micrometer, Redis, XXL-JOB, or local scheduler types. When an application provides an observation publisher or listener, Nexary job providers emit events from framework execution paths.

Current job event names can be used as metric bridge names:

- `job.trigger`
- `job.execution.start`
- `job.execution.end`
- `job.retry.attempt`
- `job.execution.timeout`
- `job.execution.skip`
- `job.listener.notification`
- `job.store.save`
- `job.store.find`
- `job.store.retention_expiry`
- `job.scheduler.run`
- `job.xxljob.bridge.trigger`

Tags must stay bounded:

- `capability`: fixed `job`
- `operation`: event name
- `provider`: `local`, `xxljob`, `memory`, `redis`, or `unknown`
- `trigger`: `direct`, `scheduled`, `bridge`, or `unknown`
- `status`: bounded values such as `success`, `failed`, `skipped`, `timeout`, `accepted`, `running`, and `expired`
- `skip_reason`: `none`, `misfire`, `concurrency`, `single_instance`, `shard_assignment`, `unknown`
- `shard_presence`: `true` / `false` / `unknown`
- `failure_category`: `none`, `timeout`, `application`, `system`
- `retry_attempt_bucket`: `none`, `1`, `2_3`, `4_plus`
- `retry_phase`: `first`, `retry`, `final`
- `store`: `memory`, `redis`

Never put these values into tags: execution id, job parameters, payload, exception message, stack trace, cache key, message id, lock token, fencing token, or arbitrary user input.

Dashboard examples:

- job success rate: aggregate `job.execution.end` by `provider`, `trigger`, and `status`
- execution latency: use event duration from `job.execution.end`, grouped by `provider`, `trigger`, and `shard_presence`
- retry pressure: aggregate `job.retry.attempt` by `retry_phase` and `retry_attempt_bucket`
- skip reasons: aggregate `job.execution.skip` by `skip_reason`
- store health: aggregate `job.store.save`, `job.store.find`, and `job.store.retention_expiry` by `store` and `status`
- XXL-JOB bridge ingress: aggregate `job.xxljob.bridge.trigger`; this is bridge ingress only, not proof of the Admin scheduling lifecycle

To connect Prometheus or another Micrometer backend, add `nexary-observation-micrometer-spring-boot-starter`. The bridge consumes provider-neutral events and creates Micrometer meters without changing the public APIs of `NexaryJob`, the scheduler, or the XXL-JOB bridge. Custom company metrics platforms can still provide a `NexaryObservationListener`, but the tag whitelist above must be preserved.

## When to Use Local Scheduling

Local scheduling fits lightweight service-local scheduled jobs, compensation jobs, reconciliation jobs, and demos.

The current sample covers:

- Spring scanning of `NexaryJob` beans
- job-name mapping through `NexaryJob.name()`
- direct business-job execution
- cron schedule registration
- single-instance execution: when a Nexary cache provider is available, a distributed lock avoids duplicate execution
- local distributed sharding: worker metadata can come from `JobSchedule` or `nexary.job.scheduler.*`
- built-in load-balancing strategies: `round_robin`, `random`, `consistent_hash`, `least_active`, `first_available`

Local distributed scheduling boundaries:

- `worker-id` identifies the current process.
- `workers` describes the current scheduling topology.
- `load-balance` assigns each shard to a worker.
- when a Nexary `CacheClient` is available and `worker-id` is configured, the local scheduler writes heartbeats and removes workers after `heartbeat-ttl`.
- `topology` isolates worker registries across applications or deployments.
- `execution-timeout`, `retry-attempts`, `retry-backoff`, `concurrency-policy`, `misfire-policy`, `misfire-threshold`, and `lock-lease-time` control local execution lifecycle.
- when `workers` is not configured, the provider keeps single-process behavior and runs all shards locally.
- when static `workers` is not configured but cache heartbeats are enabled, the worker list comes from active cache heartbeats.
- business `NexaryJob` code does not read these settings; it receives shard metadata from `JobContext`.

## When to Use the XXL-JOB Bridge

The XXL-JOB bridge fits teams that already run XXL-JOB and want platform triggers mapped back into the shared `NexaryJob` abstraction.

The current sample covers:

- the `xxljob` profile
- bridge-shaped trigger mapping
- shard parameter mapping
- the same execution lifecycle used by direct/local schedule execution: listener, retry, timeout, record, and result mapping

XXL-JOB itself owns Admin scheduling, routing strategies, sharding broadcast, and executor lifecycle. Nexary `nexary-job-xxljob` currently acts only as a bridge: it maps platform triggers and shard metadata into `NexaryJob` / `JobContext`. The current sample does not claim complete executor registration, Admin scheduling, or callback lifecycle validation.

## When to Use Processor-Style Integration

Processor-style integration fits production job executors deployed as independent processes. It does not start a web server. It starts as a non-web Spring Boot application, component-scans `NexaryJob` handlers, and executes business jobs through bridge or external-platform triggers.

The current reference skeleton lives in the `processor` subpackage of `nexary-samples/nexary-sample-job`. See [job-processor-style.md](job-processor-style.md).

## Recommended Adoption Order

1. understand `NexaryJob`, `JobContext`, `JobResult`, and `JobSchedule`
2. run starter selector mode in `nexary-sample-job`
3. inspect `nexary-sample-job-spi-scheduler` for API + local provider adoption
4. inspect `nexary-sample-job-spi-xxljob` for API + bridge provider adoption
5. inspect the processor-style skeleton if your production shape is a standalone job process
6. validate against [job-acceptance.md](job-acceptance.md)
7. run Docker middleware and integration validation when external platform validation is needed
