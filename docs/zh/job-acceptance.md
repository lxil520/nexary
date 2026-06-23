# Job 验收清单

## API

- `NexaryJob` 是唯一公开业务任务抽象。
- `JobContext` 能表达任务名、计划时间、分片信息和扩展参数。
- `JobResult` 使用 enum 状态表达成功或失败，不使用魔法字符串。
- `JobSchedule` 能表达 cron、单实例执行、分片总数、worker 信息和负载算法。
- `JobExecutionId`、`JobExecutionRecord`、`JobExecutionStatus` 能表达 Nexary 层 的执行身份和状态。
- `JobExecutionPolicy` 能表达 timeout、retry attempts/backoff、concurrency behavior、misfire behavior 和 single-instance lock lease。
- `JobExecutionStore` 是 Nexary 层 的执行记录存储抽象，默认 in-memory，可替换为 durable provider。
- 负载算法抽象至少覆盖 `round_robin`、`random`、`consistent_hash`、`least_active`、`first_available`。
- 公开 API 不暴露 XXL-JOB 或 PowerJob 原生类型。

## 本地调度

- 支持直接执行。
- 支持 cron 调度注册。
- 支持取消本地调度。
- 支持执行监听。
- direct trigger 和 cron schedule 必须进入同一条 execution lifecycle 管线。
- single-instance lock lease 不能硬编码，必须来自配置或 schedule execution policy。
- skipped single-instance、skipped shard assignment、timeout、retry 后成功/失败都应生成 `JobExecutionRecord`。
- direct trigger、cron schedule 和 skipped paths 都必须通过 `JobExecutionStore` 写入记录。
- 单实例语义要说明是否依赖外部锁能力。
- 分布式分片语义要说明 worker 拓扑来源和未配置时的单进程降级行为。
- 有 Nexary `CacheClient` 时，local scheduler 能通过 heartbeat 注册当前 worker，并按 TTL 剔除过期 worker。
- 本地分片只能保证 Nexary scheduler 的 shard 分配，不替代外部平台调度。

## XXL-JOB bridge

- bridge 复用 `NexaryJob`，不创建第二套公开任务 API。
- bridge 能把外部平台的分片信息映射到 `JobContext`。
- bridge 执行后仍触发统一的监听器。
- bridge trigger 必须进入同一条 execution lifecycle 管线，统一 listener、retry、timeout、result mapping 和 execution record。
- bridge trigger 必须通过同一个 `JobExecutionStore` 写入记录，并保留 bridge trigger 与 shard metadata。
- XXL-JOB 的 Admin 调度、路由策略、分片广播和 executor 生命周期属于 XXL-JOB 平台能力，不在 Nexary local scheduler 内重复实现。
- 文档不能把 Admin 健康检查说成完整 executor 生命周期验证。

## 样例

- `nexary-sample-job` 是 Spring Boot 工程。
- `nexary-sample-job` 是 starter selector 样例，不包含 provider wiring 包。
- 样例主代码把业务 job handler 作为用户首拷贝代码。
- 业务 job handler 只依赖 Nexary job API，不依赖 `mode.local`、`mode.xxljob` 或 provider wiring。
- local scheduler 切到 XXL-JOB bridge 时，不修改业务 job 代码。
- starter selector 通过 `nexary.job.provider` 和 profile 配置选择 provider。
- 单 provider 样例按 provider 独立模块拆分，不把多个 provider 放进一个 SPI 样例模块。
- `nexary-sample-job-spi-scheduler` 只展示 API + local provider。
- `nexary-sample-job-spi-xxljob` 只展示 API + XXL-JOB bridge provider。
- `nexary-sample-job-spi-powerjob` 只展示 API + PowerJob 触发 provider。
- `local` profile 通过测试展示任务名映射、直接执行和本地调度注册。
- `xxljob` profile 通过测试展示 bridge 触发形态和分片参数映射。
- 样例文档明确区分本地调度和 bridge 触发。
- 样例配置注释要说明 execution record 默认 in-memory，Redis durable store 需要显式开启。

## Durable execution store

- 保留 in-memory execution store 作为默认本地开发路径。
- Redis durable store 启用后，runner/store 对象重建后仍能按 `JobExecutionId` 查询记录。
- Redis durable store 必须覆盖 success、failure、retry success、timeout、skip、shard metadata、bridge metadata。
- retention / TTL 语义必须明确并有真实 Redis 集成测试。
- durable store 不改变 running cancellation 的 v0.1 non-goal。

## Processor-style 集成

- processor 样例以 `WebApplicationType.NONE` 启动，不启动 Web Server。
- job handler 使用 Spring `@Component` 注册，并实现 `NexaryJob`。
- 执行入口通过 `JobContext` 接收任务名、计划时间、分片信息和运行上下文。
- processor 样例主路径只展示非 Web 启动和业务 job handler，不引入额外回执仓库或协作者模拟类。

## PowerJob 边界

- PowerJob 触发映射复用 `NexaryJob`，不创建第二套公开任务 API。
- PowerJob 触发映射能把外部平台的分片信息映射到 `JobContext`。
- PowerJob 触发后仍进入同一条 execution lifecycle 管线，统一 listener、retry、timeout、result mapping 和 execution record。
- PowerJob 触发必须通过同一个 `JobExecutionStore` 写入记录，并保留 trigger 与 shard metadata。
- PowerJob Server 调度、worker 注册完整托管、控制台生命周期和完整 callback 生命周期属于 PowerJob 平台能力，不在 Nexary local scheduler 内重复实现。
- 文档不能把 PowerJob 触发映射说成完整平台生命周期验证。

## 集成验证

- 基础验证至少跑 job API、local scheduler、XXL-JOB bridge 和 job sample 测试。
- durable store 验证至少跑 Redis-backed store 真实中间件测试。
- processor-style 骨架至少要验证非 Web 启动、组件扫描和 bridge 触发映射。
- 外部平台验证需要明确区分“bridge 触发映射已验证”和“真实 executor 注册/平台触发已验证”。
- v0.1 running cancellation 是 explicit non-goal；`cancelExecution(executionId)` 返回 `false`，文档不得宣传为已支持。
