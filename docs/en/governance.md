# Governance

Governance adds local protection around Java calls: do not start work after the deadline, reject traffic that is too dense, send excess concurrent calls to fallback, and temporarily degrade a downstream path without rewriting business code. The verified path now has two execution engines. The default local engine handles deadline, rate limit, bulkhead, explicit degradation, and circuit decisions inside the current JVM. The Boot3 Sentinel provider can execute flow control, thread-count isolation, slow-call circuit breaking, and exception circuit breaking through Sentinel. Both paths reuse Nexary resources, policy snapshots, low-cardinality diagnostics, and the read-only Console.

The boundary is deliberate: the local governance runtime does not provide a sidecar, agent, remote config push, or cross-instance state sync. The governance platform aggregates resources, signals, topology, incident candidates, and review material. In v0.23, the only writes are Nexary-local plans, notification-test results, and audit records. It does not modify Sentinel, Gateway, APM, registry, or production notification-channel configuration.

The `0.23.0` line does not replace Sentinel Dashboard, Spring Cloud Gateway, SkyWalking, CAT, Prometheus, enterprise IM, automatic traffic-drain platforms, or a distributed trace backend. It handles two layers: the local governance runtime still handles request cancellation, the Sentinel provider, retry-stop, priority isolation, abnormal instance candidates, and local fault traces; the governance platform aggregates services, clusters, zones, middleware dependencies, request-flow samples, transaction stats, host watermarks, and low-cardinality signals into read-only topology, incident evidence packages, governance plans, dry-run results, notification previews, and audit records. v0.18 groups slow calls, error-rate signals, Sentinel blocks, Gateway disconnects, retry-stop signals, and abnormal instance signals into incident candidates. v0.19 turns Platform Mode into an operations workbench draft. v0.20 makes the default Console entry a full governance platform RC with Overview, Topology, Request Flows, Incidents, Services, Hosts, Middleware, Resource Governance, Integrations, Notifications, and Policy Plans. v0.21 moves the sample into a real Docker verification environment with Redis, Postgres, RabbitMQ, Prometheus, and SkyWalking. v0.22 completes the trusted read-only platform data source, freshness, trace query, service, host, and integration views. v0.23 generates review material and test notifications only; it does not push production configuration. The v0.11 cancellation check still runs before Sentinel entry, so canceled requests do not pollute Sentinel windows. v0.15 instance health records only real downstream results and does not count Sentinel blocks as instance failures. v0.16 traces store only low-cardinality fields and do not store business parameters. Platform signals also reject user ids, tenants, payloads, cache keys, message ids, exception text, stack traces, tokens, and passwords.

## Add Dependencies

For the Spring Boot 3.3 mainline:

```groovy
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation "com.aweimao:nexary-governance-spring-boot-starter"
implementation "com.aweimao:nexary-console-spring-boot-starter"
implementation "com.aweimao:nexary-observation-micrometer-spring-boot-starter"
```

For the Boot3 Sentinel provider, add:

```groovy
implementation "com.aweimao:nexary-governance-sentinel-spring-boot-starter"
```

If the application is a Spring Cloud Gateway entry, add:

```groovy
implementation "com.aweimao:nexary-governance-gateway-spring-boot-starter"
```

Run the sample:

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

To inspect the read-only governance platform view:

```bash
./gradlew :nexary-samples:nexary-sample-governance-platform:bootRun
curl -s http://localhost:18092/api/platform/topology
curl -s http://localhost:18092/api/platform/incidents
curl -s http://localhost:18092/api/platform/request-flows
open http://localhost:18092/nexary/console
```

## v0.17 Read-Only Governance Platform Foundation

The platform foundation contains three modules:

- `nexary-governance-platform-api`: assets, dependencies, connectors, signals, topology, and incident candidate models.
- `nexary-governance-platform-server`: `POST /api/platform/resources`, `POST /api/platform/signals`, `GET /api/platform/topology`, `GET /api/platform/services`, and `GET /api/platform/incidents`.
- `nexary-governance-platform-storage-jdbc`: an explicit JDBC repository for teams that want persistent platform evidence. It supports PostgreSQL, MySQL/MariaDB, and SQLite dialects. The demo still uses in-memory storage by default.
- `nexary-governance-platform-storage-postgres`: a compatibility artifact that pins the JDBC repository to PostgreSQL.

