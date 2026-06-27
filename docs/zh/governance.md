# 治理

治理用来给本地 Java 调用加保护：deadline 到了就别再启动新动作，请求太密就拒绝，同一个资源并发过高就让后面的调用走 fallback，需要临时停用某个下游时也不改业务代码。当前已验证路径提供两种执行引擎：默认本地引擎在当前 JVM 内完成 deadline、限流、并发隔离、显式降级和熔断判断；Boot3 Sentinel provider 可以把限流、线程数隔离、慢调用熔断和异常熔断交给 Sentinel 执行。两种路径都复用 Nexary 的资源目录、策略快照、低基数诊断和只读 Console。

它的边界很明确：本地治理运行时不做 sidecar、agent、远程下发配置或跨实例状态同步。治理平台只做资源、信号、拓扑和事故候选的只读汇聚，不修改 Sentinel、Gateway、APM、注册中心或通知渠道配置。

`0.19.0` 不替代 Sentinel Dashboard、Spring Cloud Gateway、SkyWalking、Prometheus、企业 IM、自动摘流平台或分布式 trace 后端。它解决两层问题：本地治理运行时继续处理请求取消、Sentinel provider、停止重试、优先级隔离、异常实例候选和本地 fault trace；治理平台把多个 JVM 或 connector 上报的服务、集群、机房、中间件依赖和低基数信号汇总成只读拓扑、服务列表和事故候选。v0.18 会把同一服务、集群、机房内的慢调用、错误率、Sentinel block、Gateway 断开、停止重试和异常实例信号聚合成一条事故候选，并展示首要资源、影响资源数、证据时间线和建议检查项。v0.19 把 Console Platform Mode 重做为运维工作台，第一屏优先显示事故队列、拓扑影响、服务健康和证据链，而不是继续堆计数卡片。v0.11 的请求失效终止仍在 Sentinel entry 前检查，已取消请求不会进入 Sentinel 统计窗口；v0.15 的实例健康只记录真实下游结果，不把 Sentinel block 当成实例故障；v0.16 trace 只保存低基数字段，不保存业务参数；平台信号也拒绝 userId、tenant、payload、cache key、message id、异常全文、stack trace、token 和密码。

## 引入依赖

Spring Boot 3.3 主线使用：

```groovy
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation "com.aweimao:nexary-governance-spring-boot-starter"
implementation "com.aweimao:nexary-console-spring-boot-starter"
implementation "com.aweimao:nexary-observation-micrometer-spring-boot-starter"
```

如果要使用 Boot3 Sentinel provider，再加：

```groovy
implementation "com.aweimao:nexary-governance-sentinel-spring-boot-starter"
```

如果应用本身是 Spring Cloud Gateway 入口，再加：

```groovy
implementation "com.aweimao:nexary-governance-gateway-spring-boot-starter"
```

样例工程可以直接运行：

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

如果要看治理平台只读视图，运行：

```bash
./gradlew :nexary-samples:nexary-sample-governance-platform:bootRun
curl -s http://localhost:18092/api/platform/topology
curl -s http://localhost:18092/api/platform/incidents
open http://localhost:18092/nexary/console/platform
```

## v0.17 治理平台只读地基

平台地基包含三个模块：

- `nexary-governance-platform-api`：资产、依赖、连接器、信号、拓扑和事故候选模型。
- `nexary-governance-platform-server`：`POST /api/platform/resources`、`POST /api/platform/signals`、`GET /api/platform/topology`、`GET /api/platform/services`、`GET /api/platform/incidents`。
- `nexary-governance-platform-storage-postgres`：显式接入的 Postgres repository，demo 默认仍使用内存存储。

Platform Mode 的第一版只读查看服务、依赖、事故候选和连接器状态。它不提供策略编辑，不写 Sentinel 规则，不改 Gateway route，不发送生产告警。

## v0.18 事故证据链

`0.18.0` 在 v0.17 的平台地基上补齐事故证据链：

- `IncidentCandidate` 增加 `startedAt`、`primaryResourceKey`、`evidenceCount` 和 `impactedResourceCount`。
- `EvidenceItem` 增加服务、集群、机房、耗时桶、引用类型和引用 key。
- `/api/platform/incidents/{incidentKey}` 可以读取单个事故候选。
- 依赖边增加 warning / critical 计数，Console 可以看到哪条依赖有证据。
- `nexary-sample-governance-platform` demo 会生成 open-api 和 room-resource 两类事故候选，便于本地验证。

