# v0.9 只读治理诊断 Console PRD

本文定义 v0.9 的只读治理诊断 Console。它运行在用户自己的 Spring Boot 应用内，用来查看当前 JVM 的治理资源、策略快照、窗口统计、熔断状态、拒绝原因和最近事件。它不是远程策略控制台，也不负责修改配置或汇总多个实例。

## 用户

| 用户 | 需要完成的事 |
| --- | --- |
| Java 开发者 | 本地接入治理后，确认资源名、策略和实际调用结果是否匹配。 |
| 接入排障人员 | 查看某个资源为什么被拒绝、是否进入熔断、最近一次结果是什么。 |
| 本地运维验证人员 | 在样例或单个服务实例上复现限流、并发隔离、降级、熔断打开和恢复过程。 |
| Nexary 维护者 | 验证治理诊断 API 暴露的字段是否足够页面使用，同时没有泄露高数量或业务敏感数据。 |

## 核心问题

Console 要回答五个问题：

1. 当前应用里有哪些治理资源。
2. 每个资源绑定了什么策略快照。
3. 哪些资源正在被拒绝、失败、慢调用或打开熔断。
4. 某个资源最近为什么被拒绝，最近一次结果是什么。
5. 最近发生了哪些治理事件，事件是否能帮助定位本地行为。

如果页面不能直接回答这些问题，就不应进入 v0.9。统计趋势、跨实例比较、策略编辑和账号权限不是 v0.9 的问题。

## 产品边界

- Console 只在当前 Spring Boot 应用内提供页面，默认路径为 `/nexary/console`。
- Console API 默认路径为 `/nexary/console/api`，只提供 GET 读取接口。
- Starter 只有在显式配置 `nexary.console.enabled=true` 后才暴露页面和 API。
- 数据来自本 JVM 内的治理运行时快照和最近事件；不读远程存储，不拉取其他实例。
- 页面只展示诊断字段，不展示业务 payload、完整异常消息或堆栈。
- 页面可以刷新和本地筛选，但不能提交策略修改。

## 页面范围

### Overview

展示当前实例的治理概览：

- 资源总数。
- 打开熔断的资源数。
- 最近窗口内拒绝数。
- 最近窗口内失败数。
- 最近事件列表的前几条。
- 空状态：没有治理资源时说明当前应用没有可展示资源，而不是显示错误。

### Resources

展示资源列表，支持本地筛选：

- 按 `kind` 筛选：`cache`、`messaging`、`job`、`service`、`http`、`downstream`、`custom`。
- 展示 `resourceKey`、`kind`、`name`、`provider`、`operation`。
- 展示 `circuitState`、窗口调用数、失败数、慢调用数、拒绝数。
- 展示 `lastOutcome` 和 `lastRejectionReason`。
- 点击资源进入 Resource Detail。

### Resource Detail

展示单个资源的可排障信息：

- 资源目录字段：`resourceKey`、`kind`、`name`、`provider`、`operation`、`priority`。
- 策略快照：deadline、限流窗口、窗口请求上限、并发上限、降级开关、熔断阈值。
- 运行时快照：熔断状态、窗口调用数、失败数、慢调用数、当前并发、总拒绝数。
- 最近结果：`lastOutcome`、`lastRejectionReason`、最近更新时间。
- 最近事件：只展示该资源相关事件。

### Events

展示最近治理事件：

- 字段包括 `timestamp`、`resourceKey`、`action`、`outcome`、`rejectionReason`、`circuitState`、`durationBucket`。
- 支持按资源、动作、结果和拒绝原因做本地筛选。
- 事件列表来自有上限的内存 buffer；页面必须说明它不是审计日志。

### Settings Readonly

展示 Console 自身只读状态：

- Console 是否启用。
- 页面路径和 API 路径。
- 最近事件 buffer 上限。
- 数据来源说明：当前 JVM 内存快照。

该页不提供任何保存按钮、策略编辑表单、配置下发入口或登录设置。

## API 字段原则

API 只暴露低数量诊断字段。字段必须能落到页面中的一个判断或显示位置；不能为了“以后可能用到”扩大响应。

允许字段：