The first Platform Mode is read-only. It shows services, dependencies, incident candidates, and connector status. It does not edit policies, write Sentinel rules, change Gateway routes, or send production alerts.

## v0.18 Incident Evidence Chain

`0.18.0` builds the incident evidence chain on top of the v0.17 platform foundation:

- `IncidentCandidate` now carries `startedAt`, `primaryResourceKey`, `evidenceCount`, and `impactedResourceCount`.
- `EvidenceItem` now carries service, cluster, zone, duration bucket, reference type, and reference key.
- `/api/platform/incidents/{incidentKey}` returns one incident candidate.
- Dependency edges expose warning and critical counts so Console can show which dependency has evidence.
- `nexary-sample-governance-platform` now seeds open-api and room-resource incident candidates for local verification.

`referenceType` and `referenceKey` are low-cardinality references only. They explain whether the evidence came from a metric query, Sentinel resource, Gateway route, instance health, or fault trace. v0.18 does not connect to a real SkyWalking, Prometheus, Sentinel Dashboard, or Gateway management API; those read-only connectors remain in the v0.20 roadmap slot.

## v0.19 Console Product Redesign

`0.19.0` does not add a new governance execution feature. It turns Platform Mode from a developer debugging page into a daily operations page:

- The first screen shows current health, incident count, affected services, service count, dependency count, and integration status.
- The left column is an incident queue sorted by severity and evidence count, with severity filters and search across service, cluster, zone, and resource.
- The center column shows topology impact and a service health table so users can identify the dependency and service group to inspect first.
- The right column shows the selected incident's impact scope, primary resource, suggested check, and evidence chain.
- Narrow screens use incident, topology, and service tabs so tables and evidence panels do not collide.

This release remains read-only: it does not write policies, change Sentinel / Gateway / APM configuration, or send production alerts.

## v0.20 Governance Platform RC

v0.20 does not keep patching the v0.19 page. It makes the governance platform the default Console entry and redefines the asset model, data sources, screen structure, and acceptance. See [Governance Platform RC PRD](governance-platform-prd.md) and [Governance Platform RC UI/UX Design](governance-platform-design.md).

This release keeps the v0.17 to v0.19 read-only resources, signals, topology, and incident-candidate model, then expands it with request-flow samples, CAT-style transaction stats, host signal matrix, service watermarks, zone watermarks, middleware dependencies, external references, policy-plan dry-run, and notification-channel dry-run. Nexary still does not replace SkyWalking, CAT, Prometheus, Sentinel, or Gateway. The platform connects their key evidence into one on-call view.

## v0.21 Real Middleware Probe Starting Point

v0.21 starts moving the governance platform sample from static demo data to a local verifiable chain. Run the complete Docker environment:

```bash
./scripts/console/up.sh
./scripts/console/smoke.sh
open http://127.0.0.1:18090/nexary/console
```

This environment starts the Console, Redis, Postgres, RabbitMQ, Prometheus, and SkyWalking. `POST /demo/platform/probe?iterations=50` performs real local read/write probes against Redis and Postgres, runs a RabbitMQ publish/consume probe, records low-cardinality platform signals, and exposes Prometheus text metrics at `/demo/platform/prometheus`, including `nexary_demo_probe_calls_total`, `nexary_demo_probe_latency_seconds`, JVM memory, and JVM thread gauges. The Console sample JVM sends local request traces to the Docker OAP through the SkyWalking Java agent. The UI is available at `http://127.0.0.1:18097`.

This release remains read-only. It does not use production credentials, write Sentinel or Gateway configuration, or send real notifications. SkyWalking is used only for local sample tracing; Nexary is not becoming its own agent product.

## v0.22 Trusted Read-Only Governance Platform

v0.22 moves the Console from a demo workbench to a trusted read-only platform. Every main screen shows data source and freshness. Evidence from SkyWalking, Prometheus, Sentinel, Gateway, Actuator, and alert systems is mapped into Nexary platform models before it reaches the frontend; raw external tool structures are not exposed directly.

