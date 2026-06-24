# 治理

治理用来给本地 Java 调用加保护：deadline 到了就别再启动新动作，请求太密就拒绝，同一个资源并发过高就让后面的调用走 fallback，需要临时停用某个下游时也不改业务代码。v0.9 在本地治理数据面上加了只读页面：业务入口把调用交给 `GovernanceRuntime`，运行时在当前 JVM 内完成 deadline、限流、并发隔离、显式降级和熔断判断，并提供低数量诊断快照和页面查看入口。

它的边界很明确：这是 SDK 级本地治理和本地只读页面，不是远程控制台、sidecar、agent、远程下发配置或全局服务治理平台。熔断窗口、限流窗口、拒绝计数和诊断快照都只属于当前进程，不做跨实例状态同步。

## 引入依赖

Spring Boot 3.3 主线使用：

```groovy
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation "com.aweimao:nexary-governance-spring-boot-starter"
implementation "com.aweimao:nexary-console-spring-boot-starter"
implementation "com.aweimao:nexary-observation-micrometer-spring-boot-starter"
```

样例工程可以直接运行：

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

## 配置 starter 策略

`application.yml` 适合先配置 deadline、限流、并发隔离和显式降级：

```yaml
nexary:
  governance:
    runtime:
      enabled: true
    diagnostics:
      enabled: true
  console:
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

这些字段是诊断用的低基数字段，不包含 userId、订单号、messageId、cache key、payload、异常全文或堆栈。端点只读；starter 默认不打开这些 HTTP 路径。

## 打开只读 Console

如果应用同时引入 `nexary-console-spring-boot-starter`，并设置 `nexary.console.enabled=true`，可以访问：

```bash
open http://localhost:8080/nexary/console
```

Console 读取 `/nexary/console/api` 下的只读接口。它展示当前 JVM 的 summary、resources、resource detail、events 和只读设置提示。它不会写策略，不会下发配置，也不会汇总多个实例。

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
- 熔断窗口是当前 JVM 内的本地状态；多实例之间不会共享失败计数、慢调用计数或半开探测结果。
- Cache 的治理包裹只声明在 Spring Boot 3 Redis 主线；Boot2 / Boot4 的 cache 入口要按对应样例和测试结果再扩大说明。
- v0.9 只包含本地只读页面，不包含远程控制台、sidecar、agent、远程动态配置或跨实例状态同步。
- Messaging 的 deadline header 只对新发送的消息有效；历史积压消息没有这个 header。
- Job 的 `execution-timeout` 仍负责执行中的超时控制；`start-deadline` 只判断这次触发是否还值得启动。
- 这里没有远程控制台、sidecar、agent、远程动态配置或自动下发策略。

## 验证

```bash
./gradlew :nexary-boot:nexary-governance-spring-boot-starter:check
./gradlew :nexary-samples:nexary-sample-governance:check
./gradlew check
```
