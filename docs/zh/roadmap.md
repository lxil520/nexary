# 版本路线图

这份路线图用于说明版本边界和后续 issue / PR 的优先级。它不是内部任务记录，也不列出协作过程。

## 版本原则

- 先稳定一条现代主线，再考虑兼容分支。
- 新能力进入主线前，需要同时具备公共 API、样例、文档和测试证据。
- provider 集成优先服务统一抽象，不把每个中间件都放到同一优先级。
- 治理能力应独立成长，不能反向污染 cache / messaging / job 的主 API。

## `0.1.0` 基线

`0.1.0` 的目标是形成第一个可对外说明、可本地验证、可作为后续版本基础的稳定起点。

已纳入范围：

- Redis cache、TTL、batch、cache-aside、分布式锁抽象。
- Redis tiered cache mode，内部使用 JVM local L1，并提供 best-effort Redis Pub/Sub 失效。
- 独立 atomic counter API，Redis 单 key 原子计数实现。
- Kafka / RocketMQ / Redis queue / Disruptor 消息抽象。
- 重复消费保护、bounded retry、terminal failure / dead-letter 抽象。
- 本地 scheduler、分片、worker topology、执行生命周期。
- XXL-JOB bridge，把外部触发和分片元数据映射到 `NexaryJob`。
- Starter selector 与 SPI provider 两种引入方式。
- Docker 本地联调、smoke、integration tests。
- 中英文文档、样例和发布清单。

`0.1.0` 不声明：

- cache strong consistency、exactly-once invalidation、fencing token、version-checked reads。
- counter multi-key transaction、quota system、TTL refresh-on-write。
- messaging exactly-once、全局有序、分布式事务。
- XXL-JOB Admin 注册、平台调度生命周期或 executor callback 完整托管。
- running job cancellation、持久化 job execution store。
- JDK 8 / Spring Boot 2 兼容线。
- sidecar、agent、控制面、管理后台。

发布前退出标准：

- `./gradlew check` 稳定。
- 本地 Redis / Kafka / RocketMQ / MySQL / XXL-JOB Admin smoke 和关键 integration tests 稳定。
- `publishToMavenLocal` 覆盖主要模块。
- README、样例、配置注释和中英文文档能支撑第一次接入判断。
- License、SCM、开源元数据、GitHub Actions、secret scan 和发布清单齐备。

## `0.2.x` 能力加固版

`0.2.x` 应围绕现有能力清单的残余风险做生产加固，不应盲目扩张新模块。

### `0.2.0-alpha.1`

第一批只推进和 `0.1.0` 残余风险直接相关的加固项：

- Cache：Redis lock fencing-token 方案评估与可选实现，保持现有 owner-token lock 兼容。
- Messaging：Redis queue processing queue / ack model，减少 `leftPop` 后应用级重投带来的可靠性缺口。
- Job：durable execution store，先支持 Redis 或 DB 中的一种持久化执行记录路径，并保持 in-memory store 可用于本地开发。

当前开发线已完成这批加固项。

这批不包含：

- PowerJob bridge。
- ActiveMQ 或其他新增消息 provider。
- 统一治理模块。
- 管理后台、控制面、sidecar 或 agent。

### `0.2.0-alpha.2`

第二批继续围绕现有能力做生产可观测性，不引入新 provider，也不提前建设治理模块。

- Core：补齐 provider-neutral observation 发布入口，复用 `NexaryObservationEvent`，公共 API 不暴露 Micrometer、Actuator 或任意中间件原生类型。
- Cache：围绕 get/put/delete/expire、batch、tiered L1/L2 hit/miss、invalidation、lock、counter 输出事件和指标。
- Messaging：围绕 publish、consume、retry、dead-letter、dedup、provider ack/requeue/recover 输出事件和指标。
- Job：围绕 trigger、execution status、duration、retry、timeout、skip reason、shard metadata、durable store write/read 输出事件和指标。
- Boot：可选 Micrometer bridge 只放在 Spring Boot 集成层，默认不要求业务代码感知。
- Docs：补齐生产指标名称、标签边界、基数控制和不承诺项。

这批不包含：

- trace 后端、audit event 后端或独立治理模块。
- PowerJob bridge、ActiveMQ 或其他新增 provider。
- 管理后台、控制面、sidecar 或 agent。

### `0.2.0-alpha.3`

第三批补齐 Spring Boot 指标桥接，让 `alpha.2` 的 provider-neutral observation events 能直接接入常见指标体系。

