# Job Guide

Read the job capability independently because local scheduling and external platform bridging are different concerns.

## What to Read First

- module entry: [../../nexary-job/README.md](../../nexary-job/README.md)
- acceptance checklist: [job-acceptance.md](job-acceptance.md)
- focused job sample: [../../nexary-samples/nexary-sample-job/README.en.md](../../nexary-samples/nexary-sample-job/README.en.md)
- processor-style integration: [job-processor-style.md](job-processor-style.md)
- module guide: [modules.md](modules.md)

## Supported Pieces

- `nexary-job-api`: shared job API
- `nexary-job-scheduler`: local scheduler implementation
- `nexary-job-xxljob`: XXL-JOB bridge
- `nexary-job-powerjob`: PowerJob trigger mapping
- `nexary-job-execution-store-redis`: optional Redis durable execution store

## Core Boundaries

- `NexaryJob` is the shared job abstraction.
- Local scheduling is the framework-native execution mode.
- XXL-JOB is a bridge from external platform triggers back into `NexaryJob`, not a second public job API.
- PowerJob also maps external platform triggers into `NexaryJob` without changing the public API.

## Version Entry and Dependency Choice

The current development version is `0.11.0`. After Maven Central publication, replace `nexaryVersion` with the latest release.

| Spring Boot | JDK | Status | Recommended Entry |
| --- | --- | --- | --- |
| Spring Boot 3.3.x | Java 17+ | currently verified | `com.aweimao:nexary-job-spring-boot-starter` |
| Spring Boot 2.7.x | Java 8+ | verified bounded scope | `com.aweimao:nexary-job-spring-boot2-starter` |
| Spring Boot 4.1.x | Java 21 is Nexary's primary validation runtime; official minimum JDK follows Spring documentation | verified Boot4 provider/starter | `com.aweimao:nexary-job-spring-boot4-starter` |

The Boot3 mainline starter is still named `nexary-job-spring-boot-starter`. Boot2 and Boot4 use explicit version-line artifacts: `nexary-job-spring-boot2-starter` and `nexary-job-spring-boot4-starter`. Business job code does not change when the starter or provider line changes.

Spring Boot 3.3 / Java 17+ Maven starter mode:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.aweimao</groupId>
      <artifactId>nexary-bom</artifactId>
      <version>${nexaryVersion}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependency>
  <groupId>com.aweimao</groupId>
  <artifactId>nexary-job-spring-boot-starter</artifactId>
</dependency>
```

Spring Boot 2.7 / Java 8+ starter mode:

```xml
<dependency>
  <groupId>com.aweimao</groupId>
  <artifactId>nexary-job-spring-boot2-starter</artifactId>
  <version>${nexaryVersion}</version>
</dependency>
```

Spring Boot 4.1 / Java 21 primary validation runtime starter mode:

```xml
<dependency>
  <groupId>com.aweimao</groupId>
  <artifactId>nexary-job-spring-boot4-starter</artifactId>
  <version>${nexaryVersion}</version>
</dependency>
```

Spring Boot 3.3 / Java 17+ Gradle starter mode:

```groovy
def nexaryVersion = "0.11.0"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
// The starter aggregates Nexary job API, local scheduler, XXL-JOB, PowerJob,
// and the Redis execution store provider. Select the runtime provider with
// nexary.job.provider.
implementation 'com.aweimao:nexary-job-spring-boot-starter'
```

Spring Boot 2.7 / Java 8+ Gradle:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-spring-boot2-starter:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 primary validation runtime Gradle:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-spring-boot4-starter:${nexaryVersion}"
```

Single-provider mode is for applications that want exactly one concrete provider dependency. Business jobs still depend only on `NexaryJob`, `JobContext`, and `JobResult`.

Spring Boot 3.3 / Java 17+ local scheduler provider:

```groovy
def nexaryVersion = "0.11.0"
// Business code needs only the Nexary job API at compile time.
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-scheduler'
```

Spring Boot 3.3 / Java 17+ XXL-JOB bridge provider:

```groovy
def nexaryVersion = "0.11.0"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-xxljob'
```

Spring Boot 3.3 / Java 17+ PowerJob trigger provider:

```groovy
def nexaryVersion = "0.11.0"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-powerjob'
```

Spring Boot 3.3 / Java 17+ Redis completed-record store:

```groovy
def nexaryVersion = "0.11.0"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-execution-store-redis'
```

Spring Boot 2.7 / Java 8+ local scheduler provider:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-scheduler-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ XXL-JOB bridge provider:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-xxljob-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ PowerJob trigger provider:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ Redis completed-record store:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-execution-store-redis-spring-boot2:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 primary validation runtime local scheduler provider:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-scheduler-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 primary validation runtime XXL-JOB bridge provider:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-xxljob-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 primary validation runtime PowerJob trigger provider:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 primary validation runtime Redis completed-record store:

```groovy
def nexaryVersion = "0.11.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-execution-store-redis-spring-boot4:${nexaryVersion}"
```

Verified artifact names:

