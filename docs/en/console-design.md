# v0.9 Read-Only Governance Diagnostic Console Design Draft

This document defines the page, component, and frontend constraints for the v0.9 read-only governance diagnostic Console. It supports local Spring Boot troubleshooting: after the application starts, a developer opens `/nexary/console` to inspect governance resources, policy snapshots, window counters, circuit state, rejection reasons, and recent events for the current JVM.

## Design Brief

| Item | Decision |
| --- | --- |
| Product goal | Help Java developers using Nexary governance answer: which resources are protected, which resource is rejecting calls, why it rejected, and what happened recently. |
| Data source | v0.8 read-only diagnostic data: `summary`, `resources`, `resources/{id}`, and `events`. The frontend does not invent business state. |
| Visual direction | Dense operations troubleshooting UI, closer to Spring Boot Admin, Kafka UI, and RocketMQ Dashboard scanning patterns than to a marketing site, with restrained Nexary-owned styling. |
| Interaction level | Page navigation, filtering, sorting, summary expansion, and copying a resource key. No policy edits, no saved configuration, and no write requests. |
| Device priority | Desktop first, designed around 1280px to 1440px widths. Mobile only needs readable content and vertical scrolling. |

## Non-Goals

- No policy editing, config push, remote control, login, permissions, audit backend, cross-instance aggregation, or automatic blocking.
- No user ids, tenant ids, business keys, message ids, cache keys, payloads, full exception messages, or stack traces.
- Do not describe the Console as a production control plane. It only reads diagnostic fields exposed by the current application.

## Information Architecture

The Console uses left navigation plus a main content area. The top bar keeps the current application, sample time, refresh action, and read-only marker visible. Navigation is fixed:

| Page | Main Question | Default Route |
| --- | --- | --- |
| Overview | Does the current service have an obvious governance risk. | `/nexary/console` |
| Resources | Which resources are failing, rejecting, or circuit-open. | `/nexary/console/resources` |
| Resource Detail | What policy, window state, and recent events belong to one resource. | `/nexary/console/resources/{resourceKey}` |
| Events | What governance actions happened recently, and why were calls rejected. | `/nexary/console/events` |
| Settings Readonly | How the Console and diagnostic endpoints are enabled, and what remains read-only. | `/nexary/console/settings` |

## Visual Rules

| Item | Rule |
| --- | --- |
| Density | Default table row height is 36px to 40px. Metric cards contain one primary number and one short label. |
| Color | Use white and light gray backgrounds with dark gray text. Reserve state colors for badges, thin borders, and small alerts. |
| Typography | Use the system sans-serif stack. Use tabular numbers for easier horizontal comparison. |
| Spacing | Page padding 24px, section gap 16px, table cell horizontal padding 12px. |
| Radius | Cards, badges, and inputs max out at 6px radius. Avoid large rounded marketing cards. |
| Graphics | First version needs no illustrations, background images, or decorative gradients. Troubleshooting data matters more. |

Recommended state colors:

| State | Usage | Suggested Colors |
| --- | --- | --- |
| `CLOSED` / healthy | Green text on light green background, showing that the resource is passing calls. | `#047857` / `#ecfdf5` |
| `OPEN` / circuit open | Red text on light red background, showing that the primary action is blocked. | `#b91c1c` / `#fef2f2` |
| `HALF_OPEN` | Amber text on light amber background, showing probe mode. | `#b45309` / `#fffbeb` |
| `REJECTED` | Red or orange, based on rejection reason severity. | `#c2410c` / `#fff7ed` |
| Not configured or no data | Gray, to avoid false alarms. | `#475569` / `#f8fafc` |

## Component Specs

### Status Badge

Status badges represent `circuitState`, `lastOutcome`, `lastRejectionReason`, and event `action`. A badge only displays fixed enum values and must not concatenate dynamic business text. Long values use 12px monospaced or tabular styling, a max width of 140px, ellipsis overflow, and a tooltip with the full enum.

Suggested severity:

| Value | Severity |
| --- | --- |
| `OPEN`, `CIRCUIT_OPEN`, `FAILURE` | High |
| `RATE_LIMITED`, `CONCURRENCY_LIMITED`, `REJECTED` | Medium |
| `HALF_OPEN`, `FALLBACK` | Attention |
| `CLOSED`, `SUCCESS`, `EXECUTE` | Healthy |

### Resource Table

The resource table is the primary control on the Resources page. Default columns:

| Column | Meaning |
| --- | --- |
| Resource | `kind`, `name`, and `operation`, with `provider` on the second line. |
| State | `circuitState` and `lastOutcome`. |
| Window | `windowCalls`, `windowFailures`, and `windowSlowCalls`. |
| Rejections | `totalRejections` and `lastRejectionReason`. |
| Concurrency | `activeConcurrency` / `maxConcurrency`. |
| Last Seen | Latest event time or snapshot time. |

Default sort order: `OPEN`, `HALF_OPEN`, recently rejected, recently failed, then all other resources. The resource name opens Resource Detail. Do not add a bulk action column.

### Metric Card

Metric cards appear at the top of Overview. Each card answers one question:

| Card | Field |
| --- | --- |
| Resources | Total resource count. |
| Open Circuits | Count of resources with `circuitState=OPEN`. |
| Recent Rejections | Recent-window rejection count or rejection delta. |
| Recent Failures | Recent-window failure count. |

Each card shows a primary number, a short label, and one small trend or note. No-data states show `0`; do not leave blank skeletons.

### Event List

The event list is used on the right side of Overview and as the main table on Events. Default columns:

| Column | Meaning |
| --- | --- |
| Time | `timestamp`, formatted in local time by the frontend. |
| Resource | `resourceKey` or resource name. |
| Action | `EXECUTE`, `REJECT`, or `FALLBACK`. |
| Outcome | `SUCCESS`, `FAILURE`, or `REJECTED`. |
| Reason | `rejectionReason`; show `-` when absent. |
| Duration | `durationBucket`; do not show exact business latency. |

Events only need local filtering in the first version. Default order is newest first.

### Policy Summary

Policy Summary appears on Resource Detail. It renders `policySnapshot` as read-only key-value blocks:

- deadline, rate limit, max concurrency, and degraded.
- circuit breaker enabled state, window, minimum calls, failure-rate threshold, slow-call threshold, open duration, and half-open probe calls.
- priority overrides in a separate collapsible block when present.

Keep configuration units visible, such as `300ms`, `1s`, and `30s`. Missing values render as `not set` or `unlimited`; do not infer business meaning.

### Filters

Filters sit at the top of Resources and Events:

| Control | Type |
| --- | --- |
| Kind | Segmented control: All / Cache / Messaging / Job / Service / HTTP / Downstream / Custom. |
| State | Dropdown: All / Closed / Half Open / Open / Rejected. |
| Reason | Dropdown: All / Circuit Open / Rate Limited / Concurrency Limited / Deadline / Degraded. |
| Search | Text input matching only `resourceKey`, `name`, `operation`, and `provider`. |
| Refresh | Icon button with a 5s disabled state after click to avoid accidental refresh storms. |

Filters may remain in frontend memory. If routing sync is added, only persist low-cardinality fields.

### Empty State

Use two empty-state variants:

| Scenario | Copy Direction |
| --- | --- |
| Console is available but has no resources | Explain that the current JVM has no governance resource snapshot yet. Suggest running the sample request or business path first. |
| No result after filtering | Explain that no item matches the filters. Provide a clear-filters button. |

No illustration is needed. Use one short title, no more than two lines of body text, and one secondary button.

### Error State

Differentiate errors:

| Error | Handling |
| --- | --- |
| API 404 | Explain that the Console API may be disabled or mounted at a different path. |
| API 403 / 401 | Explain that the application security configuration blocked the read-only endpoint. |
| API 500 | Show request failure and a retry button. Do not show backend exception text. |
| Network timeout | Suggest checking whether the local application is still running. |

Error details only show bounded fields such as HTTP status, path, and error category. Do not render tracing identifiers or stack traces.

## Page Prototype Notes

### Overview

First screen from top to bottom:

1. Top bar: application name, read-only badge, last refresh time, refresh button.
2. Four metric cards: Resources, Open Circuits, Recent Rejections, Recent Failures.
3. Two-column content: risk resource table on the left, limited to 8 rows; recent events on the right.
4. Footer note: the Console reads current-JVM diagnostic data and does not represent cross-instance state.

Overview should help a developer decide within 10 seconds whether to open a resource detail page.

### Resources

Resources is the main troubleshooting page:

1. Top filters.
2. Resource table, with risky resources first by default.
3. Row click opens Resource Detail.
4. Above the table, show matched count and last refresh time.

Do not turn Resources into a wall of cards. A table is better for comparing windows, rejections, and states.

### Resource Detail

Resource Detail has three sections:

1. Header: resource name, kind, provider, operation, status badge, copy resource key.
2. Two summary columns: Runtime Snapshot on the left, Policy Summary on the right.
3. Recent event table filtered to the resource.

The page must make "why was it rejected" visible: `lastRejectionReason`, `circuitState`, `windowFailures`, `windowSlowCalls`, and policy thresholds should be visible in the same screen.

### Events

Events supports time-based inspection of recent governance actions:

1. Top filters for action, outcome, reason, kind, and search.
2. Event table, newest first by default.
3. Row expansion for low-cardinality fields such as `resourceKey`, `trafficPriority`, and `durationBucket`.

Event detail must not display payloads, full exception messages, or stack traces.

### Settings Readonly

Settings Readonly only explains the current read-only configuration and boundary:

1. Console path: `/nexary/console`.
2. API path: `/nexary/console/api`.
3. Recent event buffer limit.
4. Diagnostic data source: current-JVM governance snapshot.
5. Read-only boundary: no policy edits, no config push, no instance aggregation.
6. Enablement hint: show `nexary.console.enabled=true` and the diagnostic endpoint enablement requirement.

Do not render a full `application.yml` on Settings, because sensitive configuration could be copied into the browser.

## Data Field Constraints

The frontend may rely only on low-cardinality fields:

| Category | Allowed Fields |
| --- | --- |
| Resource | `resourceKey`, `kind`, `name`, `provider`, `operation`, `priority` |
| Policy | deadline, rate limit, max concurrency, degraded, circuit breaker thresholds |
| Runtime | `circuitState`, `windowCalls`, `windowFailures`, `windowSlowCalls`, `totalRejections`, `lastOutcome`, `lastRejectionReason` |
| Event | `timestamp`, `action`, `outcome`, `rejectionReason`, `durationBucket` |

If the backend returns unknown fields, the first version ignores them. Do not auto-render unknown fields into a table, because that can leak high-cardinality business data.

## Frontend Implementation Notes

- Use Vue 3 + Vite + TypeScript. Hash routing is acceptable so the built assets can be served from Spring Boot static resources.
- The API client sends GET requests only. Do not reserve write endpoints such as `POST /policy` or `PUT /settings`.
- Every API surface needs loading, empty, error, and success states.
- Keep table and filter state in frontend memory. Events does not require a server-side query language.
- Refresh should not clear current filters. On refresh failure, keep the last successful data and show a compact error message.
- Number formatting must be stable: unknown is `-`, unlimited is `unlimited`, and durations keep their original units.
- Use local component CSS or light global variables. Do not add a heavy UI framework as a first-version dependency.
- Under 768px, collapse left navigation into the top area and make tables horizontally scrollable. Do not reshape the page into marketing cards.
- Playwright verification should cover non-empty rendering and no overlapping UI for Overview, Resources, Resource Detail, Events, and Settings Readonly, plus a visible error state.

## Acceptance Checklist

- Overview renders stable states for no resources, healthy resources, and an open circuit resource.
- Resources sorts `OPEN`, `HALF_OPEN`, and recently rejected resources first by default.
- Resource Detail shows policy summary, window counters, and recent events together.
- Events can filter by kind, state, reason, and search text.
- Settings Readonly states the read-only boundary and provides no policy editing entry.
- No page displays high-cardinality business fields, payloads, full exception messages, or stack traces.