- 资源字段：`resourceKey`、`kind`、`name`、`provider`、`operation`、`priority`。
- 策略字段：deadline、`maxRequestsPerWindow`、`rateLimitWindow`、`maxConcurrency`、`degraded`、熔断阈值和窗口配置。
- 运行时字段：`circuitState`、`windowCalls`、`windowFailures`、`windowSlowCalls`、`activeConcurrency`、`totalRejections`。
- 最近结果：`lastOutcome`、`lastRejectionReason`、`lastUpdatedAt`。
- 事件字段：`timestamp`、`resourceKey`、`action`、`outcome`、`rejectionReason`、`circuitState`、`durationBucket`。

禁止字段：

- 用户标识、租户标识、业务 key、订单号、message id、cache key、tracing 标识。
- 消息体、请求体、响应体、异常完整文本、堆栈。
- 凭证、token、内部地址、真实 broker 或数据库连接信息。
- 任意会随用户、订单、消息或缓存条目增长的高数量字段。

字段命名要稳定。状态值应来自枚举语义，例如 `CLOSED`、`OPEN`、`HALF_OPEN`、`RATE_LIMITED`、`CONCURRENCY_LIMITED`、`CIRCUIT_OPEN`、`SUCCESS`、`FAILURE`、`REJECTED`。

## 非目标

v0.9 不做：

- 登录、RBAC、用户管理、权限配置。
- 策略新增、策略修改、策略回滚、远程配置下发。
- 多实例聚合、跨进程熔断窗口、集中状态存储。
- 审计后台、操作记录、合规报表。
- sidecar、agent、独立部署控制台。
- 自动切换 provider、broker 管理、外部调度平台管理。
- 生产事故处置工作流、告警确认、工单或值班协作。

## 验收标准

文档验收：

- 中文和英文 PRD 同时存在，语义一致。
- 文档不包含临时工作记录、私有路径、真实凭证或未公开沟通内容。
- 文档明确写出只读、本 JVM、显式启用、非远程控制台。

后端验收：

- 提供 `GET /nexary/console/api/summary`。
- 提供 `GET /nexary/console/api/resources`。
- 提供 `GET /nexary/console/api/resources/{id}`。
- 提供 `GET /nexary/console/api/events`。
- 空资源返回可渲染的空状态数据，不返回 500。
- DTO 不包含禁止字段。
- Starter 关闭时不暴露 Console 页面和 API。

前端验收：

- `/nexary/console` 能打开非空页面。
- Overview、Resources、Resource Detail、Events、Settings Readonly 可访问。
- 空状态、加载状态、错误状态和正常数据状态都有明确 UI。
- 桌面布局信息密度足够排障使用；移动端至少可阅读，不要求完整操作效率。
- 页面没有策略编辑、保存、删除、登录、权限或远程配置入口。

集成验收：

- 启动治理样例后，先用 curl 触发成功、失败、限流、并发隔离、熔断打开、半开恢复，再能在 Console 看到对应资源、状态和事件。
- Playwright 截图验证关键页面非空、主要内容不重叠、筛选和详情跳转可用。
- 文档扫描不出现内部词、私有路径、真实凭证或控制台能力夸大描述。

## 给后续实现的约束

Design：界面应像排障工具，不像营销页面。优先做高密度表格、状态徽标、指标卡、事件列表、筛选器、空状态和错误状态。桌面优先，移动端保证可读即可。

Backend：Console API 是治理诊断 API 的读模型。DTO 只能暴露低数量字段，不能把异常全文、业务标识或 provider 原生类型传给前端。

Frontend：前端只消费只读 API。筛选先在本地完成，不做复杂服务端查询。构建产物应能打入 server jar 的 static resources。

## v0.10 硬化验收

v0.10 不扩大 v0.9 PRD 的产品边界。硬化只覆盖打开方式、打包资源、本地样例和发布 gate：

- 直接访问 `/nexary/console/resources`、`/nexary/console/resources/{id}`、`/nexary/console/events`、`/nexary/console/settings` 时，Spring Boot 应返回同一个可渲染的 Console 页面。
- 打包后的 jar 必须包含 Console 入口 HTML、JS 和 CSS；缺失静态资源不能静默表现为空白页。
- 本地治理样例启动后，先用 curl 触发成功、失败、限流、并发隔离、熔断打开和半开恢复，再用浏览器确认 Overview、Resources、Resource Detail、Events、Settings Readonly 非空且可跳转。
- 发布前检查应覆盖 Gradle check、Console UI build、静态资源打包结果和文档禁入词扫描。
- 页面仍然没有策略编辑、保存、删除、登录、权限、远程配置或多实例聚合入口。
