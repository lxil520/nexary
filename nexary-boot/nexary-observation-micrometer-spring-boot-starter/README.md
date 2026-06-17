# nexary-observation-micrometer-spring-boot-starter

Nexary observation events 的 Spring Boot Micrometer bridge。

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

只有应用存在 `MeterRegistry` bean 且 bridge 启用时，模块才会创建 `NexaryObservationListener`。

## 指标

- `nexary.observation.events.total`
- `nexary.observation.events.duration`

允许的 tags：`category`、`operation`、`provider`、`outcome`、`tier`、`status`、`failure_category`、`boundary`、`trigger`、`skip_reason`、`shard_presence`、`store`、`retry_attempt_bucket`、`terminal_status`、`retry_phase`。

不要把 key、id、payload、lock token、异常消息、stack trace 或任意用户输入作为 metric tags。
