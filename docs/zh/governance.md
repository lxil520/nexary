# 治理

治理用来给本地 Java 调用加保护：deadline 到了就别再启动新动作，请求太密就拒绝，同一个资源并发过高就让后面的调用走 fallback，需要临时停用某个下游时也不改业务代码。v0.6 样例还演示了一个本地熔断流程：正常调用、多次失败、慢调用、熔断打开、fallback、半开探测、恢复或重开。

它的边界很明确：这是 SDK 级本地治理，不是控制台、sidecar、agent、远程下发配置或全局服务治理平台。

## 引入依赖

Spring Boot 3.3 主线使用：

```groovy
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation "com.aweimao:nexary-governance-spring-boot-starter"
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

## 查看 runtime 诊断快照

样例提供一个只读接口，用来确认本地熔断和拒绝状态：

```bash
curl -s http://localhost:8080/governance/circuit/state
```

常用字段：

| 字段 | 说明 |
| --- | --- |
| `resourceKey` | 稳定资源 key，样例里是 profile 下游调用。 |
| `priority` | 本地策略按哪个优先级桶计数。 |
| `circuitState` | `CLOSED`、`OPEN` 或 `HALF_OPEN`。 |
| `windowCalls` / `windowFailures` / `windowSlowCalls` | 滑动窗口内已完成调用、失败调用、慢调用数量。 |
| `totalRejections` | 当前 JVM 内被本地治理拒绝的总数。 |
| `lastRejectionReason` | 最近一次拒绝原因，例如 `CIRCUIT_OPEN`、`RATE_LIMITED`、`BULKHEAD_FULL`。 |
| `activeConcurrency` / `maxConcurrency` | 当前并发和策略上限。 |
| `maxRequestsPerWindow` / `rateLimitWindow` | 限流窗口配置。 |
| `lastOutcome` | 最近一次本地治理结果，常见值有 `SUCCESS`、`FAILURE`、`REJECTED`。 |

这些字段是诊断用的低数量字段，不包含 userId、订单号、messageId、cache key、异常全文或堆栈。

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
- Messaging 的 deadline header 只对新发送的消息有效；历史积压消息没有这个 header。
- Job 的 `execution-timeout` 仍负责执行中的超时控制；`start-deadline` 只判断这次触发是否还值得启动。
- 这里没有控制台、sidecar、agent、远程动态配置或自动下发策略。

## 验证

```bash
./gradlew :nexary-boot:nexary-governance-spring-boot-starter:check
./gradlew :nexary-samples:nexary-sample-governance:check
./gradlew check
```
