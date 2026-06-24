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

- Core：补齐 Nexary 层 observation 发布入口，复用 `NexaryObservationEvent`，公共 API 不暴露 Micrometer、Actuator 或任意中间件原生类型。
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

第三批补齐 Spring Boot 指标桥接，让 `alpha.2` 的 Nexary 层 observation events 能直接接入常见指标后端。

- Boot：新增独立 Micrometer bridge starter，把 `NexaryObservationEvent` 转换成 Micrometer counter / timer。
- Core / capability API：保持不依赖 Micrometer、Actuator 或具体监控后端。
- 标签：只允许固定白名单标签，继续禁止 cache key、message id、execution id、payload、token、异常文本、堆栈等高基数字段。
- 文档：补齐 Spring Boot 接入方式、Prometheus 常见导出路径、指标名、标签边界、dashboard 示例和不承诺项。

这批不包含：

- trace exporter、audit backend 或独立治理模块。
- 新消息 provider、新 cache provider、PowerJob bridge。
- 管理后台、控制面、sidecar 或 agent。

### `0.2.0-alpha.4`

第四批进入多版本兼容工作流，先做 Spring Boot 2.7 / Java 8+，不先做 Boot 4。

- Build：新增 `compatibilityAudit` 报告型 gate，持续输出 Java 8 / Boot 2 阻断点。
- API：审计公共 API 中的 `record`、Java 9+ 集合工厂、`Stream.toList()`、pattern matching、switch expression 等 Java 8 不兼容点。
- Boot：评估独立 `spring-boot2` BOM / starter 命名和依赖锁定，避免污染 Boot 3 主线。
- Auto-configuration：补齐 Boot 2 所需 `spring.factories` 或等价兼容入口。
- Samples：准备 Boot 2 / Java 8 独立样例，业务代码仍只依赖 Nexary API。
- CI：建立 Boot 2.7 / Java 8+ 编译、样例运行和 provider 集成 gate。

这批的退出标准不是“文档声明支持”，而是形成明确差距清单、改造方案和可执行 gate。每个模块入口只有通过对应 gate 后，README 才增加 Boot2 已支持依赖片段。

### `0.2.0-alpha.5`

第五批在 Boot 2 已验证入口完成发布收口后推进 Spring Boot 4.x / Java 21+ 验证。

- Build：新增 Boot 4 / Java 21+ CI matrix。
- Boot：评估独立 `spring-boot4` BOM / starter 是否需要新 artifact，避免用户误选。
- Dependencies：验证 Spring Boot 4 依赖约束、Micrometer、Spring Data Redis、Kafka、RocketMQ、XXL-JOB bridge 的兼容性。
- Samples：准备 Boot 4 / Java 21+ 独立样例。
- Docs：通过 gate 后再扩展 README 依赖矩阵。

Boot 4 的官方最低 JDK 仍以 Spring 官方文档为准；Nexary 只把 Java 21+ 作为自己的主验证目标。

### `0.2.0` 发布候选

`0.2.0` 不再继续扩能力，重点是开源发布基础设施：

- README 和快速开始提供 Maven / Gradle dependency 入口。
- BOM、starter、API、provider 和 observation bridge 元数据齐备。
- GitHub Actions 覆盖 `check`、release gate、`publishToMavenLocal` 和 Maven Central bundle 生成。
- License、SCM、developer、sources、Javadoc 和签名配置齐备。
- Maven Central 只发布框架模块，不发布 samples。

这批不包含：

- v0.3 治理模块实现。
- 未验证完成的 Spring Boot 2 / JDK 8 或 Spring Boot 4 / Java 21+ 支持声明。
- 自动发布到 Maven Central；正式上传前仍需确认 GitHub owner、Sonatype namespace 和签名密钥。

同时启动：

- Spring Boot 2.7 / Java 8+ 兼容性差距检查和改造 gate。
- Spring Boot 4.x / Java 21+ 后续验证 gate。
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

## `0.3.x` 治理与新 provider 版

`0.3.x` 已经把治理能力独立成模块，并补齐三类常见替换诉求：Cache 的 Valkey 部署目标、Messaging 的 ActiveMQ Classic provider、Job 的 PowerJob bridge。

`0.3.x` 已纳入：

