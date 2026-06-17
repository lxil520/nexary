# Observation and Micrometer

Nexary capabilities emit provider-neutral `NexaryObservationEvent` events. The Spring Boot Micrometer bridge only maps those events to Micrometer meters and does not change cache, messaging, or job public APIs.

## Dependency

```groovy
implementation project(':nexary-boot:nexary-observation-micrometer-spring-boot-starter')
```

After publication, use the same artifact through the Nexary BOM. This starter can be used together with cache, messaging, and job starters.

## Configuration

```yaml
nexary:
  observation:
    micrometer:
      enabled: true
      counter-name: nexary.observation.events.total
      timer-name: nexary.observation.events.duration
```

`enabled=true` is the default. If the application has no `MeterRegistry`, the bridge creates no listener and behaves as a no-op.

## Metric Names

- `nexary.observation.events.total`: event counter
- `nexary.observation.events.duration`: event duration timer from `NexaryObservationEvent.duration()`

The names are configurable, but the defaults are recommended for cross-capability dashboards.

## Tag Whitelist

The bridge keeps only these tags:

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

The cache legacy `failure` tag is mapped to `failure_category`. Tags outside the whitelist are dropped.

Never use these values as metric tags:

- cache keys, raw namespaces, business ids, payloads
- message ids, raw topics, raw consumer groups
- execution ids, job parameters
- lock tokens, owner tokens, fencing tokens
- exception messages, stack traces
- arbitrary unsanitized user input

## Dashboard Suggestions

- Global: chart event volume and p95/p99 duration by `category`, `operation`, and `outcome`.
- Cache: chart Redis-only and tiered L1/L2 behavior by `provider`, `tier`, and `outcome`.
- Messaging: chart publish, consume, retry, and dead-letter paths by `provider`, `boundary`, `retry_attempt_bucket`, and `terminal_status`.
- Job: chart scheduling, execution, and skip behavior by `provider`, `trigger`, `status`, `skip_reason`, and `shard_presence`.

## Non-Goals

- No built-in governance console.
- No tracing, audit log, or security audit implementation.
- No exactly-once event claim.
- No Micrometer types in core/cache/messaging/job public APIs.
- Observation events are not consistency mechanisms.
