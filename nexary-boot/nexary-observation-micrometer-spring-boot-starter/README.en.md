# nexary-observation-micrometer-spring-boot-starter

Spring Boot Micrometer bridge for Nexary observation events.

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

The bridge creates a `NexaryObservationListener` only when a `MeterRegistry` bean exists and the bridge is enabled.

## Metrics

- `nexary.observation.events.total`
- `nexary.observation.events.duration`

Allowed tags: `category`, `operation`, `provider`, `outcome`, `tier`, `status`, `failure_category`, `boundary`, `trigger`, `skip_reason`, `shard_presence`, `store`, `retry_attempt_bucket`, `terminal_status`, `retry_phase`.

Do not use keys, ids, payloads, lock tokens, exception messages, stack traces, or arbitrary user input as metric tags.