`referenceType` 和 `referenceKey` 只是低基数定位引用，用来说明这条证据来自指标查询、Sentinel resource、Gateway route、实例健康或 fault trace。v0.18 不连接真实 SkyWalking、Prometheus、Sentinel Dashboard 或 Gateway 管理 API；这些只读 connector 按 roadmap 放到 v0.20。

## v0.19 Console 产品重设计

`0.19.0` 不新增治理执行能力，重点是把 Platform Mode 从开发调试页改成日常运维页：

- 第一屏显示当前健康、事故数、受影响服务数、服务数、依赖数和接入状态。
- 左侧是事故队列，按严重程度和证据数排序，可按严重程度过滤，也可搜索服务、集群、机房或资源。
- 中间显示拓扑影响和服务健康表，帮助先判断哪条依赖、哪个服务组需要处理。
- 右侧显示选中事故的影响范围、首要资源、建议检查项和证据链。
- 移动窄屏使用事故、拓扑、服务三个视图切换，避免表格和证据面板互相挤压。

这一版仍然只读：不写策略，不修改 Sentinel / Gateway / APM 配置，不发送生产告警。

## v0.12 Sentinel provider

Sentinel provider 是可选执行引擎。默认仍使用本地引擎；只有显式配置 `provider: sentinel` 后才启用：

```yaml
nexary:
  governance:
    provider: sentinel
    sentinel:
      enabled: true
      transport:
        enabled: false
    diagnostics:
      enabled: true
  console:
    enabled: true
```

策略字段会映射到 Sentinel 规则：

| Nexary policy | Sentinel rule |
| --- | --- |
| `max-requests-per-window` + `rate-limit-window` | QPS flow rule |
| `max-concurrency` | thread-count flow rule |
| `failure-rate-threshold` / `consecutive-failure-threshold` | exception degrade rule |
| `slow-call-threshold` / `slow-call-rate-threshold` / `minimum-calls` | slow-call degrade rule |
| `degraded=true` | Nexary fallback 语义，不伪装成 Sentinel block |

运行样例：

```bash
./gradlew :nexary-samples:nexary-sample-governance-sentinel:run
```

另开终端触发 Sentinel 限流并查看诊断：

```bash
curl -s http://localhost:8080/governance/sentinel/rate
curl -s http://localhost:8080/governance/sentinel/rate
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
NEXARY_GOVERNANCE_SENTINEL_BASE_URL=http://localhost:8080 ./scripts/governance-sentinel/smoke.sh
```

## v0.14 流量隔离与优先级

当在线请求和批处理共用同一个资源时，可以给同一 resource 配置默认策略和低优先级覆盖策略：

```yaml
nexary:
  governance:
    resources:
      shared-downstream:
        kind: downstream
        name: priority-shared-service
        provider: nexary
        operation: load
        max-requests-per-window: 100
        rate-limit-window: 1s
        max-concurrency: 32
        priorities:
          low:
            max-requests-per-window: 1
            rate-limit-window: 1m
            max-concurrency: 1
```

业务代码只绑定固定 traffic class 和 priority，不传 userId、tenant、订单号或任意业务 key：

```java
GovernanceContext context = GovernanceContext.builder()
        .resource(GovernanceResource.downstream("priority-shared-service", "load"))
        .trafficTag(TrafficTag.builder()
                .channel(TrafficTag.Channel.BATCH)
                .priority(TrafficTag.Priority.LOW)
                .build())
        .build();
```

运行 Sentinel 样例后可以直接触发：

```bash
curl -s http://localhost:8080/governance/sentinel/priority/online
curl -s http://localhost:8080/governance/sentinel/priority/batch
curl -s http://localhost:8080/governance/sentinel/priority/batch
curl -s http://localhost:8080/governance/sentinel/priority/online
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/events
NEXARY_GOVERNANCE_PRIORITY_BASE_URL=http://localhost:8080 ./scripts/governance-priority/smoke.sh
```

重点字段：

