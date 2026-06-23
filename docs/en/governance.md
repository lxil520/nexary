# Governance

Governance adds local protection around Java calls: do not start work after the deadline, reject traffic that is too dense, send excess concurrent calls to fallback, and temporarily degrade a downstream path without rewriting business code. The v0.6 sample also shows a local circuit flow: normal calls, repeated failures, slow calls, open circuit, fallback, half-open probing, recovery, and reopening.

The boundary is deliberate: this is local SDK-level governance, not a console, sidecar, agent, remote config push, or global service-governance platform.

## Add Dependencies

For the Spring Boot 3.3 mainline:

```groovy
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation "com.aweimao:nexary-governance-spring-boot-starter"
implementation "com.aweimao:nexary-observation-micrometer-spring-boot-starter"
```

Run the sample:

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

## Configure Starter Policies

Use `application.yml` first for deadlines, rate limits, bulkheads, and explicit degradation:

```yaml
nexary:
  governance:
    runtime:
      enabled: true
    default-policy:
      max-requests-per-window: 100
      rate-limit-window: 1s
      max-concurrency: 64
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

`default-policy` is the fallback policy. Each entry under `resources` matches one stable resource. `kind` can be `http`, `downstream`, `cache`, `messaging`, `job`, `service`, or `custom`. Keep `name`, `provider`, and `operation` in a small fixed set; never build them from user ids, order ids, cache keys, or message ids.

Use `priorities` when low and high priority traffic need different behavior:

```yaml
nexary:
  governance:
    resources:
      profile-api:
        kind: http
        name: profile-api
        operation: get-profile
        max-requests-per-window: 10
        priorities:
          low:
            degraded: true
          high:
            max-requests-per-window: 100
```

## Use It at a Business Entry Point

```java
GovernanceContext context = GovernanceContext.builder()
        .resource(GovernanceResource.http("profile-api", "get-profile"))
        .trafficTag(TrafficTag.builder()
                .channel(TrafficTag.Channel.ONLINE)
                .priority(TrafficTag.Priority.NORMAL)
                .build())
        .deadline(Instant.now().plusMillis(300))
        .build();

return governanceRuntime.execute(
        context,
        () -> profileService.load(userId),
        () -> profileService.fallback(userId));
```

The deadline is also written to the older `DeadlineContext`, so existing cache, messaging, and job code can continue to read the same deadline.

## Run the Circuit Sample

The circuit flow is demonstrated by `LocalCircuitBreakerProfileGateway` in `nexary-sample-governance`. The class creates a local `GovernanceRuntime` with the same policy; `reset` only clears sample state so you can repeat the commands below. The `application.yml` snippet above is what starter-based applications should copy.

```bash
curl -X POST http://localhost:8080/governance/circuit/reset
```

Normal call:

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-1?mode=success"
```

You should see `circuitState=CLOSED` and `outcome=primary`.

Two failed calls:

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-2?mode=failure"
curl "http://localhost:8080/governance/circuit/profiles/u-3?mode=failure"
```

The second response has `circuitState=OPEN` and `outcome=failure_opened`. Call again while the circuit is open:

```bash
curl "http://localhost:8080/governance/circuit/profiles/u-4?mode=success"
```

The response has `source=fallback`, `outcome=fallback_open`, and `lastRejectionReason=CIRCUIT_OPEN`; the main action is not executed.

After the half-open window, send a successful probe:

```bash
sleep 0.2
curl "http://localhost:8080/governance/circuit/profiles/u-5?mode=success"
```

The response has `circuitState=CLOSED` and `outcome=half_open_recovered`. If the half-open probe fails, the result is `outcome=half_open_reopened` and the state returns to `OPEN`.

Slow calls can open the circuit too:

```bash
curl -X POST http://localhost:8080/governance/circuit/reset
curl "http://localhost:8080/governance/circuit/profiles/u-6?mode=slow"
curl "http://localhost:8080/governance/circuit/profiles/u-7?mode=slow"
```

The second response has `circuitState=OPEN`, `windowSlowCalls=2`, and `outcome=slow_opened`.

These fields are local in-process policy settings. They are not pushed from a remote console, and their windows are not shared across service instances.

## Inspect the Runtime Diagnostic Snapshot

The sample exposes a read-only endpoint for local circuit and rejection state:

```bash
curl -s http://localhost:8080/governance/circuit/state
```

Useful fields:

| Field | Meaning |
| --- | --- |
| `resourceKey` | Stable resource key; the sample uses a profile downstream call. |
| `priority` | Priority bucket used for local policy accounting. |
| `circuitState` | `CLOSED`, `OPEN`, or `HALF_OPEN`. |
| `windowCalls` / `windowFailures` / `windowSlowCalls` | Completed, failed, and slow calls in the sliding window. |
| `totalRejections` | Total local governance rejections seen in this JVM. |
| `lastRejectionReason` | Most recent rejection reason, for example `CIRCUIT_OPEN`, `RATE_LIMITED`, or `BULKHEAD_FULL`. |
| `activeConcurrency` / `maxConcurrency` | Current concurrency and configured limit. |
| `maxRequestsPerWindow` / `rateLimitWindow` | Rate-limit window settings. |
| `lastOutcome` | Most recent local governance result, commonly `SUCCESS`, `FAILURE`, or `REJECTED`. |

The snapshot is intentionally low-cardinality. It does not include user ids, order ids, message ids, cache keys, exception text, or stack traces.

## Messaging Publish Policy

When a service already uses the messaging starter, add a local policy for the publish resource:

```yaml
nexary:
  governance:
    resources:
      message-publish:
        kind: messaging
        name: message-publish
        operation: publish
        max-requests-per-window: 50
        rate-limit-window: 1s
        max-concurrency: 16
