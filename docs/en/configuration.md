# Configuration

This page keeps copyable `application.yml` snippets in one place. Business classes keep using `NexaryJob`, `CacheClient`, and `MessagePublisher`; provider choice, cron schedules, retry policy, and execution record retention are configured here.

## Governance Configuration

```yaml
nexary:
  governance:
    runtime:
      enabled: true
    default-policy:
      max-requests-per-window: 100
      rate-limit-window: 1s
      max-concurrency: 64
    resources:
      profile-api:
        kind: http
        name: profile-api
        operation: get-profile
        deadline: 300ms
        max-requests-per-window: 2
        rate-limit-window: 1m
        max-concurrency: 1
```

| Property | Default | Meaning |
| --- | --- | --- |
| `nexary.governance.runtime.enabled` | `true` | Creates the local `GovernanceRuntime`. |
| `nexary.governance.default-policy.deadline` | none | Maximum time allowed when no resource policy matches. |
| `nexary.governance.default-policy.max-requests-per-window` | unlimited | Starts allowed in the default rate-limit window. |
| `nexary.governance.default-policy.rate-limit-window` | `1s` | Default rate-limit window. |
| `nexary.governance.default-policy.max-concurrency` | unlimited | Default concurrency limit. |
| `nexary.governance.default-policy.degraded` | `false` | Sends calls to fallback by default. Usually keep this off globally. |
| `nexary.governance.resources.<id>.kind` | `custom` | Resource type: `http`, `downstream`, `cache`, `messaging`, `job`, `service`, or `custom`. |
| `nexary.governance.resources.<id>.name` | `<id>` | Stable resource name. Do not use user ids, order ids, keys, or message ids. |
| `nexary.governance.resources.<id>.provider` | `nexary` | Backend tag such as `redis`, `kafka`, or `rocketmq`. |
| `nexary.governance.resources.<id>.operation` | `default` | Stable operation name such as `get-profile`, `cache.get`, or `publish`. |
| `nexary.governance.resources.<id>.priorities.<priority>.*` | none | Overrides the resource policy for `low`, `normal`, or `high` priority traffic. |

## Where to Configure Job Cron

With the local scheduler, cron schedules go under `nexary.job.scheduler.schedules`. `job-name` must match the value returned by `NexaryJob.name()`.

```yaml
nexary:
  job:
    provider: local
    scheduler:
      schedules:
        - job-name: sample-business-job
          cron: "0 */10 * * * *"
          enabled: true
          single-instance: true
          shard-total: 1
```

If code registration is a better fit, this call is equivalent to the YAML above:

```java
jobs.schedule(JobSchedule.single("sample-business-job", "0 */10 * * * *"));
```

## Job Configuration

| Property | Default | Meaning |
| --- | --- | --- |
| `nexary.job.provider` | `local` | Selects the Job runtime mode. The starter supports `local` and `xxljob`. |
| `nexary.job.scheduler.schedules[].job-name` | none | The target `NexaryJob.name()`. A wrong name fails when the schedule is registered at startup. |
| `nexary.job.scheduler.schedules[].cron` | none | Spring cron expression used by the local scheduler through `CronTrigger`. |
| `nexary.job.scheduler.schedules[].enabled` | `true` | Whether this schedule is registered. |
| `nexary.job.scheduler.schedules[].single-instance` | `true` | Whether this schedule runs as a single-instance job. With a `CacheClient`, it can use the distributed lock path. |
| `nexary.job.scheduler.schedules[].shard-total` | `1` | Total shard count. Keep `1` when the job is not sharded. |
| `nexary.job.scheduler.schedules[].load-balance` | global `load-balance` | Shard assignment strategy: `round_robin`, `random`, `consistent_hash`, `least_active`, `first_available`. |
| `nexary.job.scheduler.schedules[].worker-id` | global `worker-id` | Target worker for this schedule. Usually not needed. |
| `nexary.job.scheduler.schedules[].worker-ids` | global `workers` | Worker list for this schedule. Usually not needed. |
| `nexary.job.scheduler.execution-timeout` | `5m` | Maximum runtime for one execution attempt. |
| `nexary.job.scheduler.retry-attempts` | `0` | Retries after the first failed attempt. |
| `nexary.job.scheduler.retry-backoff` | `0s` | Delay between retry attempts. |
| `nexary.job.scheduler.concurrency-policy` | `allow` | Behavior when the same job shard is already running: `allow` or `skip_if_running`. |
| `nexary.job.scheduler.misfire-policy` | `fire_once` | Behavior for late scheduled runs: `fire_once` or `skip`. |
| `nexary.job.scheduler.misfire-threshold` | `1m` | Delay threshold before a run is treated as a misfire. |
| `nexary.job.scheduler.lock-lease-time` | `5m` | Single-instance lock lease. |
| `nexary.job.scheduler.start-deadline` | none | Skips this run when the trigger is older than this delay. |
| `nexary.job.scheduler.max-concurrent-executions` | unlimited | Concurrent starts allowed for the same job trigger. |
| `nexary.job.scheduler.execution-record-retention` | `1d` | In-memory execution record retention when no durable store is configured. |
| `nexary.job.xxljob.start-deadline` | none | Skips an XXL-JOB bridge trigger when it enters Nexary too late. |
| `nexary.job.xxljob.max-concurrent-executions` | unlimited | Concurrent starts allowed for the same XXL-JOB bridge job. |
| `nexary.job.powerjob.start-deadline` | none | Skips a PowerJob bridge trigger when it enters Nexary too late. |
| `nexary.job.powerjob.max-concurrent-executions` | unlimited | Concurrent starts allowed for the same PowerJob bridge job. |
| `nexary.job.execution.store.redis.enabled` | `false` | Saves completed execution records to Redis. |
| `nexary.job.execution.store.redis.key-prefix` | `nexary:job:execution:` | Redis execution record key prefix. |
| `nexary.job.execution.store.redis.retention` | `1d` | Redis execution record TTL. |

## Sharded Schedule Example

```yaml
nexary:
  job:
    scheduler:
      worker-id: node-a
      workers:
        - node-a
        - node-b
      load-balance: round_robin
      schedules:
        - job-name: sample-business-job
          cron: "0 */5 * * * *"
          single-instance: false
          shard-total: 4
```

## Watchouts

- `schedules` belongs to the local scheduler only. XXL-JOB bridge timing is still owned by XXL-JOB Admin.
- Business job classes do not read these properties; they receive shard metadata through `JobContext`.
- The Redis execution store saves completed execution records only. It is not a business audit database.
- `start-deadline` decides whether a trigger should start; `execution-timeout` limits an execution after it has started.

## Other Capability Configuration

- Cache: see [cache.md](cache.md)
- Messaging: see [messaging.md](messaging.md)
- Observation: see [observation.md](observation.md)
- Governance: see [governance.md](governance.md)
