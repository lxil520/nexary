# v0.9 只读治理诊断 Console 设计草案

本文定义 v0.9 只读治理诊断 Console 的页面、组件和前端实现约束。它服务于本地 Spring Boot 应用里的排障：开发者启动应用后访问 `/nexary/console`，查看当前 JVM 内的治理资源、策略快照、窗口统计、熔断状态、拒绝原因和最近事件。

## 设计 brief

| 项 | 决定 |
| --- | --- |
| 产品目标 | 让接入 Nexary governance 的 Java 开发者在本地应用里更快判断“哪些资源被保护、哪个资源正在拒绝、为什么拒绝、最近发生了什么”。 |
| 数据来源 | v0.8 只读诊断数据：`summary`、`resources`、`resources/{id}`、`events`。前端不创造业务状态。 |
| 视觉方向 | 运维排障密度，接近 Spring Boot Admin、Kafka UI、RocketMQ Dashboard 这类工具的扫描效率，但使用 Nexary 自己的克制样式。 |
| 交互等级 | 页面导航、筛选、排序、展开摘要、复制资源 key；不修改策略，不保存配置，不发送写请求。 |
| 设备优先级 | 桌面优先，宽度按 1280px 到 1440px 设计；移动端只保证内容可读和纵向滚动。 |

## 非目标

- 不提供策略编辑、配置下发、远程控制、登录权限、审计后台、跨实例聚合或自动封禁。
- 不展示 userId、tenant、bizKey、messageId、cache key、payload、完整异常消息或堆栈。
- 不把 Console 描述成生产控制面。它只读取当前应用暴露的诊断字段。

## 信息架构

Console 使用左侧导航加内容区。顶部保留当前应用、采样时间、刷新按钮和只读标识。导航项固定为：

| 页面 | 主要问题 | 默认入口 |
| --- | --- | --- |
| Overview | 当前服务有没有明显治理风险。 | `/nexary/console` |
| Resources | 哪些资源正在失败、拒绝或熔断。 | `/nexary/console/resources` |
| Resource Detail | 单个资源的策略、窗口和最近事件是什么。 | `/nexary/console/resources/{resourceKey}` |
| Events | 最近发生了哪些治理动作，拒绝原因是什么。 | `/nexary/console/events` |
| Settings Readonly | Console 和诊断端点如何启用，当前只读边界是什么。 | `/nexary/console/settings` |

## 视觉规则

| 项 | 规则 |
| --- | --- |
| 密度 | 默认表格行高 36px 到 40px；指标卡只放一个主数字和一个短标签。 |
| 色彩 | 背景使用白色和浅灰；文字以深灰为主；状态色只用于徽标、细边和小面积提示。 |
| 字体 | 使用系统 sans-serif。数字使用 tabular nums，便于横向比较。 |
| 间距 | 页面边距 24px；区块间距 16px；表格单元左右 12px。 |
| 圆角 | 卡片、徽标、输入框最大 6px。不要使用大圆角营销卡片。 |
| 图形 | 首版不需要插画、背景图或装饰渐变。排障数据比装饰更重要。 |

推荐状态色：

| 状态 | 用法 | 颜色建议 |
| --- | --- | --- |
| `CLOSED` / 正常 | 绿色文本加浅绿底，表示资源正在放行。 | `#047857` / `#ecfdf5` |
| `OPEN` / 熔断打开 | 红色文本加浅红底，表示主逻辑被挡住。 | `#b91c1c` / `#fef2f2` |
| `HALF_OPEN` | 琥珀色文本加浅琥珀底，表示探测中。 | `#b45309` / `#fffbeb` |
| `REJECTED` | 红色或橙色，按拒绝原因区分严重度。 | `#c2410c` / `#fff7ed` |
| 未配置或无数据 | 灰色，避免误报。 | `#475569` / `#f8fafc` |

## 组件规格

### 状态徽标

状态徽标用于 `circuitState`、`lastOutcome`、`lastRejectionReason` 和事件 `action`。徽标只显示固定枚举值，不能拼接动态文本。长值使用 12px 等宽字重，最大宽度 140px，超出时省略并在 tooltip 展示完整枚举。

严重度建议：

| 值 | 严重度 |
| --- | --- |
| `OPEN`、`CIRCUIT_OPEN`、`FAILURE` | 高 |
| `RATE_LIMITED`、`CONCURRENCY_LIMITED`、`REJECTED` | 中 |
| `HALF_OPEN`、`FALLBACK` | 注意 |
| `CLOSED`、`SUCCESS`、`EXECUTE` | 正常 |

