# Observation 与 Micrometer

Nexary 的 cache、messaging、job 会发 `NexaryObservationEvent`。Spring Boot Micrometer bridge 只负责把这些事件转成 Micrometer 指标，不改变 cache、messaging、job 的 public API。

## 引入方式

```groovy
implementation project(':nexary-boot:nexary-observation-micrometer-spring-boot-starter')
```

真实发布后可通过 BOM 引入同名 artifact。这个 starter 可以和 cache、messaging、job starter 一起使用。

## 配置

```yaml
nexary:
  observation:
    micrometer:
      enabled: true
      counter-name: nexary.observation.events.total
      timer-name: nexary.observation.events.duration
```

默认 `enabled=true`。如果应用里没有 `MeterRegistry`，bridge 不创建 listener，行为等价 no-op。

## 指标名

- `nexary.observation.events.total`：事件总数 counter
- `nexary.observation.events.duration`：事件耗时 timer，来自 `NexaryObservationEvent.duration()`

可以通过配置改名，但建议保持默认，方便跨能力统一看板。

## Tag 白名单

bridge 只保留以下 tag：

- `category`
- `operation`
- `provider`
- `outcome`
- `tier`
- `status`
- `failure_category`
- `boundary`
- `trigger`
- `skip_reason`
- `shard_presence`
- `store`
- `retry_attempt_bucket`
- `terminal_status`
- `retry_phase`

Cache 历史事件里的 `failure` 会被映射为 `failure_category`。其它未列入白名单的 tag 会被丢弃。

禁止进入指标 tag：

- cache key、raw namespace、业务 id、payload
- message id、raw topic、raw consumer group
- execution id、job 参数
- lock token、owner token、fencing token
- exception message、stack trace
- 任意未清洗的用户输入

## Dashboard 建议

- 全局：按 `category`、`operation`、`outcome` 看事件量和耗时 p95/p99。
- Cache：按 `provider`、`tier`、`outcome` 看 Redis-only 与 tiered L1/L2 命中变化。
- Messaging：按 `provider`、`boundary`、`retry_attempt_bucket`、`terminal_status` 看发送、消费、重试和 dead-letter。
- Job：按 `provider`、`trigger`、`status`、`skip_reason`、`shard_presence` 看调度、执行和跳过原因。

## 非目标

- 不内置治理控制台。
- 不做 tracing、audit log 或安全审计。
- 不声明事件 exactly-once。
- 不把 Micrometer 类型放进 core/cache/messaging/job public API。
- 不把指标事件作为一致性机制。