- `trafficClass`: `ONLINE`、`OFFLINE`、`BATCH` 或 `BACKGROUND`。
- `priority`: `HIGH`、`NORMAL` 或 `LOW`。
- `isolationReason`: `PRIORITY_RATE_LIMITED`、`PRIORITY_BULKHEAD_FULL`、`PRIORITY_DEGRADED`、`PRIORITY_CIRCUIT_OPEN` 或 `MIXED_TRAFFIC`。
- `isolatedCount`: 当前 JVM 内优先级隔离事件数量。

## v0.16 本地故障定位 trace

Trace diagnostics 默认关闭。开启后，Nexary 只在当前 JVM 内保留最近 trace，用于回答“这次调用为什么停止、哪个 resource 先值得看”：

```yaml
nexary:
  governance:
    diagnostics:
      enabled: true
    trace:
      enabled: true
      max-traces: 128
      max-events-per-trace: 32
      ttl: 10m
      expose-external-trace-id: false
```

它不是 Jaeger、Zipkin、SkyWalking 或 OpenTelemetry exporter。`traceKey` 是本地 diagnostics / Console 查询 key，不进入指标 tag；HTTP JSON 和 Console 不输出 payload、URL query、userId、tenant、messageId、cache key、异常全文或 stack trace。

运行样例：

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--spring.profiles.active=trace'
```

另开终端触发正常调用、deadline、停止重试、优先级隔离和异常实例场景：

```bash
curl -s http://localhost:8080/governance/trace/priority?priority=high
curl -s http://localhost:8080/governance/trace/deadline/trace-deadline
curl -s http://localhost:8080/governance/trace/retry-stop
curl -s http://localhost:8080/governance/trace/priority?priority=low
curl -s http://localhost:8080/governance/trace/priority?priority=low
curl -s -X POST http://localhost:8080/governance/trace/instance-health
curl -s http://localhost:8080/nexary/governance/traces
curl -s http://localhost:8080/nexary/governance/faults/summary
NEXARY_GOVERNANCE_TRACE_BASE_URL=http://localhost:8080 ./scripts/governance-trace/smoke.sh
```

重点字段：

- `terminalOutcome`: trace 的最终结果，例如 `SUCCESS`、`REJECTED`、`CANCELLED`。
- `primaryStopReason`: `DEADLINE_EXPIRED`、`CANCELLED`、`RETRY_STOPPED`、`BLOCKED`、`REJECTED`、`ISOLATED`、`INSTANCE_QUARANTINE_CANDIDATE` 或 `FAILURE`。
- `suggestedResourceKey`: 当前 trace 建议优先排查的 resource。
- `steps[].stage`: `REQUEST`、`GOVERNANCE`、`DOWNSTREAM`、`CACHE`、`MESSAGING`、`JOB`、`INSTANCE_HEALTH` 或 `RETRY`。
- `faultTraceCount` / `stoppedTraceCount`: 当前 JVM 内保留的 trace 数量和停止 trace 数量。

Console 的 Overview 会显示 fault trace 数量，Resources 会显示最近 trace 结果，Events 可以按 trace stage / stop reason 过滤，Trace detail 只读展示 step 时间线。Console 不做策略编辑、封禁按钮或远程下发。

## v0.15 实例异常发现与本地封禁模型

实例健康检测默认关闭。开启后，Nexary 只在当前 JVM 内记录下游实例结果，并给出只读诊断：

```yaml
nexary:
  governance:
    diagnostics:
      enabled: true
    instance-health:
      enabled: true
      window: 60s
      minimum-calls: 20
      suspect-windows: 2
      recovery-windows: 2
      slow-call-threshold: 2s
      slow-ratio-threshold: 0.60
      failure-ratio-threshold: 0.50
      timeout-ratio-threshold: 0.30
      skew-factor-threshold: 3.0
      expose-raw-instance-key: false
```

它不会自动摘除实例，不会调用注册中心、Spring Cloud LoadBalancer、Gateway route、云厂商或 PaaS API。`instanceKey` 默认使用稳定别名或脱敏 fingerprint，诊断字段不写 URL、query、userId、tenant、订单号、payload、异常全文或 stack trace。

运行样例：

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--spring.profiles.active=instance-health'
```

