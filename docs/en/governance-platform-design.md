# v0.20 Governance Platform RC UI/UX Design

The v0.20 UI target is an operations workbench that on-call users can actually use, not another local Console polish pass. The interface should feel like an operations tool: dense, low decoration, and built around tables, matrices, topology, and waterfalls. State colors are reserved for risk, connection, policy, and notification states.

## Information Architecture

Primary left navigation is fixed:

1. Overview
2. Topology
3. Request Flows
4. Incidents
5. Services
6. Hosts
7. Middleware
8. Resource Governance
9. Integrations
10. Notifications
11. Policy Plans

Local JVM diagnostics remain under Resource Governance or the settings boundary. The old `Trace` label is replaced by Request Flows.

## Shared Top Area

The shared top area should stay under 56px and only contain global filters and refresh state:

- Workspace
- Environment
- Team
- Zone
- Time range
- Severity
- Refresh
- Language

Pages should not use large hero sections. Each page is a compact title / toolbar plus its main work area.

## Overview

Overview answers "what is broken, who is affected, and what should we inspect first".

Recommended layout:

- Left: incident queue, with the most severe incident selected by default.
- Center: topology summary showing unhealthy dependency edges.
- Right: diagnostic conclusion with impact, primary resource, evidence count, and suggested check.
- Bottom: service watermarks, zone watermarks, middleware watermarks, and host anomaly summary.

At 1440px width, all four areas must be visible together.

## Topology

Topology answers "how are services, middleware, and rooms connected, and which edge is unhealthy".

Nodes:

- Service: service name, team, cluster, zone, and instance count.
- Middleware: Redis, PG, MQ, OSS, watermark, and connection state.
- Host/Instance: shown in detail or matrix only, not all at once on the main canvas.

Edges:

- Green solid: healthy.
- Amber dashed: slow call or P95/P99 increase.
- Red dashed: failure, timeout, or connection failure.
- Purple dashed: network jitter or packet loss.

Edges show QPS, error rate, P95/P99, and latest evidence type. Clicking an edge opens source, target, resource, error count, and external refs.

## Request Flows

Request Flows is a core v0.20 screen and must be clearly stronger than the old Trace page.

Layout:

- Left: request sample list, filterable by traceKey, endpoint, service, zone, duration, and failure type.
- Center: span tree plus duration waterfall. Each span shows service, component, operation, duration, and errorType.
- Right: CAT-style transaction stats and evidence references.

Transaction fields:

- total
- failure
- failureRate
- qps/tps
- min/max/avg
- p95/p99
- sampleTraceKey

External references show low-cardinality keys only, such as SkyWalking trace, CAT transaction, PromQL, log query, Sentinel resource, Gateway route, SBA instance, or Nexary fault trace.

## Incidents

Incidents are evidence packages, not event rows.

Incident detail contains:

- Title, severity, affected service, cluster, and zone.
- Primary evidence and primary resource.
- Timeline: metric anomaly, request sample, transaction stats, Sentinel/Gateway signal, host watermark, and external refs.
- Suggested check: service, route, resource, middleware, or host.
- Notification dry-run state.

v0.20 does not provide acknowledgement, mute, or recovery write buttons. It displays state and plans only.

## Services

Services shows a service list and detail:

- QPS
- Error rate
- P95/P99
- Instance count
- Downstream dependencies
- Policy summary
- External tool refs

The service detail should answer whether the issue comes from the service itself, a downstream dependency, middleware, host, or entry layer.

## Hosts

Hosts uses a matrix, not large cards:

| Column | Meaning |
| --- | --- |
| Host | Sanitized instance or host alias |
| CPU | CPU usage |
| Mem | Memory usage |
| Swap | Swap usage |
| Disk IO | Disk IO watermark |
| Jitter | Network jitter |
| Loss | Packet loss |
| Threads | JVM thread count |
| Last | Latest anomaly |

Redis swap / IO anomalies, board-service reachability problems, and cross-room jitter must be visible in this matrix.

## Middleware

Middleware groups Redis, PG, MQ, and OSS:

- Watermark
- Connection count
- Slow query or timeout
- Calling services
- Related incidents
- Recent request-flow samples

Redis timeout and pipeline error must drill down to request flows and incident evidence.

## Integrations

Integrations shows external-tool trust:

- SkyWalking
- CAT / logs
- Prometheus
- Sentinel
- Gateway
- Spring Boot Admin
- Feishu
- DingTalk

Each connector shows state, last success, data freshness, permission hint, read-only / dry-run / write-disabled.

## Notifications

Notifications only supports dry-run and test delivery:

- route rule
- dedup rule
- mute window
- escalation rule
- recovery rule
- test delivery

Production delivery is disabled by default to avoid flooding a chat group when a service jitters.

## Policy Plans

Policy Plans only shows plans and diffs:

- Generate plan
- Run dry-run
- Copy change
- Export review

Do not show Save, Apply, Delete, or any button that writes directly to production systems.

## Visual Acceptance

- Card radius does not exceed 6px.
- Table rows stay between 36px and 44px.
- At 1440px width, Overview shows incidents, topology, diagnosis, and watermarks together.
- Request Flows shows samples, waterfall, transaction stats, and evidence refs together.
- Topology communicates failures, slow calls, jitter, and packet loss through edge color and line style.
- Copy does not position Nexary as a replacement for SkyWalking, CAT, Prometheus, Sentinel, or Gateway.
