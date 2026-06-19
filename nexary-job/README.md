# Nexary Job

- 中文入口：[../docs/zh/job.md](../docs/zh/job.md)
- English entry: [../docs/en/job.md](../docs/en/job.md)

这里只讲 Job。如果你只是想找 cron 配置，看下面这段。

Cron 配置入口：

```yaml
nexary:
  job:
    provider: local
    scheduler:
      schedules:
        - job-name: sample-business-job
          cron: "0 */10 * * * *"
```

`job-name` 对应业务类 `NexaryJob.name()`。完整配置字段见 [docs/zh/configuration.md](../docs/zh/configuration.md) / [docs/en/configuration.md](../docs/en/configuration.md)。

本目录包括：

- `nexary-job-api`
- `nexary-job-scheduler`
- `nexary-job-xxljob`
- `nexary-job-execution-store-redis`

## 版本入口

现在开发版是 `0.2.0-SNAPSHOT`。发布到 Maven Central 后，把示例里的 `nexaryVersion` 换成最新 release。

| Spring Boot | JDK | Starter 入口 | Provider 入口 |
| --- | --- | --- | --- |
| 3.3.x | Java 17+ | `nexary-job-spring-boot-starter` | `nexary-job-scheduler` / `nexary-job-xxljob` / `nexary-job-execution-store-redis` |
| 2.7.x | Java 8+ | `nexary-job-spring-boot2-starter` | `nexary-job-scheduler-spring-boot2` / `nexary-job-xxljob-spring-boot2` / `nexary-job-execution-store-redis-spring-boot2` |
| 4.1.x | Java 21 是 Nexary 主验证运行时；官方最低 JDK 以 Spring 官方文档为准 | `nexary-job-spring-boot4-starter` | `nexary-job-scheduler-spring-boot4` / `nexary-job-xxljob-spring-boot4` / `nexary-job-execution-store-redis-spring-boot4` |

Spring Boot 3.3 / Java 17+ starter 模式：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-spring-boot-starter'
```

Spring Boot 2.7 / Java 8+ starter 模式：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-spring-boot2-starter:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 starter 模式：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-spring-boot4-starter:${nexaryVersion}"
```

如果不用 starter，就自己选一个 provider。业务代码只依赖 `nexary-job-api`。

Spring Boot 3.3 / Java 17+ local scheduler provider：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-api'
runtimeOnly 'org.nexary:nexary-job-scheduler'
```

Spring Boot 3.3 / Java 17+ XXL-JOB bridge provider：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-api'
runtimeOnly 'org.nexary:nexary-job-xxljob'
```

Spring Boot 3.3 / Java 17+ Redis completed-record store：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-api'
runtimeOnly 'org.nexary:nexary-job-execution-store-redis'
```

Spring Boot 2.7 / Java 8+ local scheduler provider：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-scheduler-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ XXL-JOB bridge provider：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-xxljob-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ Redis completed-record store：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-execution-store-redis-spring-boot2:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 local scheduler provider：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-scheduler-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 XXL-JOB bridge provider：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-xxljob-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 Redis completed-record store：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-execution-store-redis-spring-boot4:${nexaryVersion}"
```

XXL-JOB provider 只做触发映射：把外部触发和分片参数交给 `NexaryJob`。它不声明已经接管 XXL-JOB Admin 调度、executor 注册、callback lifecycle 或平台触发执行。

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

Redis store 只保存已完成执行记录，并按 TTL 过期；还不能取消正在运行的任务。

可观测事件复用 `nexary-framework:nexary-core` 的 `NexaryObservationPublisher` / `NexaryObservationListener`。Job provider 会发出 `job.trigger`、`job.execution.end`、`job.retry.attempt`、`job.execution.skip`、`job.store.save`、`job.store.find`、`job.scheduler.run`、`job.xxljob.bridge.trigger` 等事件。指标标签只能用有限维度，例如 `capability`、`operation`、`provider`、`trigger`、`status`、`skip_reason`、`shard_presence`、`failure_category`，不要把 execution id、参数、payload、异常消息或堆栈放进标签。

验收清单：

- 中文：[../docs/zh/job-acceptance.md](../docs/zh/job-acceptance.md)
- English: [../docs/en/job-acceptance.md](../docs/en/job-acceptance.md)