- Governance：deadline、traffic、rate limit、bulkhead、degradation、retry-stop 等基础语义。
- Cache：Valkey 作为 Redis 协议兼容部署目标，业务 API 不变。
- Messaging：ActiveMQ Classic queue provider，业务代码不接触 JMS 类型。
- Job：PowerJob bridge，复用统一 execution lifecycle。
- Samples：补齐 governance、ActiveMQ Classic、PowerJob 的可运行样例。
- Docs：补齐中英文接入文档、边界说明和本地验证命令。

治理模块必须保持清晰边界：它可以消费 cache / messaging / job 的事件与策略扩展点，但不应让这些能力的主 API 变胖。

`0.3.x` 的基础范围已经收口。后续同一小版本线继续做两件事：

- 把更多治理基础语义做成可配置、可运行、可测试的策略，同时保持 cache / messaging / job 主 API 简单。
- Boot2 / Boot4 继续按 provider 独立验收；只有样例和真实中间件测试都通过，README 才增加支持声明和依赖片段。

## `0.4.x` 可运行治理策略版

`0.4.x` 的重点不是继续堆 provider，而是把 `0.3.x` 已经落下来的治理语义接到真实调用路径里，让用户可以通过配置启用、通过样例验证、通过指标观察。

已纳入范围：

- Governance：把 deadline、rate limit、bulkhead、degradation、retry-stop 做成 Spring Boot 可配置策略。
- Cache：在 cache 操作入口验证 deadline、限流、降级和指标事件，不改变 `CacheClient` 业务 API。
- Messaging：在 publish / consume 路径验证 deadline、停止重试、降级和失败事件，不把 JMS、Kafka、RocketMQ 类型暴露给业务代码。
- Job：在本地 scheduler、XXL-JOB bridge、PowerJob bridge 触发路径验证 deadline、并发隔离、跳过原因和执行事件。
- Observation：补齐治理策略的指标名、标签白名单、Prometheus 示例和 dashboard 数据来源说明。
- Samples：提供可直接运行的治理样例，覆盖正常通过、被限流、降级和指标输出。
- Verification：新增治理策略的单元测试、Spring Boot 样例测试和全仓库 `check` gate。

`0.4.x` 不包含：

- 控制面、管理后台、sidecar、agent。
- 自动下发策略或远程动态配置。
- 把外部调度平台、消息平台或缓存平台的控制台能力包装成 Nexary 自己的能力。

同一小版本线后续只继续收尾两类问题：

- 根据真实样例继续扩大 Boot2 / Boot4 上的治理接入声明。
- 把治理样例补到更多真实中间件组合，但通过测试前不写进 README 支持矩阵。

## `0.5.x` 接入体验和生态稳定版

`0.5.x` 的重点是让用户更容易判断“我该怎么接、接完怎么验证、出问题怎么定位”，并把发布、文档、样例和 provider 验证流程固定下来。

`0.5.1` 已纳入：

- Release：补齐 Maven Central namespace、签名、sources、Javadoc、tag 发布和失败处理说明。
- Docs：把 README、能力文档、样例文档统一到 `0.5.1`，并补齐每条接入路径的依赖片段、配置、运行命令和限制。
- Compatibility：继续保留 Boot2 / Boot4 按能力声明的口径，不把未验收组合写成整体支持。
- Samples：修正样例端口和运行命令，保证文档里的 curl 命令能对应实际服务。
- Operations：新增常见问题页，覆盖版本、依赖、端口、中间件、provider、Job cron、指标和发布前检查。

同一小版本线后续继续做：

- Cache：在 Redis / Valkey 稳定后，再评估 Dragonfly、Garnet、Memcached；只有通过样例和真实中间件测试才写进 README。
- Messaging：继续加固 Kafka、RocketMQ、Redis queue、ActiveMQ Classic 的生产配置说明；是否新增 RabbitMQ 等 provider 先走 issue 讨论。
- Job：继续验证 XXL-JOB / PowerJob 的真实平台触发、worker / executor 生命周期边界和失败回调边界，文档必须区分“bridge 验证通过”和“平台完整生命周期由外部系统负责”。
- Samples：继续把样例整理成可以复制到业务工程的接入模板。

`0.5.x` 不包含：

- 私有化产品说明。
- 私有部署平台、租户管理、用户权限、计费或工单能力。
- 未经真实中间件验证的 provider 支持声明。

## `0.6.x` 本地治理流程

`0.6.x` 继续收紧治理能力的可验证边界。目标不是做控制台或远程策略平台，而是让一个 Java 进程内的调用能清楚地展示：正常调用、失败记录、慢调用记录、熔断打开、fallback、半开探测、恢复或重开。