The request-flow page supports time, service, endpoint, status, duration, resource, source, sorting, and pagination. Topology opens on risk paths by default, and nodes represent only external entries, real services, instance groups, or middleware. Service, host, incident, and integration pages are organized around read-only evidence, and connector failures degrade only the matching data instead of affecting the business SDK.

## v0.23 Controlled Governance Review

v0.23 generates `GovernanceReviewPlan` from v0.22 evidence and adds dry-run, diff preview, review export, notification preview, test-send result, and local audit records. A plan target can be a Sentinel resource, Gateway route, instance candidate, alert threshold, or ownership mapping, but the result only describes suggested before/after values and impact scope.

These APIs write only Nexary-local storage: plans, notification-route metadata, test results, and audit records. They do not call external systems to write configuration, drain traffic, scale capacity, or send production alerts. Notification testing is disabled by default; until test configuration is enabled explicitly, the Console can only preview the message and explain why test sending was rejected.

## v0.12 Sentinel Provider

The Sentinel provider is optional. The local engine remains the default; Sentinel is enabled only when `provider: sentinel` is configured explicitly:

```yaml
nexary:
  governance:
    provider: sentinel
    sentinel:
      enabled: true
      transport:
        enabled: false
    diagnostics:
      enabled: true
  console:
    enabled: true
```

Policy fields map to Sentinel rules:

| Nexary policy | Sentinel rule |
| --- | --- |
| `max-requests-per-window` + `rate-limit-window` | QPS flow rule |
| `max-concurrency` | thread-count flow rule |
| `failure-rate-threshold` / `consecutive-failure-threshold` | exception degrade rule |
| `slow-call-threshold` / `slow-call-rate-threshold` / `minimum-calls` | slow-call degrade rule |
| `degraded=true` | Nexary fallback semantics, not a fake Sentinel block |

Run the sample:

```bash
./gradlew :nexary-samples:nexary-sample-governance-sentinel:run
```

From another terminal, trigger Sentinel rate limiting and inspect diagnostics:

```bash
curl -s http://localhost:8080/governance/sentinel/rate
curl -s http://localhost:8080/governance/sentinel/rate
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
NEXARY_GOVERNANCE_SENTINEL_BASE_URL=http://localhost:8080 ./scripts/governance-sentinel/smoke.sh
```

## v0.14 Traffic Isolation and Priority

When online requests and batch work share the same resource, configure a default resource policy and a low-priority override:

```yaml
nexary:
  governance:
    resources:
      shared-downstream:
        kind: downstream
        name: priority-shared-service
        provider: nexary
        operation: load
        max-requests-per-window: 100
        rate-limit-window: 1s
        max-concurrency: 32
        priorities:
          low:
            max-requests-per-window: 1
            rate-limit-window: 1m
            max-concurrency: 1
```

Business code binds only fixed traffic class and priority. It does not pass user ids, tenants, order ids, or arbitrary business keys:

```java
GovernanceContext context = GovernanceContext.builder()
        .resource(GovernanceResource.downstream("priority-shared-service", "load"))
        .trafficTag(TrafficTag.builder()
                .channel(TrafficTag.Channel.BATCH)
                .priority(TrafficTag.Priority.LOW)
                .build())
        .build();
```

After starting the Sentinel sample, trigger the path directly:

```bash
curl -s http://localhost:8080/governance/sentinel/priority/online
curl -s http://localhost:8080/governance/sentinel/priority/batch
curl -s http://localhost:8080/governance/sentinel/priority/batch
curl -s http://localhost:8080/governance/sentinel/priority/online
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/events
NEXARY_GOVERNANCE_PRIORITY_BASE_URL=http://localhost:8080 ./scripts/governance-priority/smoke.sh
```

Important fields:

- `trafficClass`: `ONLINE`, `OFFLINE`, `BATCH`, or `BACKGROUND`.
- `priority`: `HIGH`, `NORMAL`, or `LOW`.
- `isolationReason`: `PRIORITY_RATE_LIMITED`, `PRIORITY_BULKHEAD_FULL`, `PRIORITY_DEGRADED`, `PRIORITY_CIRCUIT_OPEN`, or `MIXED_TRAFFIC`.
- `isolatedCount`: priority isolation events in the current JVM.

