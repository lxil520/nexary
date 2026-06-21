# Governance

Governance 用来给业务调用加一层本地保护：deadline 到了就别再启动新动作，请求太密就直接拒绝，同一个资源并发过高就让后面的调用走 fallback，需要临时停用某个下游时也不改业务代码。

业务代码仍然只写 `GovernanceContext`、`GovernanceRuntime`、`CacheClient`、`MessagePublisher`、`NexaryJob` 这些 Nexary API。Redis、Kafka、RocketMQ、ActiveMQ、XXL-JOB、PowerJob 的原生类型不会出现在业务接口里。

## 先引入 starter

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

## 写策略配置

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

## 哪些路径已经接入

| 路径 | v0.4 行为 |
| --- | --- |
| `GovernanceRuntime` | 执行前检查 deadline、限流、并发隔离和降级；拒绝时发布治理事件；有 fallback 就执行 fallback，否则抛出 `GovernanceRejectedException`。 |
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

## 现在的边界

- deadline 是启动前检查和上下文传播，不会强行杀掉已经进入业务方法的普通 Java 代码。
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
