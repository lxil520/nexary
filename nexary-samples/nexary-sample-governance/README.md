# nexary-sample-governance

这个样例演示三件事：

- Spring Boot starter 如何用 `application.yml` 配置 deadline、限流、并发隔离和手动降级。
- 本地治理数据平面如何把 `GovernanceContext`、`GovernanceRuntime`、fallback 和低数量诊断快照串起来。
- 本地熔断流程如何在一个下游调用上跑起来：正常调用、多次失败、慢调用、熔断打开、fallback、半开探测、恢复或重开。
- 只读治理诊断 Console 如何读取当前 JVM 的治理资源、窗口、熔断状态和最近事件。

v0.11 增加请求失效终止：deadline 已经过期、上游已经取消、或者 Gateway 发现客户端断开时，请求应尽快停止。这个样例同时演示进入业务前的取消和业务循环里的协作式停止。Nexary 不替代 Sentinel，Sentinel provider 放在 v0.12，retry stop 继续传播放在 v0.13。

样例引入 `nexary-observation-micrometer-spring-boot-starter`，并在测试里注册 `SimpleMeterRegistry`。触发限流、降级或并发隔离时，治理事件会写入 `nexary.observation.events.total` 和 `nexary.observation.events.duration`。

这个样例带一个本地只读页面，但不是远程控制台、sidecar、agent 或多实例治理服务。所有窗口、计数和诊断字段都来自当前 JVM。

## 运行

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

另开一个终端访问：

```bash
curl http://localhost:8080/governance/profiles/u-1
curl http://localhost:8080/governance/profiles/u-2
curl http://localhost:8080/governance/profiles/u-3
```

第三次请求超过 `profile-api/get-profile` 的 `2/min` 配置后会走 fallback。

手动降级路径：

```bash
curl http://localhost:8080/governance/degraded/u-1
```

返回里的 `source` 是 `fallback`，因为 `inventory-service/reserve` 配了 `degraded=true`。

请求取消路径：

```bash
curl -H 'Nexary-Cancellation-Id: demo-hidden-id' \
  -H 'Nexary-Cancel-Reason: CLIENT_DISCONNECTED' \
  'http://localhost:8080/governance/cancellation/slow/u-1?durationMillis=3000'
```

这次不会执行慢业务动作，返回里的 `source` 是 `fallback`。再看最近事件：

```bash
curl -s http://localhost:8080/nexary/governance/events
```

事件里可以看到 `action=CANCEL`、`outcome=CANCELLED`、`cancellationReason=CLIENT_DISCONNECTED`，但不会出现 `demo-hidden-id`。

## 只读诊断端点

样例打开了 `nexary.governance.diagnostics.enabled=true`。查看当前进程里的本地治理汇总、资源目录和最近事件：

```bash
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
```

也可以打开只读页面：

```bash
open http://localhost:8080/nexary/console
```

为了看到熔断和拒绝字段，可以先打开熔断再查询：

```bash
curl -s -X POST http://localhost:8080/governance/circuit/reset
curl -s "http://localhost:8080/governance/circuit/profiles/u-2?mode=failure"
curl -s "http://localhost:8080/governance/circuit/profiles/u-3?mode=failure"
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
```

常看字段：

| 字段 | 含义 |
| --- | --- |
| `resourceKey` | 稳定资源 key。 |
| `kind` / `name` / `provider` / `operation` | 资源目录字段。 |
| `priority` | 本地计数使用的优先级桶。 |
| `policySnapshot` | 当前资源最近一次应用到的策略。 |
| `runtimeSnapshot` | 当前资源最近一次运行后的窗口、熔断和拒绝状态。 |
| `circuitState` | `CLOSED`、`OPEN` 或 `HALF_OPEN`。 |
| `windowCalls` / `windowFailures` / `windowSlowCalls` | 当前滑动窗口内的完成调用、失败调用和慢调用数量。 |
| `totalRejections` | 当前 JVM 内的本地治理拒绝次数。 |
| `lastRejectionReason` | 最近一次拒绝原因，例如 `CIRCUIT_OPEN`、`RATE_LIMITED`、`CONCURRENCY_LIMITED`。 |
| `lastCancellationReason` | 最近一次取消原因，例如 `CLIENT_DISCONNECTED`、`DEADLINE_EXPIRED`、`UPSTREAM_CANCELLED`。 |
| `lastOutcome` | 最近一次结果，例如 `SUCCESS`、`FAILURE`、`REJECTED`。 |
| `action` | 最近事件动作，例如 `EXECUTE`、`REJECT`、`FALLBACK`、`CANCEL`。 |
| `cancellationReason` | 最近事件里的取消原因；没有取消时是 `NONE`。 |
| `durationBucket` | 粗粒度耗时桶。 |

