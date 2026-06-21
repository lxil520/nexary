# Nexary Job

- 中文入口：[../docs/zh/job.md](../docs/zh/job.md)
- English entry: [../docs/en/job.md](../docs/en/job.md)

本目录是 Nexary 的任务模块入口，不是全仓库总览。

当前关注：

- `nexary-job-api`
- `nexary-job-scheduler`
- `nexary-job-xxljob`
- `nexary-job-powerjob`
- `nexary-job-execution-store-redis`

## 版本入口

当前开发版使用 `0.3.1`。发布到 Maven Central 后，把示例里的 `nexaryVersion` 替换为最新 release。

| Spring Boot | JDK | Starter 入口 | Provider 入口 |
| --- | --- | --- | --- |
| 3.3.x | Java 17+ | `nexary-job-spring-boot-starter` | `nexary-job-scheduler` / `nexary-job-xxljob` / `nexary-job-powerjob` / `nexary-job-execution-store-redis` |
| 2.7.x | Java 8+ | `nexary-job-spring-boot2-starter` | `nexary-job-scheduler-spring-boot2` / `nexary-job-xxljob-spring-boot2` / `nexary-job-powerjob-spring-boot2` / `nexary-job-execution-store-redis-spring-boot2` |
| 4.1.x | Java 21 是 Nexary 主验证运行时；官方最低 JDK 以 Spring 官方文档为准 | `nexary-job-spring-boot4-starter` | `nexary-job-scheduler-spring-boot4` / `nexary-job-xxljob-spring-boot4` / `nexary-job-powerjob-spring-boot4` / `nexary-job-execution-store-redis-spring-boot4` |

Spring Boot 3.3 / Java 17+ starter 模式：

```groovy
def nexaryVersion = "0.3.1"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-spring-boot-starter'
```

Spring Boot 2.7 / Java 8+ starter 模式：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-spring-boot2-starter:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 starter 模式：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-spring-boot4-starter:${nexaryVersion}"
```

如果不用 starter，可以按 SPI/provider 模式选择依赖。业务代码只依赖 `nexary-job-api`，provider 按运行环境选择一个。

Spring Boot 3.3 / Java 17+ local scheduler provider：

```groovy
def nexaryVersion = "0.3.1"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-scheduler'
```

Spring Boot 3.3 / Java 17+ XXL-JOB bridge provider：

```groovy
def nexaryVersion = "0.3.1"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-xxljob'
```

Spring Boot 3.3 / Java 17+ PowerJob trigger provider：

```groovy
def nexaryVersion = "0.3.1"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-powerjob'
```

Spring Boot 3.3 / Java 17+ Redis completed-record store：

```groovy
def nexaryVersion = "0.3.1"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-execution-store-redis'
```

Spring Boot 2.7 / Java 8+ local scheduler provider：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-scheduler-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ XXL-JOB bridge provider：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-xxljob-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ PowerJob trigger provider：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ Redis completed-record store：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-execution-store-redis-spring-boot2:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 local scheduler provider：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-scheduler-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 XXL-JOB bridge provider：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-xxljob-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 PowerJob trigger provider：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 Redis completed-record store：

```groovy
def nexaryVersion = "0.3.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-execution-store-redis-spring-boot4:${nexaryVersion}"
```

XXL-JOB 和 PowerJob provider 只负责把外部平台触发和分片元数据映射到 `NexaryJob`，执行仍进入 Nexary 统一执行管线。它们不声明真实平台调度、worker/executor 注册生命周期、完整回调流程、平台托管触发、exactly-once 或运行中强取消。

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

可观测事件复用 `nexary-framework:nexary-core` 的 `NexaryObservationPublisher` / `NexaryObservationListener`。Job provider 会发出 `job.trigger`、`job.execution.end`、`job.retry.attempt`、`job.execution.skip`、`job.store.save`、`job.store.find`、`job.scheduler.run`、`job.xxljob.bridge.trigger`、`job.powerjob.bridge.trigger` 等 event。标签只允许有限维度，例如 `capability`、`operation`、`provider`、`trigger`、`status`、`skip_reason`、`shard_presence`、`failure_category`，不能包含 execution id、参数、payload、异常消息或堆栈。

验收清单：

- 中文：[../docs/zh/job-acceptance.md](../docs/zh/job-acceptance.md)
- English: [../docs/en/job-acceptance.md](../docs/en/job-acceptance.md)
