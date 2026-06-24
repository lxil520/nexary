# Job 指南

Job 能力需要单独看，因为“本地调度”和“外部平台触发桥接”不是同一件事。

## 你应该先看什么

- 模块入口：[../../nexary-job/README.md](../../nexary-job/README.md)
- 验收清单：[job-acceptance.md](job-acceptance.md)
- Job 专项样例：[../../nexary-samples/nexary-sample-job/README.md](../../nexary-samples/nexary-sample-job/README.md)
- Processor-style 集成：[job-processor-style.md](job-processor-style.md)
- 模块说明：[modules.md](modules.md)

## 已经支持

- `nexary-job-api`：统一任务 API
- `nexary-job-scheduler`：本地调度实现
- `nexary-job-xxljob`：XXL-JOB bridge
- `nexary-job-powerjob`：PowerJob 触发映射
- `nexary-job-execution-store-redis`：可选 Redis durable execution store

## 核心边界

- `NexaryJob` 是统一任务抽象。
- 本地调度是框架原生执行模式。
- XXL-JOB 是外部平台触发到 `NexaryJob` 的 bridge，不是第二套公开 job API。
- PowerJob 也只把外部平台触发映射到 `NexaryJob`，不改变当前公共 API。

## 版本入口与依赖选择

当前开发版使用 `0.8.0`。发布到 Maven Central 后，把示例里的 `nexaryVersion` 替换为最新 release。

| Spring Boot | JDK | 状态 | 推荐入口 |
| --- | --- | --- | --- |
| Spring Boot 3.3.x | Java 17+ | 当前已验证 | `com.aweimao:nexary-job-spring-boot-starter` |
| Spring Boot 2.7.x | Java 8+ | 已验证受限边界 | `com.aweimao:nexary-job-spring-boot2-starter` |
| Spring Boot 4.1.x | Java 21 是 Nexary 主验证运行时；官方最低 JDK 以 Spring 官方文档为准 | 已验证 Boot4 provider/starter | `com.aweimao:nexary-job-spring-boot4-starter` |

当前 Boot3 主线 starter 仍叫 `nexary-job-spring-boot-starter`。Boot2 和 Boot4 使用显式版本线 artifact：`nexary-job-spring-boot2-starter`、`nexary-job-spring-boot4-starter`。业务 job 代码不随 starter/provider 切换而变化。

Spring Boot 3.3 / Java 17+ Maven starter 模式：

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

Spring Boot 2.7 / Java 8+ starter 模式：

```xml
<dependency>
  <groupId>com.aweimao</groupId>
  <artifactId>nexary-job-spring-boot2-starter</artifactId>
  <version>${nexaryVersion}</version>
</dependency>
```

Spring Boot 4.1 / Java 21 主验证运行时 starter 模式：

```xml
<dependency>
  <groupId>com.aweimao</groupId>
  <artifactId>nexary-job-spring-boot4-starter</artifactId>
  <version>${nexaryVersion}</version>
</dependency>
```

Spring Boot 3.3 / Java 17+ Gradle starter 模式：

```groovy
def nexaryVersion = "0.8.0"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
// starter 聚合 Nexary job API、local scheduler、XXL-JOB、PowerJob、
// Redis execution store provider；通过 nexary.job.provider 选择运行 provider。
implementation 'com.aweimao:nexary-job-spring-boot-starter'
```