这些端点只读，默认不会暴露；starter 只有在显式配置 `nexary.governance.diagnostics.enabled=true` 后才注册 HTTP 路径。

## 熔断流程

先清空样例里的本地状态：

```bash
curl -X POST http://localhost:8080/governance/circuit/reset
```

正常调用：

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-1?mode=success"
```

返回里的 `circuitState` 是 `CLOSED`，`outcome` 是 `primary`。

连续失败两次会打开熔断：

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-2?mode=failure"
curl "http://localhost:8080/governance/circuit/profiles/u-3?mode=failure"
```

第二次返回 `circuitState=OPEN`，`outcome=failure_opened`。熔断打开后再请求一次：

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-4?mode=success"
```

这次不会调用主逻辑，返回 `source=fallback`、`outcome=fallback_open`、`lastRejectionReason=CIRCUIT_OPEN`。

等待半开窗口后发一个成功探测：

```bash
sleep 0.2
curl "http://localhost:8080/governance/circuit/profiles/u-5?mode=success"
```

返回 `circuitState=CLOSED`、`outcome=half_open_recovered`。如果半开探测失败，会返回 `outcome=half_open_reopened` 并重新进入 `OPEN`。

慢调用也能打开熔断：

```bash
curl -X POST http://localhost:8080/governance/circuit/reset
curl "http://localhost:8080/governance/circuit/profiles/u-6?mode=slow"
curl "http://localhost:8080/governance/circuit/profiles/u-7?mode=slow"
```

第二次返回 `circuitState=OPEN`，`windowSlowCalls=2`，`outcome=slow_opened`。

## 可以复制的配置

`application.yml` 里的配置适合复制到 Spring Boot 3.3 主线项目：

```yaml
nexary:
  governance:
    runtime:
      enabled: true
    diagnostics:
      enabled: true
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
```

熔断流程在这个样例里由 `LocalCircuitBreakerProfileGateway` 通过本地 `GovernanceRuntime` 演示。它用代码创建同等策略，`reset` 只是为了清空样例状态，方便重复跑 curl；上面的 YAML 是给接入 starter 的应用复制使用。

## 可以复制的代码

- `GovernanceSampleConfiguration`：放稳定资源名，不放用户 id、订单号、cache key 或 message id。
- `GovernanceSampleController`：业务入口如何创建 `GovernanceContext`，以及如何暴露熔断演示接口。
- `LocalCircuitBreakerProfileGateway`：通过本地治理运行时演示 `CLOSED`、`OPEN`、`HALF_OPEN` 的转换。
- `ProfileQueryService`：主逻辑、慢调用、取消检查、失败调用和 fallback 分开写。

资源名要稳定，例如 `profile-api/get-profile`。Micrometer 指标只保留 `resource_kind`、`governance_action`、`traffic_channel`、`traffic_priority`、`outcome` 等低数量标签。

## 不包含什么

- 不提供远程控制台、sidecar 或 agent。
- 不做远程策略下发。
- 不同步多实例之间的熔断、限流、并发隔离或诊断状态。
- 不替代 Sentinel；当前样例不声明 Sentinel 规则、dashboard 或 cluster flow control。
- 不强制中断已经进入普通 Java 业务方法的线程；业务循环需要像样例一样定期检查 `CancellationContext`。
