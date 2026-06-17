# Nexary Job

- 中文入口：[../docs/zh/job.md](../docs/zh/job.md)
- English entry: [../docs/en/job.md](../docs/en/job.md)

本目录是 Nexary 的任务能力入口，不是全仓库总览。

当前关注：

- `nexary-job-api`
- `nexary-job-scheduler`
- `nexary-job-xxljob`
- `nexary-job-execution-store-redis`

执行记录默认使用 in-memory store。需要跨进程或重建后查询 execution record 时，启用可选 Redis durable store：

```yaml
nexary:
  job:
    execution:
      store:
        redis:
          enabled: true
          retention: 1d
```

Redis store 只保存已完成的 execution record，并按 TTL 过期；running cancellation 仍不是当前能力。

可观测事件复用 `nexary-framework:nexary-core` 的 `NexaryObservationPublisher` / `NexaryObservationListener`。Job provider 会发出 `job.trigger`、`job.execution.end`、`job.retry.attempt`、`job.execution.skip`、`job.store.save`、`job.store.find`、`job.scheduler.run`、`job.xxljob.bridge.trigger` 等 provider-neutral event。标签只允许有限维度，例如 `capability`、`operation`、`provider`、`trigger`、`status`、`skip_reason`、`shard_presence`、`failure_category`，不能包含 execution id、参数、payload、异常消息或堆栈。

验收清单：

- 中文：[../docs/zh/job-acceptance.md](../docs/zh/job-acceptance.md)
- English: [../docs/en/job-acceptance.md](../docs/en/job-acceptance.md)