`0.6.x` 已纳入：

- Governance：补充熔断状态、拒绝原因和本地状态快照类型，用低数量字段描述 `CLOSED`、`OPEN`、`HALF_OPEN`。
- Boot：`circuit-breaker` 配置可绑定到本地 `GovernancePolicy`，覆盖失败率、慢调用比例、半开探测和打开时长。
- Cache：Redis 客户端调用经过本地治理运行时，失败和慢调用可以进入同一套熔断判断。
- Messaging：consumer handler 经过 `GovernanceExecution`，慢消费或失败可打开本地熔断；publish 路径仍按原有发送事件记录。
- Job：local、XXL-JOB、PowerJob 的执行入口经过 `GovernanceExecution`，慢任务或失败可打开本地熔断。
- Samples：`nexary-sample-governance` 增加 `LocalCircuitBreakerProfileGateway`，可通过 curl 跑出失败打开、慢调用打开、打开后 fallback、半开成功恢复、半开失败重开。
- Docs：治理文档和样例文档补齐依赖、配置、运行命令、curl 步骤、预期字段和边界说明。
- Tests：样例测试覆盖熔断打开、拒绝后 fallback、半开恢复、半开失败重开和慢调用打开。

`0.6.x` 后续只补两类缺口：

- 统一 messaging publish 路径的治理运行时接入，避免发送端只停留在原有发送事件和 deadline header。
- 继续补真实中间件命令级演示，让 Redis、Kafka/RocketMQ/ActiveMQ Classic、XXL-JOB/PowerJob 的熔断行为都有可复制命令。

`0.6.x` 不包含：

- 控制台、sidecar、agent、远程动态配置或策略下发。
- 全局服务治理、跨进程状态同步或跨实例统一熔断窗口。
- 对 cache / messaging / job 主 API 的侵入式改造。
- messaging publish 的统一 runtime-backed 熔断。
- 未经样例和测试验证的 README 支持声明。

## `0.7.x` Messaging publish 治理和命令级样例

`0.7.x` 的目标是把已经形成的本地治理边界讲清楚，并让用户能用命令看到 messaging 样例的发送结果、消费结果和真实中间件差异。它仍然是 Java SDK 内的治理能力，不是控制台、sidecar、agent 或远程配置。

已纳入范围：

- Messaging：说明 publish 路径的本地治理资源名：`kind=messaging`、`name=message-publish`、`operation=publish`，provider 使用 `disruptor`、`redis`、`kafka`、`rocketmq` 或 `activemq_classic`。
- Messaging：说明 provider publisher 会写入 `nexary-deadline-epoch-millis`，过期 publish 会返回 `MessagePublishResult.failed("message publish deadline exceeded", RetrySignal.stop("deadline_exceeded"))`。
- Messaging：说明 `GovernedMessagePublisher` 只保护当前 JVM 的 publish 调用；它不提供跨实例窗口、broker 级熔断或自动切换 provider。
- Samples：`nexary-sample-messaging` 文档补齐 `POST /app-error-logs` 和 `GET /app-error-logs`，让用户能看 `result.status`、`published[].publishStatus`、`published[].providerMessageId`、`published[].detail`、`consumed[]`。
- Samples：补齐 Disruptor、Redis、Kafka、RocketMQ、ActiveMQ Classic 的启动命令和 curl 命令；真实 broker 仍由 `./scripts/middleware/up.sh` 或用户本地 broker 提供。
- Governance docs：补充 messaging publish 策略 YAML，说明只有 publish 路径接入 `GovernanceExecution` 时这些字段才会生效。

`0.7.x` 不包含：

- 控制台、sidecar、agent、远程动态配置或策略下发。
- 跨进程熔断窗口、全局限流、跨实例状态同步。
- broker 高可用、fallback chain、自动创建生产 topic、自动创建生产 queue。
- exactly-once、全局有序或跨 provider 事务一致性承诺。
- 未经过样例和真实中间件验证的 README 支持声明。

## `0.8.x` 治理数据面和策略快照

`0.8.x` 把本地治理运行时的状态整理成稳定的只读数据。用户可以在一个 JVM 里看到治理资源、策略快照、运行时快照和最近事件；这些数据用于排查当前服务的本地治理行为，也为后续只读 Console 留出字段基础。它仍然不是控制台、sidecar、agent 或远程配置平台。

已纳入范围：

