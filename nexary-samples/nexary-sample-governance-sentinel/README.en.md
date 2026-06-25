# Nexary Sentinel Governance Sample

This sample shows how the Spring Boot 3.3 mainline lets Nexary governance resources execute through Sentinel. Business code still calls `GovernanceRuntime` and does not use Sentinel APIs directly. Sentinel handles QPS flow control, thread-count isolation, slow-call circuit breaking, and exception circuit breaking; Nexary keeps fallback, low-cardinality diagnostics, and the read-only Console.

## Run the Sample

```bash
./gradlew :nexary-samples:nexary-sample-governance-sentinel:run
```

The sample enables:

- `nexary.governance.provider=sentinel`
- `nexary.governance.diagnostics.enabled=true`
- `nexary.console.enabled=true`
- Sentinel transport is disabled, so Sentinel Dashboard is not required

## Trigger Governance Behavior

QPS flow control:

```bash
curl -s http://localhost:8080/governance/sentinel/rate
curl -s http://localhost:8080/governance/sentinel/rate
```

Thread-count isolation:

```bash
curl -s "http://localhost:8080/governance/sentinel/bulkhead?holdMillis=1000" &
curl -s "http://localhost:8080/governance/sentinel/bulkhead?holdMillis=1000"
wait
```

Slow-call circuit breaking:

```bash
curl -s "http://localhost:8080/governance/sentinel/slow?durationMillis=150"
curl -s "http://localhost:8080/governance/sentinel/slow?durationMillis=150"
curl -s "http://localhost:8080/governance/sentinel/slow?durationMillis=25"
```

Exception circuit breaking:

```bash
curl -s http://localhost:8080/governance/sentinel/failure || true
curl -s http://localhost:8080/governance/sentinel/failure || true
curl -s http://localhost:8080/governance/sentinel/failure || true
```

Explicit degradation:

```bash
curl -s http://localhost:8080/governance/sentinel/fallback
```

Retry stop propagation:

```bash
curl -s http://localhost:8080/governance/sentinel/retry-stop
```

## Inspect Diagnostics and Console

```bash
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
open http://localhost:8080/nexary/console
```

You can also run the smoke script:

```bash
NEXARY_GOVERNANCE_SENTINEL_BASE_URL=http://localhost:8080 ./scripts/governance-sentinel/smoke.sh
```

Important fields:

- `engine`: `SENTINEL`
- `blockReason`: `RATE_LIMITED`, `BULKHEAD_FULL`, or `CIRCUIT_OPEN`
- `lastBlockReason`: most recent Sentinel block reason on the resource snapshot
- `retryStopReason`: retry-stop reason on recent events
- `lastRetryStopReason`: most recent retry-stop reason on the resource snapshot
- `blockedCount`: Sentinel blocks in the current JVM
- `retryStoppedCount`: retry-stop events in the current JVM
- `sentinelResourceCount`: Sentinel resources in the current JVM

Diagnostics and Console do not expose Sentinel origin, cancellation id, user id, tenant, order id, cache key, message id, payload, exception text, or stack traces.

## Limits

- This sample only claims the Spring Boot 3.3 / Java 17+ mainline.
- It does not replace Sentinel Dashboard, cluster flow control, or remote rule platforms.
- Sentinel transport is disabled by default. It connects to an existing Sentinel Dashboard only when a dashboard server is configured explicitly.
- Boot2 / Boot4 Sentinel provider entries should not be added to the README support matrix until their own samples and gates pass.
