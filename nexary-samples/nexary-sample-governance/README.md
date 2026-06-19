# nexary-sample-governance

这个样例演示如何在 Spring Boot 服务里用 Nexary 做本地保护：给接口起稳定 resource 名，带上流量标签和 deadline，然后通过 `GovernanceRuntime` 执行业务代码。

样例同时引入 `nexary-observation-micrometer-spring-boot-starter`，并在本地注册 `SimpleMeterRegistry`。触发限流、降级或并发隔离时，治理事件会写入 `nexary.observation.events.total` 和 `nexary.observation.events.duration`。

## 运行

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

可访问的入口：

- `GET /governance/profiles/{userId}`：正常查询，超过本地限流时走 fallback。
- `GET /governance/degraded/{userId}`：演示手动降级，直接返回 fallback。

## 可以复制的代码

- `GovernanceSampleConfiguration`：本地 policy 配置。
- `GovernanceSampleConfiguration#governanceSampleMeterRegistry`：本地样例用的 Micrometer registry，真实服务可以换成自己的 registry。
- `GovernanceSampleController`：业务入口如何创建 `GovernanceContext` 并调用 `GovernanceRuntime`。
- `ProfileQueryService`：主逻辑和 fallback 分开写，业务代码不依赖第三方治理框架。

resource 名要稳定，例如 `profile-api/get-profile`。不要把 userId、订单号、cache key、message id 放进 resource 名或治理标签。Micrometer 指标只保留 `resource_kind`、`governance_action`、`traffic_channel`、`traffic_priority`、`outcome` 等低数量标签。
