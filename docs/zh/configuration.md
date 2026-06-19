# 配置手册

这页回答“配置写在哪里”。业务代码只实现 Nexary API；provider、cron、重试、执行记录保留策略放在 `application.yml`。

## Job cron 放哪里

本地调度 provider 使用 `nexary.job.scheduler.schedules` 注册 cron。`job-name` 必须等于业务类 `NexaryJob.name()` 返回值。

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

这段配置等价于启动时调用：

```java
jobs.schedule(JobSchedule.single("sample-business-job", "0 */10 * * * *"));
```

## Job 常用配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `nexary.job.provider` | `local` | 选择 Job provider。当前 starter 支持 `local` 和 bridge-shaped `xxljob`。 |
| `nexary.job.scheduler.schedules[].job-name` | 无 | 要调度的 `NexaryJob.name()`。写错会在启动注册时失败。 |
| `nexary.job.scheduler.schedules[].cron` | 无 | Spring cron 表达式，当前 local scheduler 使用 `CronTrigger`。 |
| `nexary.job.scheduler.schedules[].enabled` | `true` | 是否注册这一条 cron。 |
| `nexary.job.scheduler.schedules[].single-instance` | `true` | 是否按单实例执行。存在 CacheClient 时可配合分布式锁。 |
| `nexary.job.scheduler.schedules[].shard-total` | `1` | 分片总数。未做分片时保持 `1`。 |
| `nexary.job.scheduler.schedules[].load-balance` | 全局 `load-balance` | 分片分配算法：`round_robin`、`random`、`consistent_hash`、`least_active`、`first_available`。 |
| `nexary.job.scheduler.schedules[].worker-id` | 全局 `worker-id` | 指定当前 schedule 的目标 worker。通常不需要配置。 |
| `nexary.job.scheduler.schedules[].worker-ids` | 全局 `workers` | 指定当前 schedule 的 worker 列表。通常不需要配置。 |
| `nexary.job.scheduler.execution-timeout` | `5m` | 单次执行尝试的最长时间。 |
| `nexary.job.scheduler.retry-attempts` | `0` | 首次失败后的重试次数。 |
| `nexary.job.scheduler.retry-backoff` | `0s` | 两次重试之间的等待时间。 |
| `nexary.job.scheduler.concurrency-policy` | `allow` | 同一个 job shard 正在运行时的处理方式：`allow` 或 `skip_if_running`。 |
| `nexary.job.scheduler.misfire-policy` | `fire_once` | 调度迟到后的处理方式：`fire_once` 或 `skip`。 |
| `nexary.job.scheduler.misfire-threshold` | `1m` | 超过这个延迟才算 misfire。 |
| `nexary.job.scheduler.lock-lease-time` | `5m` | 单实例锁租约。 |
| `nexary.job.scheduler.execution-record-retention` | `1d` | 未启用 durable store 时，内存执行记录保留时间。 |
| `nexary.job.execution.store.redis.enabled` | `false` | 是否启用 Redis completed-record store。 |
| `nexary.job.execution.store.redis.key-prefix` | `nexary:job:execution:` | Redis 执行记录 key 前缀。 |
| `nexary.job.execution.store.redis.retention` | `1d` | Redis 执行记录 TTL。 |

## 分片示例

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

## 需要记住的边界

- `schedules` 只属于 local scheduler。XXL-JOB bridge 的调度时间仍由 XXL-JOB Admin 管。
- 业务 job 类不读取这些配置；它只通过 `JobContext` 接收 shard 信息。
- Redis execution store 只保存已完成的 execution record，不是业务审计库。
- 当前不支持取消正在运行的 execution。

## 其他能力配置入口

- Cache：见 [cache.md](cache.md)
- Messaging：见 [messaging.md](messaging.md)
- Observation：见 [observation.md](observation.md)