### 资源表格

资源表格是 Resources 页面主控件。默认列：

| 列 | 说明 |
| --- | --- |
| Resource | `kind`、`name`、`operation`，第二行显示 `provider`。 |
| State | `circuitState` 和 `lastOutcome`。 |
| Window | `windowCalls`、`windowFailures`、`windowSlowCalls`。 |
| Rejections | `totalRejections` 和 `lastRejectionReason`。 |
| Concurrency | `activeConcurrency` / `maxConcurrency`。 |
| Last Seen | 最近事件时间或快照时间。 |

默认排序：`OPEN`、`HALF_OPEN`、最近有拒绝、最近失败、其他资源。资源名可点击进入详情。表格不提供批量操作列。

### 指标卡

指标卡用于 Overview 顶部。每张卡只回答一个问题：

| 卡片 | 字段 |
| --- | --- |
| Resources | 资源总数。 |
| Open Circuits | `circuitState=OPEN` 的资源数。 |
| Recent Rejections | 最近窗口内拒绝数或总拒绝增量。 |
| Recent Failures | 最近窗口内失败数。 |

卡片显示主数字、短标签、一个趋势或说明。无数据时显示 `0`，不要显示空白骨架。

### 事件列表

事件列表用于 Overview 右侧和 Events 页面。默认列：

| 列 | 说明 |
| --- | --- |
| Time | `timestamp`，前端格式化成本地时间。 |
| Resource | `resourceKey` 或资源名。 |
| Action | `EXECUTE`、`REJECT`、`FALLBACK`。 |
| Outcome | `SUCCESS`、`FAILURE`、`REJECTED`。 |
| Reason | `rejectionReason`，没有时显示 `-`。 |
| Duration | `durationBucket`，不显示精确耗时。 |

Events 页面只做本地筛选，不要求服务端复杂查询。默认按时间倒序。

### 策略摘要

策略摘要用于 Resource Detail。它以只读键值区块展示当前资源的 `policySnapshot`：

- deadline、rate limit、max concurrency、degraded。
- circuit breaker 是否启用、窗口、最小调用数、失败率阈值、慢调用阈值、打开保持时间、半开探测数。
- priority overrides 有值时单独折叠显示。

摘要必须保留配置单位，例如 `300ms`、`1s`、`30s`。缺省值显示为 `not set` 或 `unlimited`，不要推断成业务含义。

### 筛选器

筛选器用于 Resources 和 Events 顶部：

| 控件 | 类型 |
| --- | --- |
| Kind | segmented control：All / Cache / Messaging / Job / Service / HTTP / Downstream / Custom。 |
| State | dropdown：All / Closed / Half Open / Open / Rejected。 |
| Reason | dropdown：All / Circuit Open / Rate Limited / Concurrency Limited / Deadline / Degraded。 |
| Search | 文本输入，只匹配 `resourceKey`、`name`、`operation`、`provider`。 |
| Refresh | 图标按钮，保留 5s 内禁用态，避免误点刷新风暴。 |

筛选器不写 URL 查询参数也可以；如果实现路由同步，只保存低基数字段。

### 空状态

空状态分两类：

| 场景 | 文案方向 |
| --- | --- |
| Console 可用但没有资源 | 说明当前 JVM 还没有治理资源快照；提示先执行样例请求或业务路径。 |
| 筛选后无结果 | 说明筛选条件下没有匹配项；提供清除筛选按钮。 |

空状态不使用插画。用简短标题、两行以内说明和一个次级按钮即可。

### 错误状态

错误状态要区分：

| 错误 | 处理 |
| --- | --- |
| API 404 | 说明 Console API 可能未启用或路径配置不同。 |
| API 403 / 401 | 说明应用安全配置挡住了只读端点。 |
| API 500 | 显示请求失败和重试按钮，不展示后端异常全文。 |
| 网络超时 | 提示检查本地应用是否仍在运行。 |

错误详情只显示 HTTP status、路径和错误类别这类有界字段。不要在页面展示 trace 标识或堆栈。

## 页面原型说明

### Overview

首屏从上到下：

1. 顶栏：应用名、只读徽标、最后刷新时间、刷新按钮。
2. 四个指标卡：Resources、Open Circuits、Recent Rejections、Recent Failures。
3. 两栏内容：左侧是风险资源表格，只显示前 8 条；右侧是最近事件列表。
4. 底部说明：Console 读取当前 JVM 诊断数据，不代表跨实例状态。