## v0.16 Local Fault Traces

Trace diagnostics are disabled by default. After they are enabled, Nexary keeps recent traces only inside the current JVM to answer why a call stopped and which resource should be inspected first:

```yaml
nexary:
  governance:
    diagnostics:
      enabled: true
    trace:
      enabled: true
      max-traces: 128
      max-events-per-trace: 32
      ttl: 10m
      expose-external-trace-id: false
```

This is not Jaeger, Zipkin, SkyWalking, or an OpenTelemetry exporter. A `traceKey` is a local diagnostics / Console lookup key and is not used as a metric tag. HTTP JSON and the Console do not expose payloads, URL queries, user ids, tenants, message ids, cache keys, exception text, or stack traces.

Run the sample:

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--spring.profiles.active=trace'
```

From another terminal, trigger success, deadline, retry-stop, priority isolation, and instance-health scenarios:

```bash
curl -s http://localhost:8080/governance/trace/priority?priority=high
curl -s http://localhost:8080/governance/trace/deadline/trace-deadline
curl -s http://localhost:8080/governance/trace/retry-stop
curl -s http://localhost:8080/governance/trace/priority?priority=low
curl -s http://localhost:8080/governance/trace/priority?priority=low
curl -s -X POST http://localhost:8080/governance/trace/instance-health
curl -s http://localhost:8080/nexary/governance/traces
curl -s http://localhost:8080/nexary/governance/faults/summary
NEXARY_GOVERNANCE_TRACE_BASE_URL=http://localhost:8080 ./scripts/governance-trace/smoke.sh
```

Important fields:

- `terminalOutcome`: final trace result, such as `SUCCESS`, `REJECTED`, or `CANCELLED`.
- `primaryStopReason`: `DEADLINE_EXPIRED`, `CANCELLED`, `RETRY_STOPPED`, `BLOCKED`, `REJECTED`, `ISOLATED`, `INSTANCE_QUARANTINE_CANDIDATE`, or `FAILURE`.
- `suggestedResourceKey`: resource that the trace suggests inspecting first.
- `steps[].stage`: `REQUEST`, `GOVERNANCE`, `DOWNSTREAM`, `CACHE`, `MESSAGING`, `JOB`, `INSTANCE_HEALTH`, or `RETRY`.
- `faultTraceCount` / `stoppedTraceCount`: retained trace count and stopped trace count in the current JVM.

The Console Overview shows fault trace counts. Resources show the latest trace result. Events can be filtered by trace stage / stop reason. Trace detail shows a read-only step timeline. The Console still has no policy editor, quarantine button, or remote config push.

## v0.15 Abnormal Instance Detection and Local Quarantine Model

Instance health detection is disabled by default. After it is enabled, Nexary records downstream instance results only inside the current JVM and exposes read-only diagnostics:

```yaml
nexary:
  governance:
    diagnostics:
      enabled: true
    instance-health:
      enabled: true
      window: 60s
      minimum-calls: 20
      suspect-windows: 2
      recovery-windows: 2
      slow-call-threshold: 2s
      slow-ratio-threshold: 0.60
      failure-ratio-threshold: 0.50
      timeout-ratio-threshold: 0.30
      skew-factor-threshold: 3.0
      expose-raw-instance-key: false
```

It does not remove instances automatically and does not call a registry, Spring Cloud LoadBalancer, Gateway route API, cloud vendor API, or PaaS API. `instanceKey` should be a stable alias or a masked fingerprint. Diagnostics do not write URLs, queries, user ids, tenants, order ids, payloads, exception text, or stack traces.

Run the sample:

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--spring.profiles.active=instance-health'
```

From another terminal, trigger three simulated instances:

```bash
curl -s -X POST http://localhost:8080/governance/instance-health/scenario
curl -s http://localhost:8080/nexary/governance/instance-health
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/events
NEXARY_GOVERNANCE_INSTANCE_HEALTH_BASE_URL=http://localhost:8080 ./scripts/governance-instance-health/smoke.sh
```

