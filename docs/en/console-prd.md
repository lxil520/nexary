# v0.9 Read-Only Governance Diagnostic Console PRD

This document defines the v0.9 read-only governance diagnostic Console. It runs inside the user's own Spring Boot application and helps inspect governance resources, policy snapshots, window counters, circuit state, rejection reasons, and recent events for the current JVM. It is not a remote policy console, and it does not modify configuration or aggregate multiple instances.

## Users

| User | Job To Be Done |
| --- | --- |
| Java developer | Confirm that resource names, policies, and actual call outcomes match after integrating governance locally. |
| Integration debugger | Check why a resource was rejected, whether its circuit is open, and what the latest outcome was. |
| Local operations verifier | Reproduce rate limit, bulkhead rejection, degradation, circuit opening, and recovery in a sample or one service instance. |
| Nexary maintainer | Verify that diagnostic API fields are enough for the UI without leaking high-cardinality or business-sensitive data. |

## Core Questions

The Console must answer five questions:

1. Which governance resources exist in this application.
2. Which policy snapshot is bound to each resource.
3. Which resources are being rejected, failing, running slowly, or holding an open circuit.
4. Why a specific resource was last rejected and what its latest outcome was.
5. Which recent governance events help explain local behavior.

If a page does not directly answer these questions, it should not enter v0.9. Statistical trends, cross-instance comparison, policy editing, and account permissions are not v0.9 problems.

## Product Boundary

- The Console is served only inside the current Spring Boot application. The default page path is `/nexary/console`.
- The Console API default path is `/nexary/console/api`, and it exposes GET-only reads.
- The starter exposes pages and APIs only after `nexary.console.enabled=true`.
- Data comes from in-JVM governance runtime snapshots and recent events. It does not read remote storage or fetch other instances.
- Pages show diagnostic fields only. They do not show business payloads, full exception messages, or stack traces.
- Pages may refresh and filter locally, but they cannot submit policy changes.

## Page Scope

### Overview

Shows the current instance summary:

- Total resource count.
- Number of resources with open circuits.
- Recent rejection count.
- Recent failure count.
- The first few recent events.
- Empty state: when no governance resources exist, explain that there is no resource to display instead of showing an error.

### Resources

Shows the resource list with local filtering:

- Filter by `kind`: `cache`, `messaging`, `job`, `service`, `http`, `downstream`, `custom`.
- Show `resourceKey`, `kind`, `name`, `provider`, and `operation`.
- Show `circuitState`, window calls, failures, slow calls, and rejection counts.
- Show `lastOutcome` and `lastRejectionReason`.
- Open Resource Detail when a resource is selected.

### Resource Detail

Shows debugging data for one resource:

- Resource catalog fields: `resourceKey`, `kind`, `name`, `provider`, `operation`, `priority`.
- Policy snapshot: deadline, rate-limit window, request limit per window, concurrency limit, degradation flag, and circuit thresholds.
- Runtime snapshot: circuit state, window calls, failures, slow calls, active concurrency, and total rejections.
- Latest result: `lastOutcome`, `lastRejectionReason`, and latest update time.
- Recent events: only events for this resource.

### Events

Shows recent governance events:

- Fields include `timestamp`, `resourceKey`, `action`, `outcome`, `rejectionReason`, `circuitState`, and `durationBucket`.
- Support local filtering by resource, action, outcome, and rejection reason.
- Events come from a bounded in-memory buffer. The page must make clear that this is not an audit log.

### Settings Readonly

Shows read-only Console state:

- Whether the Console is enabled.
- Page path and API path.
- Recent event buffer limit.
- Data source note: current JVM memory snapshots.

This page has no save button, policy editing form, config push entry, or login settings.

## API Field Principles

APIs expose only low-cardinality diagnostic fields. Every field must support a specific page decision or visible UI element; responses must not grow for speculative future use.

Allowed fields:

- Resource fields: `resourceKey`, `kind`, `name`, `provider`, `operation`, `priority`.
- Policy fields: deadline, `maxRequestsPerWindow`, `rateLimitWindow`, `maxConcurrency`, `degraded`, and circuit thresholds / window settings.
- Runtime fields: `circuitState`, `windowCalls`, `windowFailures`, `windowSlowCalls`, `activeConcurrency`, `totalRejections`.
- Latest result: `lastOutcome`, `lastRejectionReason`, `lastUpdatedAt`.
- Event fields: `timestamp`, `resourceKey`, `action`, `outcome`, `rejectionReason`, `circuitState`, `durationBucket`.

Forbidden fields:

- User identifiers, tenant identifiers, business keys, order ids, message ids, cache keys, and tracing identifiers.
- Message bodies, request bodies, response bodies, full exception text, stack traces.
- Credentials, tokens, internal addresses, real broker connection strings, real database connection strings.
- Any field whose cardinality grows with users, orders, messages, or cache entries.

Field names should be stable. State values should come from enum semantics, for example `CLOSED`, `OPEN`, `HALF_OPEN`, `RATE_LIMITED`, `CONCURRENCY_LIMITED`, `CIRCUIT_OPEN`, `SUCCESS`, `FAILURE`, and `REJECTED`.

## Non-Goals

v0.9 does not include:

- Login, RBAC, user management, or permission configuration.
- Policy creation, policy edits, policy rollback, or remote config push.
- Multi-instance aggregation, cross-process circuit windows, or centralized state storage.
- Audit backends, operation records, or compliance reports.
- Sidecars, agents, or an independently deployed control console.
- Automatic provider switching, broker management, or external scheduler management.
- Incident response workflows, alert acknowledgement, ticketing, or on-call collaboration.

## Acceptance Criteria

Documentation acceptance:

- Chinese and English PRDs both exist and carry the same meaning.
- Documents do not include temporary work records, private paths, real credentials, or unpublished planning notes.
- Documents explicitly state read-only, current JVM, explicit enablement, and not a remote console.

Backend acceptance:

- Provide `GET /nexary/console/api/summary`.
- Provide `GET /nexary/console/api/resources`.
- Provide `GET /nexary/console/api/resources/{id}`.
- Provide `GET /nexary/console/api/events`.
- Empty resources return renderable empty-state data instead of 500.
- DTOs do not contain forbidden fields.
- When the starter is disabled, Console pages and APIs are not exposed.

Frontend acceptance:

- `/nexary/console` opens a non-empty page.
- Overview, Resources, Resource Detail, Events, and Settings Readonly are reachable.
- Empty, loading, error, and normal data states have explicit UI.
- Desktop layout is dense enough for debugging. Mobile only needs to remain readable.
- Pages have no policy edit, save, delete, login, permission, or remote config entry.

Integration acceptance:

- After starting the governance sample, use curl to trigger success, failure, rate limit, bulkhead rejection, open circuit, and half-open recovery, then see the matching resources, states, and events in the Console.
- Playwright screenshots verify that key pages are non-empty, primary content does not overlap, and filtering plus detail navigation works.
- Documentation scanning does not find internal terms, private paths, real credentials, or overstated console capabilities.

## Constraints For Implementation

Design: the UI should feel like a debugging tool, not a marketing page. Prefer dense tables, status badges, metric cards, event lists, filters, empty states, and error states. Design desktop first; keep mobile readable.

Backend: the Console API is a read model over governance diagnostics. DTOs may expose only low-cardinality fields and must not pass exception text, business identifiers, or provider-native types to the frontend.

Frontend: the frontend consumes read-only APIs only. Filtering starts locally; do not add complex server-side queries. Build output should be packageable into the server jar's static resources.