Overview 的目标是 10 秒内判断是否需要进入资源详情。

### Resources

Resources 是排障主页面：

1. 顶部筛选器。
2. 资源表格，默认优先显示有风险资源。
3. 行点击进入 Resource Detail。
4. 表格上方显示当前筛选命中数量和最后刷新时间。

不要把 Resources 做成卡片墙。表格更适合比较窗口、拒绝和状态字段。

### Resource Detail

Resource Detail 分三段：

1. Header：资源名、kind、provider、operation、状态徽标、复制 resource key。
2. 两列摘要：左侧 Runtime Snapshot，右侧 Policy Summary。
3. 最近事件表，只显示该资源相关事件。

详情页要突出“为什么拒绝”：`lastRejectionReason`、`circuitState`、`windowFailures`、`windowSlowCalls` 和策略阈值必须在同一屏可见。

### Events

Events 页面用于按时间查最近治理动作：

1. 顶部筛选器，包含 action、outcome、reason、kind、搜索。
2. 事件表，默认倒序。
3. 点击事件行展开低基数字段，例如 `resourceKey`、`trafficPriority`、`durationBucket`。

事件详情不能展示 payload、异常全文或堆栈。

### Settings Readonly

Settings Readonly 只说明当前只读配置和边界：

1. Console path：`/nexary/console`。
2. API path：`/nexary/console/api`。
3. Recent event buffer 上限。
4. 诊断数据来源：当前 JVM 的 governance snapshot。
5. 只读边界：不编辑策略、不下发配置、不聚合实例。
6. 启用提示：展示 `nexary.console.enabled=true` 和诊断端点启用要求。

Settings 页面不展示完整 `application.yml`，避免误把敏感配置带到浏览器。

## 数据字段约束

前端只能依赖低基数字段：

| 类别 | 允许字段 |
| --- | --- |
| 资源 | `resourceKey`、`kind`、`name`、`provider`、`operation`、`priority` |
| 策略 | deadline、rate limit、max concurrency、degraded、circuit breaker 阈值 |
| 运行时 | `circuitState`、`windowCalls`、`windowFailures`、`windowSlowCalls`、`totalRejections`、`lastOutcome`、`lastRejectionReason` |
| 事件 | `timestamp`、`action`、`outcome`、`rejectionReason`、`durationBucket` |

如果后端返回未知字段，首版前端忽略。不要把未知字段自动渲染成表格，避免泄露高数量业务信息。

## 前端实现注意点

- 使用 Vue 3 + Vite + TypeScript。路由可用 hash mode，方便打进 Spring Boot static resources。
- API client 只发 GET 请求。代码里不要预留 `POST /policy`、`PUT /settings` 这类写接口。
- 所有接口要有 loading、empty、error、success 四种状态。
- 表格和筛选状态保持在前端内存；Events 不要求服务端查询语法。
- 页面刷新不应中断当前筛选；刷新失败时保留上一次成功数据并显示小型错误提示。
- 数字格式化要稳定：未知显示 `-`，无限制显示 `unlimited`，Duration 保留原始单位。
- CSS 使用局部组件样式或轻量全局变量；不要引入重型 UI 框架作为首版依赖。
- 移动端小于 768px 时左侧导航收起到顶部，表格改为横向滚动，不重排成营销卡片。
- Playwright 验证至少覆盖 Overview、Resources、Resource Detail、Events 和 Settings Readonly 非空、无重叠、错误状态可见。
- v0.10 硬化要求直接访问 `/nexary/console/resources`、`/nexary/console/resources/{resourceKey}`、`/nexary/console/events`、`/nexary/console/settings` 时也能加载同一套静态资源，不依赖先进入 Overview。
- 打包验证必须确认 jar 内存在入口 HTML、JS 和 CSS；静态资源缺失时测试应失败，不能只看到空白页。

## 验收清单

- Overview 能在无资源、有正常资源、有熔断打开资源三种状态下稳定展示。
- Resources 默认排序能把 `OPEN`、`HALF_OPEN` 和最近拒绝资源排在前面。
- Resource Detail 能同时看到策略摘要、窗口统计和最近事件。
- Events 能按 kind、state、reason 和搜索词筛选。
- Settings Readonly 明确写出只读边界，不给用户策略编辑入口。
- 页面不展示高数量业务字段、payload、完整异常消息或堆栈。
- 直接 URL / deep link 可打开对应页面，刷新后仍显示可用状态。
- 本地治理样例可用浏览器验证关键页面非空，并覆盖空数据、正常数据和熔断打开数据。