- Runtime：提供 `GovernanceDiagnostics`、`GovernanceResourceDescriptor`、`GovernancePolicySnapshot`、`GovernanceRuntimeEvent`、`GovernanceRuntimeSummary`。
- Runtime：`GovernanceRuntime` 保留原有 execute 行为，同时提供 `resources()`、`snapshots()`、`recentEvents()`、`summary()` 只读方法。
- Runtime：最近事件使用有上限的 ring buffer，按时间顺序返回。
- Diagnostics：事件字段只包含 `resourceKey`、`action`、`outcome`、`rejectionReason`、`circuitState`、`timestamp`、`durationBucket`。
- Boot：`nexary-governance-spring-boot-starter` 在显式开启 `nexary.governance.diagnostics.enabled=true` 后提供 `GET /nexary/governance/summary`、`/resources`、`/resources/{resourceKey}`、`/events`。
- Samples：`nexary-sample-governance` 增加 diagnostics profile 和 curl 命令，用来触发成功、失败、限流、并发隔离、熔断打开、半开探测和恢复。

`0.8.x` 不包含：

- 写策略、下发配置、远程控制、登录权限、审计后台或可视化页面。
- userId、tenant、bizKey、messageId、cache key、payload、完整异常消息或堆栈。
- 跨实例窗口、集中状态存储、自动切换 provider。
- 未通过真实样例和中间件测试的 Boot2 / Boot4 / provider 支持声明。

## `0.9.x` 只读治理诊断 Console

`0.9.x` 在 v0.8 的本地治理数据面上加一个页面。用户启动自己的 Spring Boot 应用后，访问 `/nexary/console`，就能看到当前 JVM 的 summary、resources、resource detail、events 和只读设置提示。这个页面只读，只服务本地排查。

已纳入范围：

- Console API：`GET /nexary/console/api/summary`、`/resources`、`/resources/{id}`、`/events`。
- Console 页面：Overview、Resources、Resource Detail、Events、Settings Readonly。
- Starter：`nexary-console-spring-boot-starter` 只有在 `nexary.console.enabled=true` 后才注册页面和 API。
- 打包：`nexary-console-server` 会把 Vue 构建产物放入 jar 的 `static/nexary/console`。
- 样例：`nexary-sample-governance` 默认引入 Console starter，启动后可以直接打开 `/nexary/console`。

`0.9.x` 不包含：

- 写策略、下发配置、远程控制、登录、权限、审计后台或多实例聚合。
- 展示 userId、tenant、bizKey、messageId、cache key、payload、异常全文或堆栈。
- 独立部署的前端服务、sidecar、agent 或跨服务管理页面。

## `0.10.x` 1.0 前 Console 和本地诊断硬化

`0.10.x` 是 1.0 前的硬化版本，不引入新的治理平台承诺。它继续服务单个 Spring Boot 应用内的本地排查，把 v0.9 只读 Console 做到更容易打开、更不容易在打包和发布时回归。

已纳入范围：

- Console 直接 URL：`/nexary/console`、`/nexary/console/`、`/nexary/console/resources`、`/nexary/console/resources/{resourceKey}`、`/nexary/console/events`、`/nexary/console/settings` 都应能返回可渲染页面。
- 静态资源：打入 `nexary-console-server` jar 后，入口 HTML、JS 和 CSS 路径要稳定；资源缺失或空白页回归应被测试或 release gate 发现。
- 本地 sample 可视验证：启动 `nexary-sample-governance` 后，用 curl 触发成功、失败、限流、并发隔离、熔断打开、半开恢复，再用浏览器验证 Overview、Resources、Resource Detail、Events、Settings Readonly 非空且可跳转。
- Release gate：`0.10.1` 发布前继续跑 release preflight、Gradle check、Console UI build、静态资源打包检查、Docker 样例 smoke 和公开文档扫描。

`0.10.x` 不包含：

- 写策略、策略回滚、远程配置、动态下发。
- 多实例聚合、跨进程状态同步、集中状态存储。
- 登录、权限、RBAC、用户管理、审计后台。
- sidecar、agent、独立部署控制台或跨服务管理页面。
- 自动封禁、外部平台管理或事故处置流程。

验收目标：

- Console 直接 URL 和 deep link 在 Spring Boot jar 内都能打开，不依赖先访问首页。
- 静态资源缺失时不能静默变成空白页。
- 本地治理样例能完成可视验证，并覆盖空数据、正常数据和熔断打开数据。
- release gate 稳定，失败时能指出是 Gradle、UI build、静态资源打包、文档扫描还是发布输入问题。

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