另开终端触发三个模拟实例：

```bash
curl -s -X POST http://localhost:8080/governance/instance-health/scenario
curl -s http://localhost:8080/nexary/governance/instance-health
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/events
NEXARY_GOVERNANCE_INSTANCE_HEALTH_BASE_URL=http://localhost:8080 ./scripts/governance-instance-health/smoke.sh
```

重点字段：

- `instanceHealthState`: `HEALTHY`、`SUSPECT`、`QUARANTINE_CANDIDATE` 或 `RECOVERING`。
- `quarantineReason`: `SERVER_ERROR_RATIO`、`SLOW_RATIO`、`READ_TIMEOUT_SPIKE`、`CONNECT_TIMEOUT_SPIKE`、`RESET_SPIKE` 或 `STATUS_CODE_SKEW`。
- `recoveryAdvice`: `BACKOFF`、`QUARANTINE_CANDIDATE`、`MANUAL_ACTION_REQUIRED` 或 `RECOVERY_PROBE`。
- `instanceSuspectCount`: 当前 JVM 内异常实例候选数量。
- `quarantineCandidateCount`: 当前 JVM 内封禁候选数量。

Console 的 Overview 会显示 suspect / quarantine candidate / recovery probe 数量，Resource detail 会显示实例状态表，Events 会显示 `INSTANCE_SUSPECT`、`QUARANTINE_CANDIDATE`、`RECOVERY_PROBE` 和 `INSTANCE_RECOVERED`。这些页面仍是只读页面，没有封禁按钮和策略编辑。

## v0.13 停止重试传播

停止重试传播使用固定枚举，不把业务 key、message id、cache key、payload 或异常全文写进事件：

- `DEADLINE_EXPIRED`
- `CANCELLED`
- `CLIENT_DISCONNECTED`
- `UPSTREAM_CANCELLED`
- `SHUTDOWN`
- `RATE_LIMITED`
- `BULKHEAD_FULL`
- `CIRCUIT_OPEN`
- `DEGRADED`
- `RETRY_EXHAUSTED`
- `TIMEOUT`
- `REJECTED`
- `UNKNOWN`

触发 Sentinel 样例里的停止重试路径：

```bash
curl -s http://localhost:8080/governance/sentinel/retry-stop
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/events
```

重点字段：

- `retryStoppedCount`: 当前 JVM 内停止重试事件数量。
- `retryStopReason`: 最近事件里的停止重试原因。
- `lastRetryStopReason`: 资源快照里的最近停止重试原因。

消息消费和任务执行遇到治理拒绝、deadline 过期、请求取消或超时时，会停止当前 retry loop；普通业务异常仍按各自 retry policy 重试，直到重试耗尽。

新增诊断字段保持低基数：

| 字段 | 说明 |
| --- | --- |
| `engine` | `LOCAL` 或 `SENTINEL`。 |
| `blockReason` | 最近事件的 Sentinel 拦截原因，例如 `RATE_LIMITED`、`BULKHEAD_FULL`、`CIRCUIT_OPEN`。 |
| `lastBlockReason` | 资源快照里的最近 Sentinel 拦截原因。 |
| `blockedCount` | 当前 JVM 内 Sentinel 拦截次数。 |
| `sentinelResourceCount` | 当前 JVM 内由 Sentinel 执行的资源数量。 |

Sentinel transport 默认关闭，不要求 Sentinel Dashboard。只有显式设置 `nexary.governance.sentinel.transport.enabled=true` 并配置 dashboard server 时，才会设置 Sentinel dashboard 地址。当前只声明 Spring Boot 3.3 主线 Sentinel provider；Boot2 / Boot4 Sentinel starter 要等独立样例和 gate 通过后再写入支持矩阵。

## 配置 starter 策略

`application.yml` 适合先配置 deadline、限流、并发隔离和显式降级：

