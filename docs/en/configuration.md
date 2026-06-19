# Configuration

This page answers where configuration goes. Business code implements Nexary APIs; provider selection, cron schedules, retry policy, and execution record retention live in `application.yml`.

## Where to Configure Job Cron

The local scheduler provider registers cron schedules from `nexary.job.scheduler.schedules`. `job-name` must match the value returned by `NexaryJob.name()`.

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

This configuration is equivalent to registering the schedule at startup:

```java
jobs.schedule(JobSchedule.single("sample-business-job", "0 */10 * * * *"));
```

## Job Configuration

| Property | Default | Meaning |
| --- | --- | --- |
| `nexary.job.provider` | `local` | Selects the Job provider. The current starter supports `local` and bridge-shaped `xxljob`. |
| `nexary.job.scheduler.schedules[].job-name` | none | The target `NexaryJob.name()`. A wrong name fails when the schedule is registered at startup. |
| `nexary.job.scheduler.schedules[].cron` | none | Spring cron expression used by the local scheduler through `CronTrigger`. |
| `nexary.job.scheduler.schedules[].enabled` | `true` | Whether this schedule is registered. |
| `nexary.job.scheduler.schedules[].single-instance` | `true` | Whether this schedule runs as a single-instance job. When a CacheClient is present, it can use the distributed lock path. |
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
| `nexary.job.scheduler.execution-record-retention` | `1d` | In-memory execution record retention when no durable store is configured. |
| `nexary.job.execution.store.redis.enabled` | `false` | Enables the Redis completed-record store. |
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

## Boundaries

- `schedules` belongs to the local scheduler only. XXL-JOB bridge timing is still owned by XXL-JOB Admin.
- Business job classes do not read these properties; they receive shard metadata through `JobContext`.
- The Redis execution store saves completed execution records only. It is not a business audit database.
- Running execution cancellation is not supported in the current provider set.

## Other Capability Configuration

- Cache: see [cache.md](cache.md)
- Messaging: see [messaging.md](messaging.md)
- Observation: see [observation.md](observation.md)
