# Observation 与 Micrometer

Nexary 的 cache、messaging、job 和 governance 模块通过 `NexaryObservationEvent` 发出事件。Spring Boot Micrometer 接入模块只负责把这些事件映射成 Micrometer 指标，不改变业务 API。

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

默认 `enabled=true`。如果应用里没有 `MeterRegistry`，Micrometer listener 不会创建；应用仍可按需提供自己的 `NexaryObservationPublisher`。

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
- `resource_kind`
- `governance_action`
- `traffic_channel`
- `traffic_priority`
- `boundary`
- `trigger`
- `skip_reason`
- `shard_presence`
- `store`
- `retry_attempt_bucket`
- `retry_decision`
- `terminal_status`
- `retry_phase`

Cache 历史事件里的 `failure` 会被映射为 `failure_category`。其它未列入白名单的 tag 会被丢弃。

禁止进入指标 tag：

- cache key、raw namespace、业务 id、payload
- message id、raw topic、raw consumer group
- execution id、job 参数
- governance resource name、tenant、bizKey、用户 id、订单号
- lock token、owner token、fencing token
- exception message、stack trace
- 任意未清洗的用户输入

## Dashboard 建议

- 全局：按 `category`、`operation`、`outcome` 看事件量和耗时 p95/p99。
- Cache：按 `provider`、`tier`、`outcome` 看单层缓存与 L1/L2 命中变化。
- Messaging：按 `provider`、`boundary`、`retry_attempt_bucket`、`terminal_status` 看发送、消费、重试和 dead-letter。
- Job：按 `provider`、`trigger`、`status`、`skip_reason`、`shard_presence` 看调度、执行和跳过原因。
- Governance：按 `resource_kind`、`governance_action`、`traffic_channel`、`traffic_priority`、`retry_decision` 看限流、并发隔离、deadline、降级和停止重试事件。

## 怎么验证字段

先跑治理样例：

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
curl -s http://localhost:8080/governance/circuit/state
```

返回里看 `circuitState`、`windowCalls`、`windowFailures`、`windowSlowCalls`、`totalRejections`、`lastRejectionReason`、`activeConcurrency`、`maxConcurrency`、`lastOutcome`。这些字段对应治理事件里的低数量 tag，例如 `category=governance`、`resource_kind`、`governance_action`、`traffic_channel`、`traffic_priority`。

再跑 messaging 样例：

```bash
./gradlew :nexary-samples:nexary-sample-messaging:run
curl -s -X POST http://localhost:8082/app-error-logs \
  -H 'Content-Type: application/json' \
  -d '{"appId":"billing","messageId":"m-1001","level":"ERROR","message":"payment timeout"}'
curl -s http://localhost:8082/app-error-logs
```

返回里看 `result.status`、`published[].publishStatus`、`published[].providerMessageId`、`published[].detail`、`consumed[]`。指标 tag 不会包含 `messageId`、payload、raw topic、异常全文或堆栈。

## 非目标

- 不做 tracing、audit log 或安全审计。
- 不声明事件 exactly-once。
- 不把 Micrometer 类型放进 core/cache/messaging/job public API。
- 不把指标事件作为一致性机制。