Important fields:

- `instanceHealthState`: `HEALTHY`, `SUSPECT`, `QUARANTINE_CANDIDATE`, or `RECOVERING`.
- `quarantineReason`: `SERVER_ERROR_RATIO`, `SLOW_RATIO`, `READ_TIMEOUT_SPIKE`, `CONNECT_TIMEOUT_SPIKE`, `RESET_SPIKE`, or `STATUS_CODE_SKEW`.
- `recoveryAdvice`: `BACKOFF`, `QUARANTINE_CANDIDATE`, `MANUAL_ACTION_REQUIRED`, or `RECOVERY_PROBE`.
- `instanceSuspectCount`: abnormal instance candidates in the current JVM.
- `quarantineCandidateCount`: quarantine candidates in the current JVM.

The Console Overview shows suspect / quarantine candidate / recovery probe counts. Resource detail shows the instance health table. Events show `INSTANCE_SUSPECT`, `QUARANTINE_CANDIDATE`, `RECOVERY_PROBE`, and `INSTANCE_RECOVERED`. These pages stay read-only; there is no quarantine button or policy editor.

## v0.13 Retry Stop Propagation

Retry stop propagation uses fixed enum values and does not write business keys, message ids, cache keys, payloads, or exception text into events:

- `DEADLINE_EXPIRED`
- `CANCELLED`
- `CLIENT_DISCONNECTED`
- `UPSTREAM_CANCELLED`
- `SHUTDOWN`
- `RATE_LIMITED`
- `BULKHEAD_FULL`
- `CIRCUIT_OPEN`
- `DEGRADED`
- `RETRY_EXHAUSTED`
- `TIMEOUT`
- `REJECTED`
- `UNKNOWN`

Trigger the Sentinel sample retry-stop path:

```bash
curl -s http://localhost:8080/governance/sentinel/retry-stop
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/events
```

Important fields:

- `retryStoppedCount`: retry-stop events in the current JVM.
- `retryStopReason`: retry-stop reason on recent events.
- `lastRetryStopReason`: most recent retry-stop reason on a resource snapshot.

Messaging consumption and job execution stop their current retry loop after governance rejection, expired deadlines, request cancellation, or timeout. Normal business exceptions still follow their own retry policy until attempts are exhausted.

The added diagnostic fields stay low-cardinality:

| Field | Meaning |
| --- | --- |
| `engine` | `LOCAL` or `SENTINEL`. |
| `blockReason` | Sentinel block reason for a recent event, such as `RATE_LIMITED`, `BULKHEAD_FULL`, or `CIRCUIT_OPEN`. |
| `lastBlockReason` | Most recent Sentinel block reason on a resource snapshot. |
| `blockedCount` | Sentinel blocks seen in the current JVM. |
| `sentinelResourceCount` | Resources executed by Sentinel in the current JVM. |

Sentinel transport is disabled by default and Sentinel Dashboard is not required. The dashboard server is used only when `nexary.governance.sentinel.transport.enabled=true` and a dashboard server is configured. The current support matrix claims only the Spring Boot 3.3 mainline Sentinel provider; Boot2 / Boot4 Sentinel starters should not be documented until their independent samples and gates pass.

## Configure Starter Policies

Use `application.yml` first for deadlines, rate limits, bulkheads, and explicit degradation:

```yaml
nexary:
  governance:
    runtime:
      enabled: true
    diagnostics:
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
  console:
    enabled: true
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

`nexary.governance.diagnostics.enabled` is `false` by default. Enable it only when local diagnostics are needed; the endpoints are GET-only and cannot change policies.

## v0.11 Request Cancellation for Stale Work

v0.11 is about stale request cancellation, not a circuit-breaker platform replacement. It has two layers:

- An already-expired deadline should not start the primary action.
- A request already canceled, timed out, or judged stale upstream should stop before it enters a downstream call.
- If the request has already entered a business loop, code can check `CancellationContext.cancelled()` and return quickly.
- Gateway propagates deadline and cancellation id downstream; when the client disconnects, Gateway calls downstream `POST /nexary/governance/cancellations` to cancel the token in the current JVM.
- The stop result should be visible in local diagnostics and observation events while keeping fields low-cardinality.
- Fallback runs only when business code provides one and its semantics are valid; otherwise the result is a local governance rejection.

Downstream services do not expose the cancellation receiver by default. Enable it explicitly:

```yaml
nexary:
  governance:
    cancellation:
      receiver:
        enabled: true
