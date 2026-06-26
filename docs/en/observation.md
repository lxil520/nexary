# Observation and Micrometer

Nexary cache, messaging, job, and governance modules emit `NexaryObservationEvent` events. The Spring Boot Micrometer integration maps those events to Micrometer meters and does not change application APIs.

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

`enabled=true` is the default. If the application has no `MeterRegistry`, the Micrometer listener is not created; applications can still provide their own `NexaryObservationPublisher` when needed.

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

The cache legacy `failure` tag is mapped to `failure_category`. Tags outside the whitelist are dropped.

Never use these values as metric tags:

- cache keys, raw namespaces, business ids, payloads
- message ids, raw topics, raw consumer groups
- execution ids, job parameters
- governance resource names, tenants, biz keys, user ids, order ids
- trace keys, external trace ids
- lock tokens, owner tokens, fencing tokens
- exception messages, stack traces
- arbitrary unsanitized user input

## Dashboard Suggestions

- Global: chart event volume and p95/p99 duration by `category`, `operation`, and `outcome`.
- Cache: chart single-layer and L1/L2 behavior by `provider`, `tier`, and `outcome`.
- Messaging: chart publish, consume, retry, and dead-letter paths by `provider`, `boundary`, `retry_attempt_bucket`, and `terminal_status`.
- Job: chart scheduling, execution, and skip behavior by `provider`, `trigger`, `status`, `skip_reason`, and `shard_presence`.
- Governance: chart rate limit, bulkhead, deadline, degradation, and retry-stop events by `resource_kind`, `governance_action`, `traffic_channel`, `traffic_priority`, and `retry_decision`.

## Verify the Fields

Run the governance sample first:

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
curl -s http://localhost:8080/governance/circuit/state
```

Inspect `circuitState`, `windowCalls`, `windowFailures`, `windowSlowCalls`, `totalRejections`, `lastRejectionReason`, `activeConcurrency`, `maxConcurrency`, and `lastOutcome`. These map to bounded governance tags such as `category=governance`, `resource_kind`, `governance_action`, `traffic_channel`, and `traffic_priority`.

Then run the messaging sample:

```bash
./gradlew :nexary-samples:nexary-sample-messaging:run
curl -s -X POST http://localhost:8082/app-error-logs \
  -H 'Content-Type: application/json' \
  -d '{"appId":"billing","messageId":"m-1001","level":"ERROR","message":"payment timeout"}'
curl -s http://localhost:8082/app-error-logs
```

Inspect `result.status`, `published[].publishStatus`, `published[].providerMessageId`, `published[].detail`, and `consumed[]`. Metric tags must not include `messageId`, payload, raw topic, exception text, or stack traces.

## v0.16 Fault Traces and Metrics

`/nexary/governance/traces` and the Console Trace detail page are local diagnostics views. They are not Micrometer metrics and they are not external trace exporters. A `traceKey` is used only to look up one local trace and is not added to `nexary.observation.events.total` or `nexary.observation.events.duration` tags.

To verify the trace view, run:

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--spring.profiles.active=trace'
NEXARY_GOVERNANCE_TRACE_BASE_URL=http://localhost:8080 ./scripts/governance-trace/smoke.sh
```

Metrics answer how many events happened, how long they took, and how they split across fixed tags. Traces answer why one local call stopped and which resource should be inspected first. They use different outputs.

## Non-Goals

- No Jaeger, Zipkin, SkyWalking, or OpenTelemetry exporter.
- No audit log or security audit implementation.
- No exactly-once event claim.
- No Micrometer types in core/cache/messaging/job public APIs.
- Observation events are not consistency mechanisms.
