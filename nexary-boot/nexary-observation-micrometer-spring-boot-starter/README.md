# nexary-observation-micrometer-spring-boot-starter

把 Nexary observation events 接到 Micrometer 的 Spring Boot 模块。

## 引入方式

```groovy
implementation project(':nexary-boot:nexary-observation-micrometer-spring-boot-starter')
```

## 配置

```yaml
nexary:
  observation:
    micrometer:
      enabled: true
      counter-name: nexary.observation.events.total
      timer-name: nexary.observation.events.duration
```

只有应用存在 `MeterRegistry` bean 且 Micrometer 接入启用时，模块才会创建 `NexaryObservationListener`。模块还会提供默认 `NexaryObservationPublisher`，把事件发送给已注册的 listener。

## 指标

- `nexary.observation.events.total`
- `nexary.observation.events.duration`

允许的 tags：`category`、`operation`、`provider`、`outcome`、`tier`、`status`、`failure_category`、`resource_kind`、`governance_action`、`traffic_channel`、`traffic_priority`、`boundary`、`trigger`、`skip_reason`、`shard_presence`、`store`、`retry_attempt_bucket`、`retry_decision`、`terminal_status`、`retry_phase`。

不要把 key、id、payload、resource name、tenant、bizKey、lock token、异常消息、stack trace 或任意用户输入作为 metric tags。