```

After the downstream sample is running, check direct cancellation diagnostics:

```bash
NEXARY_GOVERNANCE_CANCELLATION_BASE_URL=http://localhost:28091 \
  ./scripts/governance-cancellation/smoke.sh
```

Run the Gateway sample and downstream sample together to verify client-disconnect notification:

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--server.port=28091'
NEXARY_GOVERNANCE_DOWNSTREAM_URI=http://127.0.0.1:28091 \
  ./gradlew :nexary-samples:nexary-sample-governance-gateway:run
curl --max-time 1 'http://localhost:28090/gateway/governance/cancellation/slow/u-1?durationMillis=5000' || true
curl -s http://localhost:28091/nexary/governance/summary
curl -s http://localhost:28091/nexary/governance/events
```

Acceptance should verify cancellation, rejection, fallback, and event recording inside the current JVM only. Do not document v0.11 as a Sentinel replacement, cross-instance state sync, remote policy push, or thread kill; code that has already entered an ordinary Java method only supports cooperative stop checks.

## v0.8 Local Governance Data

v0.8 describes the governance path in four layers:

| Layer | Role | What to do |
| --- | --- | --- |
| `GovernanceContext` | Describes the stable resource, traffic tag, priority, and deadline for one call. | Build it at the business entry point; do not put user ids, order ids, cache keys, or message ids in resource names. |
| `GovernanceRuntime` | Applies deadline, rate limit, bulkhead, degradation, circuit decisions, and fallback in the current JVM. | Pass the primary action and fallback to `execute(...)` instead of duplicating governance branches in business code. |
| `GovernanceResourceDescriptor` | Lists configured or already-used resources, priorities, and policy snapshots. | Use it to confirm resource name, provider, operation, and policy binding. |
| `GovernanceRuntimeSnapshot` / `GovernanceRuntimeEvent` | Exposes low-cardinality fields for this process. | Use it for local debugging only; do not treat it as a remote console or multi-instance view. |

The starter auto-configures `GovernancePolicyRegistry`, `GovernanceRuntime`, and `GovernanceExecution`. If the application provides one of those beans, the starter backs off.

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

## Inspect the Read-Only Diagnostic Endpoints

The sample enables `nexary.governance.diagnostics.enabled=true` in `application.yml`, so you can call the starter's read-only endpoints directly. Start the sample first:

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

From another terminal, trigger a few calls:

```bash
curl -s http://localhost:8080/governance/profiles/u-1
curl -s http://localhost:8080/governance/profiles/u-2
curl -s http://localhost:8080/governance/profiles/u-3
```

Inspect summary, resources, and recent events:

```bash
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
```

Open the circuit and inspect diagnostics again:

```bash
curl -s -X POST http://localhost:8080/governance/circuit/reset
curl -s "http://localhost:8080/governance/circuit/profiles/u-1?mode=failure"
curl -s "http://localhost:8080/governance/circuit/profiles/u-2?mode=failure"
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
```

Useful fields:

