# Nexary Job

- 中文入口：[../docs/zh/job.md](../docs/zh/job.md)
- English entry: [../docs/en/job.md](../docs/en/job.md)

本目录是 Nexary 的任务能力入口，不是全仓库总览。

当前关注：

- `nexary-job-api`
- `nexary-job-scheduler`
- `nexary-job-xxljob`
- `nexary-job-execution-store-redis`

## 版本入口

当前已验证组合是 Spring Boot 3.3.x + Java 17+：

```groovy
def nexaryVersion = "0.2.0-alpha.2"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-spring-boot-starter'
```

如果不用 starter，可以按 SPI/provider 模式选择依赖：

```groovy
def nexaryVersion = "0.2.0-alpha.2"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-api'
runtimeOnly 'org.nexary:nexary-job-scheduler' // local scheduler provider
// runtimeOnly 'org.nexary:nexary-job-xxljob' // XXL-JOB bridge provider
// runtimeOnly 'org.nexary:nexary-job-execution-store-redis' // optional durable records
```

Spring Boot 2.7 + Java 8+ 和 Spring Boot 4.x + Java 21+ 目前是兼容目标，不是已发布支持。拟定 artifactId 分别是 `nexary-job-spring-boot2-starter` 和 `nexary-job-spring-boot4-starter`；Boot2 provider 线拟定使用 `nexary-job-api-java8`、`nexary-job-scheduler-spring5`、`nexary-job-xxljob-spring5`、`nexary-job-execution-store-redis-spring5`。这些名称在通过独立验证前不能写成可用依赖。

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
