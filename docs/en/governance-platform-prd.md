# v0.20 Governance Platform RC PRD

v0.20 is no longer closed as "one more Platform page." It delivers a unified governance platform RC. The local Console diagnostics remain, but the default experience is now for operations and platform governance: Overview, Topology, Request Flows, Incidents, Services, Hosts, Middleware, Resource Governance, Integrations, Notifications, and Policy Plans.

The platform does not replace SkyWalking, CAT, Sentinel, Spring Cloud Gateway, Prometheus, Spring Boot Admin, Feishu, or DingTalk. It aggregates cross-tool evidence, request analysis, incident diagnosis, and governance planning so operators do not have to inspect several middleware consoles manually every day.

## Goals

1. Show what is broken, who is affected, and what to check first within 10 seconds.
2. Connect services, rooms, middleware, host watermarks, request flows, and external evidence.
3. Provide rule-based diagnostic conclusions while staying read-only and dry-run first.
4. Leave stable model and UI entry points for later Prometheus, SkyWalking, CAT, Sentinel, Gateway, SBA, and enterprise chat connectors.

## Cloud-Phone Reference Scenario

The v0.20 demo must cover a cloud-phone topology instead of abstract service rows:

| Layer | Objects |
| --- | --- |
| Cloud business | open-api, sdk-api, scheduler, consumer, admin-console, user-platform |
| Cloud middleware | redis-main, postgres-main, oss-main |
| Room business | room-resource, signaling, board-service |
| Room middleware | redis-room, oss-room |
| Typical anomalies | Redis timeout, Redis pipeline error, board-service disconnect, cross-room jitter, HTTP failure, host swap / IO watermark |

Demo data must use aliases such as `room-a-signaling-01`, `redis-room-a-primary`, and `board-service-room-a`. Do not write real private IPs, business ids, cache keys, payloads, tokens, passwords, full exception text, or full stack traces.

## v0.20 Screens

| Screen | Question | v0.20 RC Acceptance |
| --- | --- | --- |
| Overview | Where is today's risk. | Incident queue, topology summary, diagnostic conclusion, and service / zone / middleware watermarks are visible together. |
| Topology | Which dependency edge is unhealthy. | Services, middleware, and zones are layered; red dashed edges mean failure or timeout, amber means slow call, purple means jitter or packet loss. |
| Request Flows | Where did one request slow down or fail. | Left sample list, center span tree and waterfall, CAT-style transaction stats, and evidence references. |
| Incidents | Why did it alert, and where should we start. | Evidence package with metrics, request sample, transaction stats, Sentinel/Gateway signal, host watermark, and external refs. |
| Services | Is one service healthy. | QPS, error rate, P95/P99, instances, downstream dependencies, policy summary, and external refs. |
| Hosts | Are hosts or instances unhealthy. | Matrix for CPU, memory, swap, disk IO, threads, GC, network jitter, packet loss, and latest anomaly. |
| Middleware | Is Redis/PG/MQ/OSS dragging the service. | Watermark, connections, slow query or timeout, callers, and related incidents. |
| Resource Governance | What did Nexary local governance observe. | Keep resources, events, and fault traces as read-only diagnostics, but not as the main path. |
| Integrations | Can external-tool data be trusted. | SkyWalking, CAT/logs, Prometheus, Sentinel, Gateway, SBA, Feishu, and DingTalk state and freshness. |
| Notifications | How do exceptions reach chat without flooding. | Dry-run, test delivery, routing, deduplication, mute, escalation, and recovery rules. |
| Policy Plans | What would change before a rule changes. | Diff, dry-run, copy, export review; no Apply/Save/Delete production-write buttons. |

## Backend Model

v0.20 adds read-only models under `org.nexary.governance.platform` without changing cache, messaging, or job user APIs:

| Model | Meaning |
| --- | --- |
| `GovernanceRequestFlow` | One request-flow sample with traceKey, entry service, endpoint, status, duration, span count, primary error, and external refs. |
| `GovernanceSpan` | Span tree node with parent/span id, service, resource, middleware component, duration, status, error type, and source refs. |
| `GovernanceTransactionMetric` | CAT-style transaction stats with total, failure, failureRate, qps/tps, min/max/avg, p95/p99, and sample traceKey. |
| `GovernanceHostSignal` | Host or instance watermark with CPU, memory, swap, disk IO, network jitter, packet loss, connections, JVM, and thread state. |
| `GovernanceEvidenceRef` | External evidence reference limited to SkyWalking trace, CAT transaction, PromQL, log query, Sentinel resource, Gateway route, SBA instance, or Nexary fault trace. |

## Read-Only APIs

v0.20 RC must provide at least these endpoints:

- `GET /api/platform/snapshot`
- `GET /api/platform/request-flows`
- `GET /api/platform/request-flows/{traceKey}`
- `GET /api/platform/transactions`
- `GET /api/platform/hosts`
- `GET /api/platform/incidents/{incidentKey}`

All responses must use sanitized low-cardinality fields. External evidence carries references only, not payloads, full exception text, or full stack traces.

## Incident Signals

v0.20 incident grouping must recognize:

- `REDIS_TIMEOUT`
- `REDIS_PIPELINE_ERROR`
- `BROKEN_PIPE`
- `DEPENDENCY_TIMEOUT`
- `NETWORK_JITTER`
- `PACKET_LOSS`
- `HOST_WATERMARK`

These signals must become evidence packages, not simple service counters. Each package should drill down to request flows, transaction stats, host watermarks, and external references.

## Non-Goals

- Do not connect real production credentials.
- Do not add a real LLM analysis assistant.
- Do not write production Sentinel, Gateway, Feishu, or DingTalk configuration.
- Do not automatically drain traffic, quarantine instances, change rules, or scale services.
- Do not describe Nexary as a replacement for APM, monitoring, rate limiting, circuit breaking, or chat tools.

## v0.20 RC Acceptance

- `http://127.0.0.1:18090/nexary/console` opens the new governance overview by default.
- Primary navigation is Overview, Topology, Request Flows, Incidents, Services, Hosts, Middleware, Resource Governance, Integrations, Notifications, and Policy Plans.
- The shared top area stays under 56px and pages do not use large hero sections.
- At 1440px desktop width, Overview shows incidents, topology, diagnostic conclusion, and watermarks together.
- Request Flows is clearly stronger than the old Trace page, with samples, span waterfall, transaction stats, and evidence references.
- Demo incidents cover Redis timeout, pipeline error, Broken pipe, cross-room jitter, and host watermark anomalies.
- Frontend `npm run build`, backend platform tests, and Docker 18090 smoke pass.

After these checks pass, `v0.20.0` can be released as the governance platform RC. Its boundary is product framing, read-only models, demo topology, and troubleshooting entry points. The demo UI is not the final operations experience.

## v0.21 Handoff Boundary

v0.21 should not keep polishing static demo UI. It should connect real read-only metrics and watermarks first:

- Micrometer / Prometheus-style metrics: QPS, error rate, P95/P99, and HTTP success rate.
- Host and JVM watermarks: CPU, memory, GC, threads, and connection pools.
- Middleware watermarks: Redis, PG, MQ, and OSS connections, slow calls, timeouts, and errors.
- Docker environment with real components and sample services, so v0.20 screens and models can be tested against real read-only data.
- Still no writes to Sentinel, Gateway, Feishu, DingTalk, or production configuration; policy and notification paths remain dry-run.