| Field | Meaning |
| --- | --- |
| `resourceKey` | Stable resource key; the sample uses a profile downstream call. |
| `kind` / `name` / `provider` / `operation` | Resource catalog fields that show which resource owns the policy. |
| `priority` | Priority bucket used for local policy accounting. |
| `policySnapshot` | Policy snapshot last applied to this resource. |
| `runtimeSnapshot` | Window, circuit, and rejection state after the latest local run. |
| `circuitState` | `CLOSED`, `OPEN`, or `HALF_OPEN`. |
| `windowCalls` / `windowFailures` / `windowSlowCalls` | Completed, failed, and slow calls in the sliding window. |
| `totalRejections` | Total local governance rejections seen in this JVM. |
| `lastRejectionReason` | Most recent rejection reason, for example `CIRCUIT_OPEN`, `RATE_LIMITED`, or `CONCURRENCY_LIMITED`. |
| `activeConcurrency` / `maxConcurrency` | Current concurrency and configured limit. |
| `maxRequestsPerWindow` / `rateLimitWindow` | Rate-limit window settings. |
| `lastOutcome` | Most recent local governance result, commonly `SUCCESS`, `FAILURE`, or `REJECTED`. |
| `action` | Recent event action, commonly `EXECUTE`, `REJECT`, or `FALLBACK`. |
| `durationBucket` | Coarse duration bucket; exact business latency is not exposed. |
| `engine` | Whether the resource or event ran through `LOCAL` or `SENTINEL`. |
| `blockReason` / `lastBlockReason` | Sentinel block reason as a fixed enum; Sentinel origin and business identifiers are not exposed. |
| `blockedCount` / `sentinelResourceCount` | Sentinel block count and Sentinel resource count in the current JVM. |

The diagnostics are intentionally low-cardinality. They do not include user ids, order ids, message ids, cache keys, payloads, exception text, or stack traces. The endpoints are read-only, and the starter does not expose these HTTP paths unless explicitly enabled.

## Open the Read-Only Console

If the application also adds `nexary-console-spring-boot-starter` and sets `nexary.console.enabled=true`, open:

```bash
open http://localhost:8080/nexary/console
```

The Console reads the GET-only API under `/nexary/console/api`. It shows summary, resources, resource detail, events, and read-only settings for the current JVM. It does not write policies, push configuration, or aggregate multiple instances.

To inspect the governance page first, run the Docker sample:

```bash
./scripts/console/up.sh
./scripts/console/smoke.sh
open http://127.0.0.1:18090/nexary/console
```

This container runs `nexary-sample-governance`; it is not a separate control service. The page and diagnostics APIs read local governance state from the same sample process.

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
| v0.11 request cancellation for stale work | When a deadline has expired, upstream has canceled, or the client has disconnected, cancel or reject before business work starts; running work stops cooperatively through `CancellationContext`, with local diagnostics and observation events. |
| v0.12 Sentinel provider | The Spring Boot 3.3 mainline can choose `provider=sentinel`, letting Sentinel execute QPS flow control, thread-count isolation, slow-call circuit breaking, and exception circuit breaking while Nexary keeps the stable Java API, fallback, low-cardinality diagnostics, and Console. |
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
- v0.11 cancellation covers pre-start cancellation and cooperative stop checks while business code is running. It does not promise forced interruption of running business threads, and it does not replace application-server or client connection cancellation.
- Nexary does not replace Sentinel Dashboard, cluster flow control, or remote rule platforms; the current support matrix claims only the Boot3 mainline Sentinel provider.
- Boot2 / Boot4 Sentinel provider support is not in the support matrix yet. Do not copy the Boot3 starter into Boot2 / Boot4 applications before their independent samples and gates pass.
- Circuit windows are local to the current JVM. Instances do not share failure counts, slow-call counts, or half-open probe results.
- Cache wrapping is claimed for the Spring Boot 3 Redis mainline. Boot2 / Boot4 cache entries should be expanded only after their samples and tests prove the same behavior.
- v0.10 continues to include only the local read-only page and local diagnostics hardening. It does not include a remote console, sidecar, agent, remote dynamic configuration, or cross-instance state sync.
- Messaging deadline headers apply to newly published messages. Older queued messages do not gain a deadline retroactively.
- Job `execution-timeout` still controls in-flight timeout. `start-deadline` only decides whether a trigger should start.
- There is no remote console, sidecar, agent, remote dynamic config, or policy push service here.

## Verify

```bash
./gradlew :nexary-boot:nexary-governance-spring-boot-starter:check
./gradlew :nexary-governance:nexary-governance-sentinel:check
./gradlew :nexary-boot:nexary-governance-sentinel-spring-boot-starter:check
./gradlew :nexary-samples:nexary-sample-governance:check
./gradlew :nexary-samples:nexary-sample-governance-sentinel:check
./scripts/governance-sentinel/smoke.sh
./gradlew check
```
