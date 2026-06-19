# nexary-observation-micrometer-spring-boot-starter

Spring Boot module that maps Nexary observation events to Micrometer meters.

## Dependency

```groovy
implementation project(':nexary-boot:nexary-observation-micrometer-spring-boot-starter')
```

## Configuration

```yaml
nexary:
  observation:
    micrometer:
      enabled: true
      counter-name: nexary.observation.events.total
      timer-name: nexary.observation.events.duration
```

The module creates a `NexaryObservationListener` only when a `MeterRegistry` bean exists and the Micrometer integration is enabled. It also provides a default `NexaryObservationPublisher` that sends events to registered listeners.

## Metrics

- `nexary.observation.events.total`
- `nexary.observation.events.duration`

Allowed tags: `category`, `operation`, `provider`, `outcome`, `tier`, `status`, `failure_category`, `resource_kind`, `governance_action`, `traffic_channel`, `traffic_priority`, `boundary`, `trigger`, `skip_reason`, `shard_presence`, `store`, `retry_attempt_bucket`, `retry_decision`, `terminal_status`, `retry_phase`.

Do not use keys, ids, payloads, resource names, tenants, biz keys, lock tokens, exception messages, stack traces, or arbitrary user input as metric tags.
