# nexary-sample-governance

这个样例演示两件事：

- Spring Boot starter 如何用 `application.yml` 配置 deadline、限流、并发隔离和手动降级。
- v0.6 本地熔断流程如何在一个下游调用上跑起来：正常调用、多次失败、慢调用、熔断打开、fallback、半开探测、恢复或重开。

样例引入 `nexary-observation-micrometer-spring-boot-starter`，并在测试里注册 `SimpleMeterRegistry`。触发限流、降级或并发隔离时，治理事件会写入 `nexary.observation.events.total` 和 `nexary.observation.events.duration`。

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
- `ProfileQueryService`：主逻辑、慢调用、失败调用和 fallback 分开写。

资源名要稳定，例如 `profile-api/get-profile`。Micrometer 指标只保留 `resource_kind`、`governance_action`、`traffic_channel`、`traffic_priority`、`outcome` 等低数量标签。