```yaml
nexary:
  governance:
    runtime:
      enabled: true
    diagnostics:
      enabled: true
    default-policy:
      max-requests-per-window: 100
      rate-limit-window: 1s
      max-concurrency: 64
    resources:
      profile-api:
        kind: http
        name: profile-api
        provider: nexary
        operation: get-profile
        deadline: 300ms
        max-requests-per-window: 2
        rate-limit-window: 1m
        max-concurrency: 1
      inventory-reserve:
        kind: downstream
        name: inventory-service
        provider: nexary
        operation: reserve
        degraded: true
      profile-service:
        kind: downstream
        name: profile-service
        provider: nexary
        operation: load-profile
        circuit-breaker:
          enabled: true
          window: 5s
          minimum-calls: 2
          failure-rate-threshold: 100
          slow-call-threshold: 100ms
          slow-call-rate-threshold: 100
          half-open-probe-calls: 1
          open-state-duration: 150ms
          sliding-window-size: 8
          consecutive-failure-threshold: 2
  console:
    enabled: true
```

`default-policy` 兜底；`resources` 里的每一项只匹配一个稳定资源。`kind` 可选 `http`、`downstream`、`cache`、`messaging`、`job`、`service`、`custom`。`name`、`provider`、`operation` 必须是固定小集合，不能拼用户 id、订单号、cache key、message id。

需要按优先级覆盖时，可以在资源下面加 `priorities`：

```yaml
nexary:
  governance:
    resources:
      profile-api:
        kind: http
        name: profile-api
        operation: get-profile
        max-requests-per-window: 10
        priorities:
          low:
            degraded: true
          high:
            max-requests-per-window: 100
```

## 在业务入口使用

```java
GovernanceContext context = GovernanceContext.builder()
        .resource(GovernanceResource.http("profile-api", "get-profile"))
        .trafficTag(TrafficTag.builder()
                .channel(TrafficTag.Channel.ONLINE)
                .priority(TrafficTag.Priority.NORMAL)
                .build())
        .deadline(Instant.now().plusMillis(300))
        .build();

return governanceRuntime.execute(
        context,
        () -> profileService.load(userId),
        () -> profileService.fallback(userId));
```

`deadline` 会同步写入旧的 `DeadlineContext`，已有 cache、messaging、job 代码仍然能沿用同一个截止时间。

`nexary.governance.diagnostics.enabled` 默认是 `false`。只有需要本地排查时才打开；打开后只提供 GET 端点，不接受策略修改。

## v0.11 请求失效终止

v0.11 要处理的是 stale request cancellation，而不是熔断平台替换。它包含两层：

- 已经过期的 deadline 不应再启动主逻辑。
- 已经被上游取消、超时或判定无价值的请求，应在进入下游调用前停止。
- 如果请求已经进入业务循环，业务代码可以定期检查 `CancellationContext.cancelled()` 并尽快返回。
- Gateway 入口会向下游传播 deadline 和 cancellation id；客户端断开后，Gateway 会调用下游 `POST /nexary/governance/cancellations` 通知当前 JVM 内的 token 取消。
- 停止结果要能在本地诊断和观测事件里看到，字段仍然保持低数量。
- fallback 只在业务明确提供且语义合理时运行；否则返回本地治理拒绝。

下游服务默认不会暴露取消接收端点。需要显式打开：

```yaml
nexary:
  governance:
    cancellation:
      receiver:
        enabled: true
```

运行 downstream 样例后可以直接检查取消诊断：

```bash
NEXARY_GOVERNANCE_CANCELLATION_BASE_URL=http://localhost:28091 \
  ./scripts/governance-cancellation/smoke.sh
```

Gateway 样例和 downstream 样例一起跑时，可以用客户端提前断开验证通知链路：

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--server.port=28091'
NEXARY_GOVERNANCE_DOWNSTREAM_URI=http://127.0.0.1:28091 \
  ./gradlew :nexary-samples:nexary-sample-governance-gateway:run
