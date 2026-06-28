# v0.20 治理平台 RC PRD

v0.20 不再按“补一个 Platform 页面”收口，而是交付统一治理平台 RC。Nexary 仍保留本地 Console 诊断，但默认入口必须服务于值班和平台治理：总览、拓扑、请求链路、事故、服务、主机实例、中间件、资源治理、集成、通知和策略计划。

平台不替代 SkyWalking、CAT、Sentinel、Spring Cloud Gateway、Prometheus、Spring Boot Admin、Feishu 或 DingTalk。它做的是跨工具证据聚合、请求分析、事故归因和治理编排入口，减少每天打开多个中间件控制台人工巡检的成本。

## 目标

1. 10 秒内看出哪里坏、影响谁、先查什么。
2. 把服务、机房、中间件、主机水位、请求链路和外部工具证据串起来。
3. 对异常给出规则化诊断结论，先只读和 dry-run，不直接写生产配置。
4. 为后续真实连接 Prometheus、SkyWalking、CAT、Sentinel、Gateway、SBA 和企业 IM 留出模型和入口。

## 云手机参考场景

v0.20 demo 必须覆盖云手机拓扑，而不是抽象服务列表：

| 层级 | 对象 |
| --- | --- |
| 云上业务 | open-api、sdk-api、scheduler、consumer、admin-console、user-platform |
| 云上中间件 | redis-main、postgres-main、oss-main |
| 机房业务 | room-resource、signaling、board-service |
| 机房中间件 | redis-room、oss-room |
| 典型异常 | Redis 超时、Redis pipeline 错误、board-service 断开、跨机房抖动、HTTP 失败、主机 swap / IO 水位异常 |

所有 demo 数据只能使用别名，例如 `room-a-signaling-01`、`redis-room-a-primary`、`board-service-room-a`。禁止写真实内网 IP、业务 ID、cache key、payload、token、密码、异常全文或完整 stack trace。

## v0.20 页面范围

| 页面 | 要回答的问题 | v0.20 RC 验收 |
| --- | --- | --- |
| 总览 | 今天哪里有风险。 | 同屏看到事故队列、拓扑摘要、诊断结论、服务/机房/中间件水位。 |
| 拓扑 | 哪条依赖边有问题。 | 服务、中间件、机房分层；红色虚线表示失败/超时，黄色表示慢调用，紫色表示抖动或丢包。 |
| 请求链路 | 一次请求慢在哪里、错在哪里。 | 左侧样本列表，中间 span 树和耗时瀑布，顶部或右侧 CAT 式交易统计，外部证据引用。 |
| 事故 | 为什么报警，先看哪里。 | 事故证据包：指标异常、请求样本、交易统计、Sentinel/Gateway 信号、主机水位和外部引用。 |
| 服务 | 某个服务是否健康。 | QPS、错误率、P95/P99、实例、下游依赖、策略摘要、外部工具引用。 |
| 主机实例 | 主机和实例信号是否异常。 | 矩阵列出 CPU、内存、swap、磁盘 IO、线程、GC、网络抖动、丢包、最近异常。 |
| 中间件 | Redis/PG/MQ/OSS 是否拖慢业务。 | 水位、连接、慢查询/超时、调用方、关联事故。 |
| 资源治理 | Nexary 本地治理证据是什么。 | 保留资源、事件、fault trace 的只读诊断入口，但不作为主路径。 |
| 集成 | 外部工具数据是否可信。 | SkyWalking、CAT/日志、Prometheus、Sentinel、Gateway、SBA、Feishu、DingTalk 状态和新鲜度。 |
| 通知 | 异常怎么进群且不刷屏。 | dry-run、测试发送、路由、去重、静默、升级、恢复通知规则。 |
| 策略计划 | 改规则前会影响什么。 | diff、dry-run、copy、export review；禁止 Apply/Save/Delete 生产写入按钮。 |

## 后端模型

