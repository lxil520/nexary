# v0.20 治理平台 RC UI/UE 设计

v0.20 UI 目标是做“值班能用的治理工作台”，不是继续美化本地 Console。页面要像运维工具：高密度、低装饰、表格/矩阵/拓扑/瀑布优先，状态色只用于风险、连接、策略和通知。

## 信息架构

左侧主导航固定为：

1. 总览
2. 拓扑
3. 请求链路
4. 事故
5. 服务
6. 主机实例
7. 中间件
8. 资源治理
9. 集成
10. 通知
11. 策略计划

本地 JVM 诊断保留在资源治理或设置边界里，不再占据主路径。旧的 `Trace` 命名统一改成“请求链路”。

## 公共区域

顶部公共区目标高度不超过 56px，只放全局筛选和刷新状态：

- Workspace
- Environment
- Team
- Zone
- Time range
- Severity
- Refresh
- Language

页面内部不再放大 hero。每页结构为紧凑标题/工具栏 + 主工作区。

## 总览

总览回答“哪里坏、影响谁、先查什么”。

推荐布局：

- 左侧：事故队列，默认选中最严重事故。
- 中间：拓扑摘要，展示异常依赖边。
- 右侧：诊断结论，展示影响范围、首要资源、证据数和建议检查。
- 底部：服务水位、机房水位、中间件水位、主机异常摘要。

1440px 宽度下，这四类信息必须同屏可见。

## 拓扑

拓扑页回答“服务、中间件、机房怎么串起来，哪条边有问题”。

节点：

- Service：服务名、团队、集群、机房、实例数。
- Middleware：Redis、PG、MQ、OSS 等类型、水位和连接状态。
- Host/Instance：只在详情或矩阵中显示，不把所有实例堆到主画布。

边：

- 绿色实线：健康。
- 黄色虚线：慢调用或 P95/P99 升高。
- 红色虚线：失败、超时、连接失败。
- 紫色虚线：网络抖动或丢包。

边上显示 QPS、错误率、P95/P99 和最近证据类型。点击边后右侧显示 source、target、resource、错误数、外部引用。

## 请求链路

请求链路页是 v0.20 的核心页面，必须明显强于旧 Trace 页。

布局：

- 左侧：请求样本列表，支持按 traceKey、endpoint、服务、机房、耗时、失败类型筛选。
- 中间：span 树 + 耗时瀑布，每个 span 显示 service、component、operation、duration、errorType。
- 右侧：CAT 风格交易统计和证据引用。

交易统计字段：

- total
- failure
- failureRate
- qps/tps
- min/max/avg
- p95/p99
- sampleTraceKey

外部引用只展示低基数 key，例如 SkyWalking trace 引用、CAT transaction 引用、PromQL 引用、日志查询引用、Sentinel resource、Gateway route、SBA instance、Nexary fault trace。

## 事故

事故页从事件列表升级为“证据包”。

事故详情包含：

- 标题、严重级别、影响服务、集群、机房。
- 主证据和首要资源。
- 时间线：指标异常、请求样本、交易统计、Sentinel/Gateway 信号、主机水位、外部引用。
- 建议检查：服务、route、资源、中间件或 host。
- 通知 dry-run 状态。

v0.20 不提供确认、静默、恢复写入按钮，只展示状态和计划。

## 服务

服务页展示服务列表和详情：

- QPS
- 错误率
- P95/P99
- 实例数
- 下游依赖
- 策略摘要
- 外部工具引用

服务详情要能回答“这个服务的问题来自自身、下游、中间件、主机还是入口”。

## 主机实例

主机实例页使用矩阵，不做大卡片：

| 列 | 说明 |
| --- | --- |
| Host | 脱敏实例或主机别名 |
| CPU | CPU 使用率 |
| Mem | 内存使用率 |
| Swap | swap 使用率 |
| Disk IO | 磁盘 IO 水位 |
| Jitter | 网络抖动 |
| Loss | 丢包 |
| Threads | JVM 线程数 |
| Last | 最近异常 |

Redis swap / IO 异常、board-service 不可达、跨机房抖动必须能在矩阵中直观看到。

## 中间件

中间件页按 Redis、PG、MQ、OSS 类型展示：

- 水位
- 连接数
- 慢查询或超时
- 调用方服务
- 关联事故
- 最近请求链路样本

Redis timeout 和 pipeline error 必须能从中间件页下钻到请求链路和事故证据。

## 集成

集成页展示外部工具可信度：

- SkyWalking
- CAT / 日志
- Prometheus
- Sentinel
- Gateway
- Spring Boot Admin
- Feishu
- DingTalk

每个 connector 展示 state、last success、data freshness、permission hint、read-only / dry-run / write-disabled。

## 通知

通知页只做 dry-run 和测试发送：

- route rule
- dedup rule
- mute window
- escalation rule
- recovery rule
- test delivery

默认不能打开生产发送，避免异常抖动刷屏。

## 策略计划

策略计划页只展示计划和 diff：

- Generate plan
- Run dry-run
- Copy change
- Export review

禁止出现 Save、Apply、Delete 或直接写生产系统的按钮。

## 视觉验收

- 卡片圆角不超过 6px。
- 表格行高 36px 到 44px。
- 1440px 宽度下总览同屏看到事故、拓扑、诊断和水位。
- 请求链路页同屏看到样本、瀑布、交易统计和证据引用。
- 拓扑页能通过边的颜色和线型看出失败、慢调用、抖动或丢包。
- 文案不把 Nexary 描述成 SkyWalking、CAT、Prometheus、Sentinel 或 Gateway 的替代品。