curl --max-time 1 'http://localhost:28090/gateway/governance/cancellation/slow/u-1?durationMillis=5000' || true
curl -s http://localhost:28091/nexary/governance/summary
curl -s http://localhost:28091/nexary/governance/events
```

验收时只验证当前 JVM 内的取消、拒绝、fallback 和事件记录。不要把 v0.11 写成 Sentinel 替代、跨实例状态同步、远程策略下发或线程强杀；已经进入普通 Java 业务方法的路径只支持协作式停止。

## v0.8 本地治理数据

v0.8 把治理路径按四个层次说明：

| 层次 | 作用 | 用户需要做什么 |
| --- | --- | --- |
| `GovernanceContext` | 描述一次调用的稳定资源、流量标签、优先级和 deadline。 | 在业务入口构造 context，不把 userId、订单号、cache key 或 message id 放进资源名。 |
| `GovernanceRuntime` | 在当前 JVM 内执行 deadline、限流、并发隔离、降级、熔断和 fallback。 | 把主逻辑和 fallback 交给 `execute(...)`，不要在业务代码里重复写治理分支。 |
| `GovernanceResourceDescriptor` | 列出已配置或已运行过的资源、优先级和策略快照。 | 用来确认资源名、provider、operation 和策略是否按预期绑定。 |
| `GovernanceRuntimeSnapshot` / `GovernanceRuntimeEvent` | 暴露当前进程内的低基数字段。 | 只用于排查本地状态；不要把它当成远程控制台或多实例状态。 |

starter 自动装配 `GovernancePolicyRegistry`、`GovernanceRuntime` 和 `GovernanceExecution`。如果应用自己提供同名 Bean，starter 会让出位置。

## 运行熔断样例

熔断流程在 `nexary-sample-governance` 的 `LocalCircuitBreakerProfileGateway` 里演示。这个类创建本地 `GovernanceRuntime` 和同等策略，`reset` 只用于清空样例状态，方便重复执行下面的命令；上面的 `application.yml` 片段是给接入 starter 的应用复制使用。

```bash
curl -X POST http://localhost:8080/governance/circuit/reset
```

正常调用：

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-1?mode=success"
```

能看到 `circuitState=CLOSED`、`outcome=primary`。

连续失败两次：

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-2?mode=failure"
curl "http://localhost:8080/governance/circuit/profiles/u-3?mode=failure"
```

第二次返回 `circuitState=OPEN`、`outcome=failure_opened`。熔断打开后再请求：

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-4?mode=success"
```

返回 `source=fallback`、`outcome=fallback_open`、`lastRejectionReason=CIRCUIT_OPEN`，主逻辑不会执行。

等待半开窗口后发成功探测：

```bash
sleep 0.2
curl "http://localhost:8080/governance/circuit/profiles/u-5?mode=success"
```

返回 `circuitState=CLOSED`、`outcome=half_open_recovered`。如果半开探测失败，结果会是 `outcome=half_open_reopened`，状态重新回到 `OPEN`。

慢调用也能打开熔断：

```bash
curl -X POST http://localhost:8080/governance/circuit/reset
curl "http://localhost:8080/governance/circuit/profiles/u-6?mode=slow"
curl "http://localhost:8080/governance/circuit/profiles/u-7?mode=slow"
```

第二次返回 `circuitState=OPEN`、`windowSlowCalls=2`、`outcome=slow_opened`。

这些字段是本地进程内策略，不会从远程控制台下发，也不会在多个实例之间共享窗口。

## 查看只读诊断端点

样例在 `application.yml` 里打开了 `nexary.governance.diagnostics.enabled=true`，因此可以直接访问 starter 提供的只读端点。先运行样例：

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

另开终端触发几次调用：

```bash
curl -s http://localhost:8080/governance/profiles/u-1
curl -s http://localhost:8080/governance/profiles/u-2
curl -s http://localhost:8080/governance/profiles/u-3
```

查看汇总、资源目录和最近事件：

```bash
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
```

打开熔断后再看诊断：

```bash
curl -s -X POST http://localhost:8080/governance/circuit/reset
curl -s "http://localhost:8080/governance/circuit/profiles/u-1?mode=failure"
curl -s "http://localhost:8080/governance/circuit/profiles/u-2?mode=failure"
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
```

常用字段：