- Boot：新增独立 Micrometer bridge starter，把 `NexaryObservationEvent` 转换成 Micrometer counter / timer。
- Core / capability API：保持不依赖 Micrometer、Actuator 或具体监控后端。
- 标签：只允许固定白名单标签，继续禁止 cache key、message id、execution id、payload、token、异常文本、堆栈等高基数字段。
- 文档：补齐 Spring Boot 接入方式、Prometheus 常见导出路径、指标名、标签边界、dashboard 示例和不承诺项。

这批不包含：

- trace exporter、audit backend 或独立治理模块。
- 新消息 provider、新 cache provider、PowerJob bridge。
- 管理后台、控制面、sidecar 或 agent。

### `0.2.0` 发布候选

`0.2.0` 不再继续扩能力，重点是开源发布基础设施：

- README 和快速开始提供 Maven / Gradle dependency 入口。
- BOM、starter、API、provider 和 observation bridge 元数据齐备。
- GitHub Actions 覆盖 `check`、release gate、`publishToMavenLocal` 和 Maven Central bundle 生成。
- License、SCM、developer、sources、Javadoc 和签名配置齐备。
- Maven Central 只发布框架模块，不发布 samples。

这批不包含：

- v0.3 治理模块实现。
- 未验证完成的 Spring Boot 2 / JDK 8 兼容声明。
- 自动发布到 Maven Central；正式上传前仍需确认 GitHub owner、Sonatype namespace 和签名密钥。

同时启动：

- Spring Boot 2 / JDK 8 兼容性差距检查。
- 独立 BOM / starter / 兼容分支方案评估。
- 通过真实编译、样例和 provider 集成验证后，再把兼容组合加入 README dependency 矩阵。

### Cache

- Redis lock 的更强语义说明和可选 fencing-token 方案评估。
- counter 高级能力评估：TTL refresh-on-write、多 key 限流或配额是否应成为独立能力。
- cache metrics：L1/L2 hit rate、miss、invalidation、lock、counter 指标。
- Valkey、Dragonfly、Garnet、Memcached 等 provider 评估。
- 保持 Caffeine/JVM local 作为内部 L1，不作为 Redis 平级公开后端。

### Messaging

- Kafka / RocketMQ 更生产化的 consumer container 集成。
- Redis queue 的 processing queue / ack model / 原子重试模型评估。
- delayed message / scheduled message 抽象评估。
- batch publish / batch consume。
- provider-specific 限制和生产配置文档细化。
- ActiveMQ 或其他消息系统只作为 provider integration 评估，不优先于现有 provider 加固。

### Job

- durable execution store，可评估 Redis 或 DB 持久化执行记录。
- running cancellation 是否进入正式能力范围。
- PowerJob bridge。
- XXL-JOB executor 侧真实联调增强，但仍不能把 Admin 平台能力说成 Nexary 自己实现。
- misfire / concurrency / retry 策略的更多生产场景验证。
- job metrics：执行次数、耗时、失败率、skip 原因、分片分布。

### Release

- Maven Central 发布流程固化。
- 示例工程和文档从“可运行”升级到“可作为接入模板”。
- GitHub issue / PR 模板、贡献指南、变更日志流程稳定。

## `0.3.x` 治理扩展版

`0.3.x` 再开始让治理能力独立成模块。

优先候选：

- 统一观测导出：metrics、trace、audit event。
- timeout、retry、bulkhead、rate limit、degradation/fallback 等治理策略。
- 服务治理模块原型。
- 跨能力的统一配置模型和观测标签。
- 可选 provider：PowerJob、ActiveMQ 等只在现有抽象稳定后进入评估，不抢占治理主线。

治理模块必须保持清晰边界：它可以消费 cache / messaging / job 的事件与策略扩展点，但不应让这些能力的主 API 变胖。

## `1.0.0` 稳定版目标

- 公共 API 冻结到可长期维护水平。
- Maven Central 正式发布稳定。
- 文档、样例、issue / PR / release 流程稳定。
- 兼容策略明确，包括是否需要 Spring Boot 2 / JDK 8 单独兼容线。

## 社区协作约定

建议把社区变更分成四类：

1. bug fix：直接 issue / PR。
2. provider integration：先 issue 讨论，再决定是否进入 roadmap。
3. public API change：先设计讨论，再 PR。
4. governance capability：先做范围定义，不直接堆实现。

一个能力如果要进入路线图，至少要回答：

- 解决什么真实问题。
- 是否扩大公共 API 面。
- 是否需要新增样例。
- 是否需要新增 Docker 联调或集成测试。
- 是否会改变当前版本的风险声明。
