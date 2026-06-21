# nexary-sample-governance

这个样例演示如何在 Spring Boot 服务里用 Nexary 做本地保护：策略写在 `application.yml`，业务入口只负责声明资源名、流量标签和 deadline，然后通过 `GovernanceRuntime` 调用业务代码。

样例同时引入 `nexary-observation-micrometer-spring-boot-starter`，并在本地注册 `SimpleMeterRegistry`。触发限流、降级或并发隔离时，治理事件会写入 `nexary.observation.events.total` 和 `nexary.observation.events.duration`。

## 运行

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

可访问的入口：

- `GET /governance/profiles/{userId}`：使用 `profile-api/get-profile` 策略。连续请求超过 `2/min` 后走 fallback。
- `GET /governance/degraded/{userId}`：使用 `inventory-service/reserve` 策略。`degraded=true` 时直接走 fallback。

## 可以复制的配置

```yaml
nexary:
  governance:
    resources:
      profile-api:
        kind: http
        name: profile-api
        operation: get-profile
        deadline: 300ms
        max-requests-per-window: 2
        rate-limit-window: 1m
        max-concurrency: 1
```

## 可以复制的代码

- `GovernanceSampleConfiguration`：放稳定资源名，不放策略。
- `GovernanceSampleController`：业务入口如何创建 `GovernanceContext` 并调用 `GovernanceRuntime`。
- `ProfileQueryService`：主逻辑和 fallback 分开写，业务代码不依赖第三方治理框架。

资源名要稳定，例如 `profile-api/get-profile`。不要把 userId、订单号、cache key、message id 放进资源名或治理标签。Micrometer 指标只保留 `resource_kind`、`governance_action`、`traffic_channel`、`traffic_priority`、`outcome` 等低数量标签。