| 字段 | 说明 |
| --- | --- |
| `resourceKey` | 稳定资源 key，样例里是 profile 下游调用。 |
| `kind` / `name` / `provider` / `operation` | 资源目录字段，用来确认策略绑定到了哪个资源。 |
| `priority` | 本地策略按哪个优先级桶计数。 |
| `policySnapshot` | 当前资源最近一次应用到的策略快照。 |
| `runtimeSnapshot` | 当前资源最近一次运行后的窗口、熔断和拒绝状态。 |
| `circuitState` | `CLOSED`、`OPEN` 或 `HALF_OPEN`。 |
| `windowCalls` / `windowFailures` / `windowSlowCalls` | 滑动窗口内已完成调用、失败调用、慢调用数量。 |
| `totalRejections` | 当前 JVM 内被本地治理拒绝的总数。 |
| `lastRejectionReason` | 最近一次拒绝原因，例如 `CIRCUIT_OPEN`、`RATE_LIMITED`、`CONCURRENCY_LIMITED`。 |
| `activeConcurrency` / `maxConcurrency` | 当前并发和策略上限。 |
| `maxRequestsPerWindow` / `rateLimitWindow` | 限流窗口配置。 |
| `lastOutcome` | 最近一次本地治理结果，常见值有 `SUCCESS`、`FAILURE`、`REJECTED`。 |
| `action` | 最近事件动作，常见值有 `EXECUTE`、`REJECT`、`FALLBACK`。 |
| `durationBucket` | 粗粒度耗时桶，不暴露精确业务耗时。 |
| `engine` | 当前资源或事件由 `LOCAL` 还是 `SENTINEL` 执行。 |
| `blockReason` / `lastBlockReason` | Sentinel 拦截原因，只保留固定枚举，不写 Sentinel origin 或业务标识。 |
| `blockedCount` / `sentinelResourceCount` | 当前 JVM 内 Sentinel 拦截次数和 Sentinel 资源数量。 |

这些字段是诊断用的低基数字段，不包含 userId、订单号、messageId、cache key、payload、异常全文或堆栈。端点只读；starter 默认不打开这些 HTTP 路径。

## 打开只读 Console

如果应用同时引入 `nexary-console-spring-boot-starter`，并设置 `nexary.console.enabled=true`，可以访问：

```bash
open http://localhost:8080/nexary/console
```

Console 读取 `/nexary/console/api` 下的只读接口。它展示当前 JVM 的 summary、resources、resource detail、events 和只读设置提示。它不会写策略，不会下发配置，也不会汇总多个实例。

如果只想先看治理页面，可以直接运行 Docker 样例：

```bash
./scripts/console/up.sh
./scripts/console/smoke.sh
open http://127.0.0.1:18090/nexary/console
```

这个容器运行的是 `nexary-sample-governance`，不是独立控制台。页面和诊断接口读取同一个样例进程里的本地治理状态。

## Messaging publish 策略

如果服务已经接入 messaging starter，可以给 publish 资源加本地策略：

```yaml
nexary:
  governance:
    resources:
      message-publish:
        kind: messaging
        name: message-publish
        operation: publish
        max-requests-per-window: 50
        rate-limit-window: 1s
        max-concurrency: 16
```

这个策略只作用在当前 JVM 发起的 publish 调用上。发送结果在 messaging 样例里看：`POST /app-error-logs` 的 `result.status`，以及 `GET /app-error-logs` 的 `published[].publishStatus`、`published[].providerMessageId`、`published[].detail`、`consumed[]`。

## 已接入路径

| 路径 | 行为 |
| --- | --- |
| `GovernanceRuntime` | 执行前检查 deadline、限流、并发隔离和显式降级；拒绝时发布治理事件；有 fallback 就执行 fallback，否则抛出 `GovernanceRejectedException`。 |
| v0.6 样例熔断 | `LocalCircuitBreakerProfileGateway` 通过本地 `GovernanceRuntime` 演示 `CLOSED`、`OPEN`、`HALF_OPEN` 状态转换，覆盖失败、慢调用、拒绝、fallback、恢复和重开。 |
| v0.11 请求失效终止 | deadline 已过期、上游取消或客户端断开时，进入业务前取消或拒绝；执行中路径通过 `CancellationContext` 协作式停止，并记录本地诊断和 observation 事件。 |
| v0.12 Sentinel provider | Spring Boot 3.3 主线可选择 `provider=sentinel`，由 Sentinel 执行 QPS 限流、线程数隔离、慢调用熔断和异常熔断；Nexary 继续提供稳定 Java API、fallback、低基数诊断和 Console。 |
| Cache Redis 主线 | Spring Boot 3 Redis `CacheClient` Bean 会被治理运行时包一层；资源名是 `cache-client`，后端标签是 `redis`，操作名如 `cache.get`、`cache.put`、`cache.batch_get`。 |
| Messaging | publish / consume 会传递 `nexary-deadline-epoch-millis` header；过期消息会在业务 handler 前被拒绝；重试结束和降级会发布治理事件。 |
| Job | 本地 scheduler、XXL-JOB bridge、PowerJob bridge 支持 `start-deadline` 和 `max-concurrent-executions`；被跳过时记录 bounded skip reason。 |
| Observation | Micrometer bridge 会保留治理相关固定 tag，丢弃资源名、tenant、bizKey、异常全文等高数量字段。 |