| Target | artifactId | Status |
| --- | --- | --- |
| Boot2 starter | `nexary-job-spring-boot2-starter` | verified bounded scope |
| Boot2 API | `nexary-job-api` | verified Java 8 bytecode |
| Boot2 local scheduler | `nexary-job-scheduler-spring-boot2` | verified |
| Boot2 XXL-JOB bridge | `nexary-job-xxljob-spring-boot2` | verified trigger-mapping entry |
| Boot2 PowerJob trigger | `nexary-job-powerjob-spring-boot2` | verified trigger-mapping entry |
| Boot2 Redis execution store | `nexary-job-execution-store-redis-spring-boot2` | verified completed-record store |
| Boot4 starter | `nexary-job-spring-boot4-starter` | verified Boot4 entry |
| Boot4 local scheduler | `nexary-job-scheduler-spring-boot4` | verified |
| Boot4 XXL-JOB bridge | `nexary-job-xxljob-spring-boot4` | verified trigger-mapping entry |
| Boot4 PowerJob trigger | `nexary-job-powerjob-spring-boot4` | verified trigger-mapping entry |
| Boot4 Redis execution store | `nexary-job-execution-store-redis-spring-boot4` | verified completed-record store |

## Adoption Modes

Business code should implement or call only Nexary job API:

- job handlers implement `NexaryJob`
- execution context uses `JobContext`
- outcomes use `JobResult`
- execution receipts use `JobExecutionListener`
- callers that need one execution trace use `NexaryJobOperations.triggerExecution(...)` and read `JobExecutionRecord`

Local scheduler, XXL-JOB, PowerJob, and processor startup belong to framework wiring. Applications can start with `nexary-job-spring-boot-starter`, which aggregates the current providers, or depend on `nexary-job-api` and add exactly one concrete provider for the selected runtime mode.

Switching between local scheduler, XXL-JOB, and PowerJob should not change business job handlers. Starter mode changes `nexary.job.provider` and related settings; without the starter, change the provider dependency and related settings.

Current samples:

- starter selector: `nexary-samples/nexary-sample-job`
- SPI local provider: `nexary-samples/nexary-sample-job-spi-scheduler`
- SPI XXL-JOB bridge provider: `nexary-samples/nexary-sample-job-spi-xxljob`
- SPI PowerJob trigger provider: `nexary-samples/nexary-sample-job-spi-powerjob`

## Execution Lifecycle

The Nexary v0.1 job execution lifecycle is Nexary-level:

- `JobExecutionId`: unique id for one execution
- `JobExecutionRecord`: trigger source, context, status, attempts, start/end timestamps, duration, message, and error
- `JobExecutionStatus`: shared `SUCCESS`, `FAILED`, `SKIPPED`, `TIMEOUT`, and `CANCELLED` states
- `JobExecutionPolicy`: timeout, retry attempts/backoff, concurrency behavior, misfire behavior, and single-instance lock lease

`triggerExecution(...)` returns the full execution record. The older `trigger(...)` method remains available for simple callers that only need `JobResult`. Direct triggers, local cron schedules, XXL-JOB triggers, and PowerJob triggers enter the same execution pipeline, so listener, retry, timeout, result mapping, and execution record semantics stay consistent.

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
- `job.powerjob.bridge.trigger`

Tags must stay bounded:

- `capability`: fixed `job`
- `operation`: event name
- `provider`: `local`, `xxljob`, `powerjob`, `memory`, `redis`, or `unknown`
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
- PowerJob trigger ingress: aggregate `job.powerjob.bridge.trigger`; this proves Nexary received the trigger, not the PowerJob Server scheduling lifecycle

To connect Prometheus or another Micrometer backend, add `nexary-observation-micrometer-spring-boot-starter`. The bridge consumes Nexary-level events and creates Micrometer meters without changing the public APIs of `NexaryJob`, the scheduler, or the XXL-JOB bridge. Custom company metrics platforms can still provide a `NexaryObservationListener`, but the tag whitelist above must be preserved.

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
- trigger-mapping trigger mapping
- shard parameter mapping
- the same execution lifecycle used by direct/local schedule execution: listener, retry, timeout, record, and result mapping

XXL-JOB itself owns Admin scheduling, routing strategies, sharding broadcast, and executor lifecycle. Nexary `nexary-job-xxljob` currently acts only as a bridge: it maps platform triggers and shard metadata into `NexaryJob` / `JobContext`. The current sample does not claim complete executor registration, Admin scheduling, or callback lifecycle validation.

## When to Use the PowerJob Trigger Provider

The PowerJob trigger provider fits teams that already run PowerJob and want platform triggers plus shard metadata mapped back into the shared `NexaryJob` abstraction.

The current sample covers:

- `nexary.job.provider=powerjob`
- PowerJob trigger metadata mapping
- shard parameter mapping
- the same execution lifecycle used by direct/local schedule execution: listener, retry, timeout, record, and result mapping

PowerJob Server, console, worker registration, platform scheduling, and complete callback lifecycle remain owned by the PowerJob platform. Nexary `nexary-job-powerjob` currently maps platform triggers and shard metadata into `NexaryJob` / `JobContext`. The current sample does not claim PowerJob Server scheduling, fully managed worker registration, console lifecycle, complete callback flow, exactly-once execution, or running cancellation.

## When to Use Processor-Style Integration

Processor-style integration fits production job executors deployed as independent processes. It does not start a web server. It starts as a non-web Spring Boot application, component-scans `NexaryJob` handlers, and executes business jobs through bridge or external-platform triggers.

The current reference skeleton lives in the `processor` subpackage of `nexary-samples/nexary-sample-job`. See [job-processor-style.md](job-processor-style.md).

## Recommended Order

1. understand `NexaryJob`, `JobContext`, `JobResult`, and `JobSchedule`
2. run starter selector mode in `nexary-sample-job`
3. inspect `nexary-sample-job-spi-scheduler` for API + local provider integration
4. inspect `nexary-sample-job-spi-xxljob` for API + bridge provider integration
5. inspect the processor-style skeleton if your production shape is a standalone job process
6. validate against [job-acceptance.md](job-acceptance.md)
7. run Docker middleware and integration validation when external platform validation is needed