v0.20 在 `org.nexary.governance.platform` 下新增只读模型，不改变 cache、messaging、job 的用户 API：

| 模型 | 说明 |
| --- | --- |
| `GovernanceRequestFlow` | 一次请求链路样本，包含 traceKey、入口服务、endpoint、状态、耗时、span 数、首要异常、外部引用。 |
| `GovernanceSpan` | span 树节点，包含 parent/span id、服务、资源、中间件组件、耗时、状态、错误类型、来源引用。 |
| `GovernanceTransactionMetric` | CAT 风格统计，包含 total、failure、failureRate、qps/tps、min/max/avg、p95/p99、样本 traceKey。 |
| `GovernanceHostSignal` | 主机/实例水位，包含 CPU、内存、swap、磁盘 IO、网络抖动、丢包、连接数、JVM/线程状态。 |
| `GovernanceEvidenceRef` | 外部证据引用，类型限定为 SkyWalking trace、CAT transaction、PromQL、日志查询、Sentinel resource、Gateway route、SBA instance、Nexary fault trace。 |

## 只读 API

v0.20 RC 至少提供这些端点：

- `GET /api/platform/snapshot`
- `GET /api/platform/request-flows`
- `GET /api/platform/request-flows/{traceKey}`
- `GET /api/platform/transactions`
- `GET /api/platform/hosts`
- `GET /api/platform/incidents/{incidentKey}`

所有返回内容都必须是脱敏低基数字段。外部证据只放引用，不放 payload、完整异常文本或完整 stack trace。

## 事故信号

v0.20 事故聚合必须识别这些信号：

- `REDIS_TIMEOUT`
- `REDIS_PIPELINE_ERROR`
- `BROKEN_PIPE`
- `DEPENDENCY_TIMEOUT`
- `NETWORK_JITTER`
- `PACKET_LOSS`
- `HOST_WATERMARK`

这些信号不能简单按服务计数展示，必须聚合成证据包，并能下钻到请求链路、交易统计、主机水位和外部引用。

## 非目标

- 不接真实生产连接器凭证。
- 不接真实 LLM 分析助手。
- 不写生产 Sentinel、Gateway、Feishu、DingTalk。
- 不自动摘流、封禁、改规则或扩容。
- 不把 Nexary 描述成 APM、监控、限流熔断或 IM 工具的替代品。

## v0.20 RC 验收

- `http://127.0.0.1:18090/nexary/console` 默认进入新版治理总览。
- 主导航为：总览、拓扑、请求链路、事故、服务、主机实例、中间件、资源治理、集成、通知、策略计划。
- 顶部公共区不超过 56px，页面不再使用大 hero。
- 1440px 桌面宽度下，总览能同时看到事故、拓扑、诊断结论和水位摘要。
- 请求链路页明显强于旧 Trace 页，能看到请求样本、Span 瀑布、交易统计和证据引用。
- Demo 事故能表达 Redis 超时、pipeline 错误、Broken pipe、跨机房抖动和主机水位异常。
- 前端 `npm run build` 通过，后端平台测试通过，Docker 18090 smoke 通过。

以上验收通过后，`v0.20.0` 可以作为治理平台 RC 发布。它的定位是产品边界、只读模型、demo 拓扑和排障入口闭合，不把 demo UI 当作最终运维体验。

## v0.21 接力边界

v0.21 不继续围绕静态 demo 微调界面，优先把真实只读指标和水位接进来：

- Micrometer / Prometheus 类指标：QPS、错误率、P95/P99、HTTP 成功率。
- 主机和 JVM 水位：CPU、内存、GC、线程、连接池。
- 中间件水位：Redis、PG、MQ、OSS 的连接、慢调用、超时和错误。
- Docker 环境补齐真实组件和 sample 服务，用真实只读数据检验 v0.20 的页面和模型是否成立。
- 仍不写 Sentinel、Gateway、Feishu、DingTalk 或生产配置；策略和通知继续 dry-run。
