# Governance

Governance 是 Nexary v0.3 增加的基础语义。它先把几个会反复用到的概念定清楚：这次调用还剩多少时间、属于哪类流量、优先级是什么、被保护的是哪个资源，以及重试什么时候应该停。

这一层只提供 Java API 和事件对象。它不内置限流器、隔离舱、降级执行器，也不把任何监控后端或中间件 SDK 暴露给业务代码。

## 在业务调用前传上下文

在进入一个业务入口或调用下游前，创建 `GovernanceContext`：

```java
GovernanceContext context = GovernanceContext.builder()
        .deadline(Instant.now().plusMillis(200))
        .trafficTag(TrafficTag.builder()
                .channel(TrafficTag.Channel.ONLINE)
                .priority(TrafficTag.Priority.HIGH)
                .build())
        .resource(GovernanceResource.service("checkout-api"))
        .build();

return GovernanceContext.callWithContext(context, () -> checkoutService.submit(order));
```

`GovernanceContext.callWithContext(...)` 会同时设置旧的 `DeadlineContext`。已经读取 `DeadlineContext.current()` 的 cache、messaging、job 代码不需要换一套 deadline 入口。

## 判断 deadline

如果只想在执行前判断还能不能继续，可以用 `TimeoutDecision`：

```java
TimeoutDecision decision = TimeoutDecision.from(context, Instant.now());
if (!decision.isAllowed()) {
    return CheckoutResult.timeout();
}
```

`reason()` 目前只会返回固定值，例如 `allowed` 或 `deadline_exceeded`。不要把用户 id、订单号、异常全文之类的动态内容放进 reason。

## 资源名怎么取

`GovernanceResource` 里的 `name` 和 `operation` 要是稳定、低数量的配置名：

- `checkout-api`
- `payment-events`
- `nightly-sync`
- `redis-cache`

例如 `GovernanceResource.http("profile-api", "get-profile")` 里，`profile-api` 是资源名，`get-profile` 是操作名。不要把真实 URL、用户 id 或订单号拼进去。

不要放这些值：

- 用户 id、订单号、手机号
- cache key、message id、execution id
- token、异常全文、堆栈

这些值会让策略匹配和事件聚合变得不可控，也可能把业务数据带进日志或监控系统。

## 重试什么时候停

下游已经明确拒绝重试时，业务代码或 provider 可以使用 `RetrySignal.stop("reason")`。`reason` 只给本地判断使用，不应该进入事件 tag。

核心事件工厂会把这类结果记成 `governance.retry.stopped`，并按重试次数放进固定桶，例如 `0`、`1`、`2`、`3_5`、`6_10`、`gt_10`。

## 事件对象

`GovernanceObservationEvents` 只创建事件对象，不负责把事件送到任何监控后端。当前预留的事件名：

- `governance.deadline.exceeded`
- `governance.retry.stopped`
- `governance.rate_limited`
- `governance.degraded`
- `governance.bulkhead.rejected`

这些名字给后续的本地运行时和观测模块复用。事件里不放 payload、cache key、message id、execution id、token、异常全文或堆栈。

## 现在不做什么

- 不内置限流、隔离、降级执行逻辑。
- 不内置控制台。
- 不做 sidecar、agent 或平台托管。
- 不做自动封禁、自动根因定位或 IDC 切流。
- 不把 Sentinel、Resilience4j、Micrometer、Redis、Kafka、RocketMQ、PowerJob、ActiveMQ 类型放进业务 API。