## 策略字段

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `deadline` | 无 | 本次动作最多还能运行多久。已经带入更早 deadline 时，以更早的为准。 |
| `max-requests-per-window` | 不限制 | 一个窗口内允许启动的次数。`0` 或负数按不限制处理。 |
| `rate-limit-window` | `1s` | 限流窗口。 |
| `max-concurrency` | 不限制 | 同一个资源同一个优先级允许并发运行的数量。 |
| `degraded` | `false` | 为 `true` 时直接走 fallback，不执行主逻辑。 |
| `circuit-breaker.enabled` | `false` | 为 `true` 时记录完成调用，并按下面的字段判断是否打开熔断。 |
| `circuit-breaker.minimum-calls` | `10` | 窗口内至少完成多少次调用后，失败率和慢调用比例才参与判断。 |
| `circuit-breaker.failure-rate-threshold` | `50` | 失败调用比例达到该百分比后打开熔断。 |
| `circuit-breaker.slow-call-threshold` | `1s` | 单次调用耗时达到这个值后计为慢调用。 |
| `circuit-breaker.slow-call-rate-threshold` | `50` | 慢调用比例达到该百分比后打开熔断。 |
| `circuit-breaker.open-state-duration` | `30s` | 熔断打开后等待多久才允许半开探测。 |
| `circuit-breaker.half-open-probe-calls` | `1` | 半开状态允许同时运行的探测调用数。 |
| `circuit-breaker.window` | `30s` | 熔断统计窗口时长。 |
| `circuit-breaker.sliding-window-size` | `100` | 熔断统计窗口最多保留的完成调用数。 |
| `circuit-breaker.consecutive-failure-threshold` | `0` | 连续失败达到该次数时打开熔断；`0` 表示关闭这个触发条件。 |

## 现在的边界

- deadline 是启动前检查和上下文传播，不会强行杀掉已经进入业务方法的普通 Java 代码。
- v0.11 的取消语义覆盖启动前取消和执行中协作式停止；不承诺强制中断已经运行的业务线程，也不替代应用服务器或客户端的连接取消机制。
- Nexary 不替代 Sentinel Dashboard、cluster flow control 或远程规则平台；当前只声明 Boot3 主线的 Sentinel provider。
- Boot2 / Boot4 Sentinel provider 还没有写入支持矩阵；通过独立样例和 gate 前不要复制 Boot3 starter 到 Boot2 / Boot4 应用。
- 熔断窗口是当前 JVM 内的本地状态；多实例之间不会共享失败计数、慢调用计数或半开探测结果。
- Cache 的治理包裹只声明在 Spring Boot 3 Redis 主线；Boot2 / Boot4 的 cache 入口要按对应样例和测试结果再扩大说明。
- v0.10 继续只包含本地只读页面和本地诊断硬化，不包含远程控制台、sidecar、agent、远程动态配置或跨实例状态同步。
- Messaging 的 deadline header 只对新发送的消息有效；历史积压消息没有这个 header。
- Job 的 `execution-timeout` 仍负责执行中的超时控制；`start-deadline` 只判断这次触发是否还值得启动。
- 这里没有远程控制台、sidecar、agent、远程动态配置或自动下发策略。

## 验证

```bash
./gradlew :nexary-boot:nexary-governance-spring-boot-starter:check
./gradlew :nexary-governance:nexary-governance-sentinel:check
./gradlew :nexary-boot:nexary-governance-sentinel-spring-boot-starter:check
./gradlew :nexary-samples:nexary-sample-governance:check
./gradlew :nexary-samples:nexary-sample-governance-sentinel:check
./scripts/governance-sentinel/smoke.sh
./gradlew check
```