```

This policy only affects publish calls made by the current JVM. Check the result in the messaging sample: `result.status` from `POST /app-error-logs`, and `published[].publishStatus`, `published[].providerMessageId`, `published[].detail`, and `consumed[]` from `GET /app-error-logs`.

## Integrated Paths

| Path | Behavior |
| --- | --- |
| `GovernanceRuntime` | Checks deadline, rate limit, bulkhead, and explicit degradation before the action starts; publishes governance events when it rejects; runs fallback when provided, otherwise throws `GovernanceRejectedException`. |
| v0.6 circuit sample | `LocalCircuitBreakerProfileGateway` uses the local `GovernanceRuntime` to demonstrate `CLOSED`, `OPEN`, and `HALF_OPEN` transitions for failures, slow calls, rejection, fallback, recovery, and reopening. |
| Cache Redis mainline | The Spring Boot 3 Redis `CacheClient` Bean is wrapped by the governance runtime; resource name is `cache-client`, provider tag is `redis`, and operations include `cache.get`, `cache.put`, and `cache.batch_get`. |
| Messaging | publish / consume propagates the `nexary-deadline-epoch-millis` header; expired messages are rejected before the business handler; retry-stop and degradation publish governance events. |
| Job | local scheduler, XXL-JOB bridge, and PowerJob bridge support `start-deadline` and `max-concurrent-executions`; skipped runs record bounded skip reasons. |
| Observation | The Micrometer bridge keeps fixed governance tags and drops resource names, tenant, bizKey, exception text, and other high-cardinality data. |

## Policy Fields

| Property | Default | Meaning |
| --- | --- | --- |
| `deadline` | none | Maximum time allowed for this action. If the incoming context already has an earlier deadline, the earlier one wins. |
| `max-requests-per-window` | unlimited | Starts allowed in one rate-limit window. `0` or negative values mean unlimited. |
| `rate-limit-window` | `1s` | Rate-limit accounting window. |
| `max-concurrency` | unlimited | Concurrent calls allowed for the same resource and priority. |
| `degraded` | `false` | When `true`, runs fallback without executing the main action. |
| `circuit-breaker.enabled` | `false` | When `true`, records completed calls and evaluates the fields below. |
| `circuit-breaker.minimum-calls` | `10` | Minimum completed calls required before failure-rate and slow-call-rate checks can open the circuit. |
| `circuit-breaker.failure-rate-threshold` | `50` | Failure percentage that opens the circuit. |
| `circuit-breaker.slow-call-threshold` | `1s` | Completed-call duration after which a call is counted as slow. |
| `circuit-breaker.slow-call-rate-threshold` | `50` | Slow-call percentage that opens the circuit. |
| `circuit-breaker.open-state-duration` | `30s` | How long the circuit stays open before half-open probing. |
| `circuit-breaker.half-open-probe-calls` | `1` | Probe calls allowed to run at the same time while half-open. |
| `circuit-breaker.window` | `30s` | Circuit accounting window duration. |
| `circuit-breaker.sliding-window-size` | `100` | Maximum completed calls retained in the circuit window. |
| `circuit-breaker.consecutive-failure-threshold` | `0` | Consecutive failed calls that open the circuit; `0` disables this trigger. |

## Limits

- Deadline is a pre-start check and context propagation. It does not forcibly stop ordinary Java code that has already entered the business method.
- Circuit windows are local to the current JVM. Instances do not share failure counts, slow-call counts, or half-open probe results.
- Cache wrapping is claimed for the Spring Boot 3 Redis mainline. Boot2 / Boot4 cache entries should be expanded only after their samples and tests prove the same behavior.
- Messaging deadline headers apply to newly published messages. Older queued messages do not gain a deadline retroactively.
- Job `execution-timeout` still controls in-flight timeout. `start-deadline` only decides whether a trigger should start.
- There is no console, sidecar, agent, remote dynamic config, or policy push service here.

## Verify

```bash
./gradlew :nexary-boot:nexary-governance-spring-boot-starter:check
./gradlew :nexary-samples:nexary-sample-governance:check
./gradlew check
```