Spring Boot 2.7 / Java 8+ Gradle：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-spring-boot2-starter:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 Gradle：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-spring-boot4-starter:${nexaryVersion}"
```

单 provider 模式适合只引入一个具体 provider。业务 job 仍只依赖 `NexaryJob`、`JobContext`、`JobResult`。

Spring Boot 3.3 / Java 17+ local scheduler provider：

```groovy
def nexaryVersion = "0.8.0"
// 业务代码编译期只需要 Nexary job API。
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-scheduler'
```

Spring Boot 3.3 / Java 17+ XXL-JOB bridge provider：

```groovy
def nexaryVersion = "0.8.0"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-xxljob'
```

Spring Boot 3.3 / Java 17+ PowerJob 触发 provider：

```groovy
def nexaryVersion = "0.8.0"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-powerjob'
```

Spring Boot 3.3 / Java 17+ Redis completed-record store：

```groovy
def nexaryVersion = "0.8.0"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-api'
runtimeOnly 'com.aweimao:nexary-job-execution-store-redis'
```

Spring Boot 2.7 / Java 8+ local scheduler provider：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-scheduler-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ XXL-JOB bridge provider：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-xxljob-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ PowerJob 触发 provider：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot2:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ Redis completed-record store：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-execution-store-redis-spring-boot2:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 local scheduler provider：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-scheduler-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 XXL-JOB bridge provider：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-xxljob-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 PowerJob 触发 provider：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot4:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 Redis completed-record store：

```groovy
def nexaryVersion = "0.8.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-execution-store-redis-spring-boot4:${nexaryVersion}"
```

已验证 artifact 命名：

| 目标 | artifactId | 状态 |
| --- | --- | --- |
| Boot2 starter | `nexary-job-spring-boot2-starter` | 已验证受限边界 |
| Boot2 API | `nexary-job-api` | 已验证 Java 8 bytecode |
| Boot2 local scheduler | `nexary-job-scheduler-spring-boot2` | 已验证 |
| Boot2 XXL-JOB bridge | `nexary-job-xxljob-spring-boot2` | 已验证触发映射入口 |
| Boot2 PowerJob trigger | `nexary-job-powerjob-spring-boot2` | 已验证触发映射入口 |
| Boot2 Redis execution store | `nexary-job-execution-store-redis-spring-boot2` | 已验证 completed-record store |
| Boot4 starter | `nexary-job-spring-boot4-starter` | 已验证 Boot4 入口 |
| Boot4 local scheduler | `nexary-job-scheduler-spring-boot4` | 已验证 |
| Boot4 XXL-JOB bridge | `nexary-job-xxljob-spring-boot4` | 已验证触发映射入口 |
| Boot4 PowerJob trigger | `nexary-job-powerjob-spring-boot4` | 已验证触发映射入口 |
| Boot4 Redis execution store | `nexary-job-execution-store-redis-spring-boot4` | 已验证 completed-record store |

## 接入模式

业务代码应该只实现或调用 Nexary job API：

- job handler 实现 `NexaryJob`
- 执行上下文使用 `JobContext`
- 结果使用 `JobResult`
- 执行回执使用 `JobExecutionListener`
- 需要追踪一次执行时，通过 `NexaryJobOperations.triggerExecution(...)` 获取 `JobExecutionRecord`

local scheduler、XXL-JOB、PowerJob 和 processor 启动方式属于框架接线。应用可以先使用 `nexary-job-spring-boot-starter` 聚合当前 provider，也可以只依赖 `nexary-job-api`，再按运行模式引入一个具体 provider。

切换 local scheduler、XXL-JOB 和 PowerJob 时，不应修改业务 job handler。starter 模式只改 `nexary.job.provider` 和相关配置；不用 starter 时只改 provider 依赖和相关配置。

当前样例：

- starter selector：`nexary-samples/nexary-sample-job`
- SPI local provider：`nexary-samples/nexary-sample-job-spi-scheduler`
- SPI XXL-JOB bridge provider：`nexary-samples/nexary-sample-job-spi-xxljob`
- SPI PowerJob trigger provider：`nexary-samples/nexary-sample-job-spi-powerjob`

## 执行生命周期

Nexary v0.1 的 job 执行生命周期是 Nexary 层 的：

- `JobExecutionId`：一次执行的唯一 ID
- `JobExecutionRecord`：记录触发来源、上下文、状态、尝试次数、开始/结束时间、耗时、消息和错误
- `JobExecutionStatus`：统一表达 `SUCCESS`、`FAILED`、`SKIPPED`、`TIMEOUT`、`CANCELLED`
- `JobExecutionPolicy`：统一表达 timeout、retry attempts/backoff、concurrency behavior、misfire behavior、single-instance lock lease

`triggerExecution(...)` 返回完整执行记录；旧的 `trigger(...)` 仍可用于只关心 `JobResult` 的简单场景。direct trigger、local cron schedule、XXL-JOB 触发和 PowerJob 触发都进入同一条执行管线，因此监听、重试、超时、结果映射和执行记录语义一致。

执行记录默认写入 in-memory store，适合本地开发和轻量测试。需要跨进程、重启后查询记录时，可以启用 Redis durable store：

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

Redis store 只保存已完成的 execution record，并按 `retention` 设置 Redis TTL。它不提供 running cancellation，也不替代业务审计库；如果需要长期审计，应把 `JobExecutionListener` 的回执同步到自己的审计存储。

取消语义需要分清：

- `cancel(jobName)`：取消 provider 已注册的后续 schedule。local scheduler 支持；XXL-JOB bridge 的 schedule 属于外部平台，不在 Nexary 内取消。
- `cancelExecution(executionId)`：取消正在运行的执行。v0.1 明确不支持 running cancellation，默认返回 `false`。

## 可观测事件与指标

Job provider 复用 `nexary-framework:nexary-core` 的 `NexaryObservationPublisher` / `NexaryObservationListener`。业务 `NexaryJob` 不需要依赖 Micrometer、Redis、XXL-JOB 或本地 scheduler 类型；只要应用提供 observation publisher 或 listener，Nexary job provider 会在框架执行路径里发事件。

当前 job 事件名可以直接作为指标桥接名：

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

标签必须是有界标签：

- `capability`：固定为 `job`
- `operation`：事件名
- `provider`：`local`、`xxljob`、`powerjob`、`memory`、`redis` 或 `unknown`
- `trigger`：`direct`、`scheduled`、`bridge` 或 `unknown`
- `status`：`success`、`failed`、`skipped`、`timeout`、`accepted`、`running`、`expired` 等有限状态
- `skip_reason`：`none`、`misfire`、`concurrency`、`single_instance`、`shard_assignment`、`unknown`
- `shard_presence`：`true` / `false` / `unknown`
- `failure_category`：`none`、`timeout`、`application`、`system`
- `retry_attempt_bucket`：`none`、`1`、`2_3`、`4_plus`
- `retry_phase`：`first`、`retry`、`final`
- `store`：`memory`、`redis`

禁止把下列内容放进标签：execution id、job 参数、payload、异常消息、堆栈、cache key、message id、lock token、fencing token 或任意用户输入。

Dashboard 可以从这些事件汇总：

- Job 执行成功率：按 `provider`、`trigger`、`status` 聚合 `job.execution.end`
- 执行耗时：使用 `job.execution.end` 的 event duration，按 `provider`、`trigger`、`shard_presence` 分组
- 重试热度：按 `retry_phase` / `retry_attempt_bucket` 聚合 `job.retry.attempt`
- 跳过原因：按 `skip_reason` 聚合 `job.execution.skip`
- Store 健康：按 `store`、`status` 聚合 `job.store.save`、`job.store.find`、`job.store.retention_expiry`
- XXL-JOB bridge 入口量：聚合 `job.xxljob.bridge.trigger`，只代表 bridge 入口，不代表 Admin 调度链路
- PowerJob 触发入口量：聚合 `job.powerjob.bridge.trigger`，只代表 Nexary 接收到触发，不代表 PowerJob Server 调度链路

需要接 Prometheus 或其他 Micrometer 后端时，引入 `nexary-observation-micrometer-spring-boot-starter`。该 bridge 只消费 Nexary 层 event 并生成 Micrometer meter，不改变 `NexaryJob`、scheduler 或 XXL-JOB bridge 的 public API。自定义企业指标平台仍可提供 `NexaryObservationListener`，转换时必须继续遵守上面的标签白名单。

## 本地调度适合什么

本地调度适合服务内部的轻量定时任务、补偿任务、对账任务和演示环境。

当前样例覆盖：

- Spring 扫描 `NexaryJob` Bean
- 通过 `NexaryJob.name()` 建立任务名映射
- 直接执行业务 job
- cron 调度注册
- 单实例执行：存在 Nexary cache provider 时使用分布式锁避免重复执行
- 本地分布式分片：通过 `JobSchedule` 或 `nexary.job.scheduler.*` 提供 worker 信息
- 内置负载算法：`round_robin`、`random`、`consistent_hash`、`least_active`、`first_available`

本地分布式调度的边界：

- `worker-id` 表示当前进程。
- `workers` 表示当前调度拓扑。
- `load-balance` 决定 shard 分配到哪个 worker。
- 有 Nexary `CacheClient` 且配置 `worker-id` 时，local scheduler 会写入 heartbeat，并按 `heartbeat-ttl` 剔除过期 worker。
- `topology` 用于隔离不同应用或部署环境的 worker 注册表。
- `execution-timeout`、`retry-attempts`、`retry-backoff`、`concurrency-policy`、`misfire-policy`、`misfire-threshold`、`lock-lease-time` 控制本地执行生命周期。
- 未配置 `workers` 时保持单进程行为，本地节点执行全部 shard。
- 未配置静态 `workers` 但启用 cache heartbeat 时，worker 列表来自 cache 中的活跃 heartbeat。
- 业务 `NexaryJob` 不读取这些配置，只从 `JobContext` 接收 shard 信息。

## XXL-JOB bridge 适合什么

XXL-JOB bridge 适合已经有 XXL-JOB 平台的团队，把平台触发映射回统一的 `NexaryJob`。

当前样例覆盖：

- `xxljob` profile
- 触发映射 触发映射
- 分片参数映射
- 与 direct/local schedule 相同的 execution lifecycle：listener、retry、timeout、record、result mapping

XXL-JOB 平台本身负责 Admin 调度、路由策略、分片广播和 executor 生命周期。Nexary `nexary-job-xxljob` 当前只做 bridge：把平台触发和分片参数映射到 `NexaryJob` / `JobContext`。当前样例不声称已经完成完整 executor 注册、Admin 调度、回调生命周期验证。

## PowerJob 触发接入适合什么

PowerJob 触发接入适合已经有 PowerJob 平台的团队，把平台触发和分片信息映射回统一的 `NexaryJob`。

当前样例覆盖：

- `nexary.job.provider=powerjob`
- PowerJob 触发元数据映射
- 分片参数映射
- 与 direct/local schedule 相同的 execution lifecycle：listener、retry、timeout、record、result mapping

PowerJob Server、控制台、worker 注册、平台调度和完整 callback 生命周期仍由 PowerJob 平台负责。Nexary `nexary-job-powerjob` 当前只做触发映射：把平台触发和分片参数映射到 `NexaryJob` / `JobContext`。当前样例不声称已经完成 PowerJob Server 调度、worker 注册完整托管、控制台生命周期、完整回调流程、exactly-once 或运行中强取消。

## Processor-style 适合什么

Processor-style 适合生产中独立部署的任务执行进程。它不启动 Web Server，而是以 Spring Boot 非 Web 应用启动，扫描 `NexaryJob` 组件，并通过 bridge 或外部平台触发执行业务 job。

当前参考骨架位于 `nexary-samples/nexary-sample-job` 的 `processor` 子包，详见 [job-processor-style.md](job-processor-style.md)。

## 推荐接入顺序

1. 先理解 `NexaryJob`、`JobContext`、`JobResult`、`JobSchedule`
2. 用 `nexary-sample-job` 跑 starter selector 模式
3. 用 `nexary-sample-job-spi-scheduler` 理解 API + local provider 引入
4. 用 `nexary-sample-job-spi-xxljob` 理解 API + bridge provider 引入
5. 如果你的生产形态是独立任务进程，再看 processor-style skeleton
6. 根据 [job-acceptance.md](job-acceptance.md) 做独立验收
7. 需要外部平台验证时，再跑 Docker 中间件和集成验证
