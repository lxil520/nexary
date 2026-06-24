# nexary-sample-governance

This sample shows three paths:

- How the Spring Boot starter reads `application.yml` for deadlines, rate limits, bulkheads, and explicit degradation.
- How the local governance data plane connects `GovernanceContext`, `GovernanceRuntime`, fallback, and bounded diagnostic snapshots.
- How the local circuit flow behaves for one downstream call: normal calls, repeated failures, slow calls, open circuit, fallback, half-open probing, recovery, and reopening.
- How the read-only governance diagnostic Console reads current-JVM resources, windows, circuit state, and recent events.

The sample also adds `nexary-observation-micrometer-spring-boot-starter`. Tests register a local `SimpleMeterRegistry`, so rate-limit, degradation, and bulkhead events are recorded in `nexary.observation.events.total` and `nexary.observation.events.duration`.

This sample includes a local read-only page, but it is not a remote console, sidecar, agent, or multi-instance governance service. All windows, counters, and diagnostic fields come from the current JVM.

## Run

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

From another terminal:

```bash
curl http://localhost:8080/governance/profiles/u-1
curl http://localhost:8080/governance/profiles/u-2
curl http://localhost:8080/governance/profiles/u-3
```

The third request exceeds the `profile-api/get-profile` `2/min` policy and returns fallback.

Explicit degradation:

```bash
curl http://localhost:8080/governance/degraded/u-1
```

The response `source` is `fallback` because `inventory-service/reserve` has `degraded=true`.

## Read-Only Diagnostic Endpoints

The sample enables `nexary.governance.diagnostics.enabled=true`. Inspect local summary, resources, and recent events for the current process:

```bash
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
```

You can also open the read-only page:

```bash
open http://localhost:8080/nexary/console
```

To see circuit and rejection fields, open the circuit first and then inspect diagnostics:

```bash
curl -s -X POST http://localhost:8080/governance/circuit/reset
curl -s "http://localhost:8080/governance/circuit/profiles/u-2?mode=failure"
curl -s "http://localhost:8080/governance/circuit/profiles/u-3?mode=failure"
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
```

Useful fields:

| Field | Meaning |
| --- | --- |
| `resourceKey` | Stable resource key. |
| `kind` / `name` / `provider` / `operation` | Resource catalog fields. |
| `priority` | Priority bucket used for local accounting. |
| `policySnapshot` | Policy last applied to this resource. |
| `runtimeSnapshot` | Window, circuit, and rejection state after the latest local run. |
| `circuitState` | `CLOSED`, `OPEN`, or `HALF_OPEN`. |
| `windowCalls` / `windowFailures` / `windowSlowCalls` | Completed, failed, and slow calls in the current sliding window. |
| `totalRejections` | Local governance rejections in this JVM. |
| `lastRejectionReason` | Most recent rejection reason, such as `CIRCUIT_OPEN`, `RATE_LIMITED`, or `CONCURRENCY_LIMITED`. |
| `lastOutcome` | Most recent result, such as `SUCCESS`, `FAILURE`, or `REJECTED`. |
| `action` | Recent event action, such as `EXECUTE`, `REJECT`, or `FALLBACK`. |
| `durationBucket` | Coarse duration bucket. |

These endpoints are read-only and disabled by default; the starter registers them only when `nexary.governance.diagnostics.enabled=true`.

## Circuit Flow

Clear the local sample state first:

```bash
curl -X POST http://localhost:8080/governance/circuit/reset
```

Normal call:

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-1?mode=success"
```

The response has `circuitState=CLOSED` and `outcome=primary`.

Two failed calls open the circuit:

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-2?mode=failure"
curl "http://localhost:8080/governance/circuit/profiles/u-3?mode=failure"
```

The second response has `circuitState=OPEN` and `outcome=failure_opened`. Call again while open:

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-4?mode=success"
```

The main path is not called. The response has `source=fallback`, `outcome=fallback_open`, and `lastRejectionReason=CIRCUIT_OPEN`.

After the half-open window, send a successful probe:

```bash
sleep 0.2
curl "http://localhost:8080/governance/circuit/profiles/u-5?mode=success"
```

The response has `circuitState=CLOSED` and `outcome=half_open_recovered`. If the half-open probe fails, the response has `outcome=half_open_reopened` and the circuit returns to `OPEN`.

Slow calls can open the circuit too:

```bash
curl -X POST http://localhost:8080/governance/circuit/reset
curl "http://localhost:8080/governance/circuit/profiles/u-6?mode=slow"
curl "http://localhost:8080/governance/circuit/profiles/u-7?mode=slow"
```

The second response has `circuitState=OPEN`, `windowSlowCalls=2`, and `outcome=slow_opened`.

## Configuration to Copy

The `application.yml` settings are suitable for a Spring Boot 3.3 mainline project:

```yaml
nexary:
  governance:
    runtime:
      enabled: true
    diagnostics:
      enabled: true
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
      profile-service:
        kind: downstream
        name: profile-service
        provider: nexary
        operation: load-profile
        circuit-breaker:
          enabled: true
          window: 5s
          minimum-calls: 2
          failure-rate-threshold: 100
          slow-call-threshold: 100ms
          slow-call-rate-threshold: 100
          half-open-probe-calls: 1
          open-state-duration: 150ms
          sliding-window-size: 8
          consecutive-failure-threshold: 2
```

The circuit flow is demonstrated by `LocalCircuitBreakerProfileGateway` through the local `GovernanceRuntime`. It creates the same policy in code, and `reset` only clears sample state so the curl commands can be repeated. The YAML above is what starter-based applications should copy.

## Code to Copy

- `GovernanceSampleConfiguration`: stable resource names, never user ids, order ids, cache keys, or message ids.
- `GovernanceSampleController`: how a business entry point creates `GovernanceContext`, and how the circuit demo endpoints are exposed.
- `LocalCircuitBreakerProfileGateway`: uses the local governance runtime to demonstrate `CLOSED`, `OPEN`, and `HALF_OPEN`.
- `ProfileQueryService`: main logic, slow call, failed call, and fallback are kept separate.

Keep resource names stable, such as `profile-api/get-profile`. Micrometer meters keep only bounded tags such as `resource_kind`, `governance_action`, `traffic_channel`, `traffic_priority`, and `outcome`.

## What This Does Not Include

- No remote console, sidecar, or agent.
- No remote policy push.
- No cross-instance sync for circuit, rate-limit, bulkhead, or diagnostic state.
