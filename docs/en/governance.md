# Governance

Governance in Nexary v0.3 starts with shared Java semantics: how much time a call has left, what kind of traffic it carries, how important it is, which resource is protected, and when retry should stop.

This layer only provides Java APIs and event objects. It does not include a rate limiter, bulkhead, fallback executor, monitoring backend, or provider SDK in application APIs.

## Pass Context Before a Business Call

Create a `GovernanceContext` before entering a business path or calling a downstream service:

```java
GovernanceContext context = GovernanceContext.builder()
        .deadline(Instant.now().plusMillis(200))
        .trafficTag(TrafficTag.builder()
                .channel(TrafficTag.Channel.ONLINE)
                .priority(TrafficTag.Priority.HIGH)
                .build())
        .resource(GovernanceResource.service("checkout-api"))
        .build();

return GovernanceContext.callWithContext(context, () -> checkoutService.submit(order));
```

`GovernanceContext.callWithContext(...)` also updates the older `DeadlineContext`. Existing cache, messaging, and job code that reads `DeadlineContext.current()` can keep using that deadline entry point.

## Check the Deadline

Use `TimeoutDecision` when code only needs to know whether it may start:

```java
TimeoutDecision decision = TimeoutDecision.from(context, Instant.now());
if (!decision.isAllowed()) {
    return CheckoutResult.timeout();
}
```

`reason()` returns bounded values such as `allowed` or `deadline_exceeded`. Do not put user ids, order numbers, exception text, or other dynamic data into the reason.

## Resource Names

`GovernanceResource.name` and `operation` should be stable, low-cardinality configured names:

- `checkout-api`
- `payment-events`
- `nightly-sync`
- `redis-cache`

For example, in `GovernanceResource.http("profile-api", "get-profile")`, `profile-api` is the resource name and `get-profile` is the operation name. Do not include the real URL, user id, or order number.

Do not put these values there:

- user ids, order numbers, phone numbers
- cache keys, message ids, execution ids
- tokens, exception text, stack traces

Those values make policy matching and event grouping hard to operate and may leak business data into logs or monitoring systems.

## Stop Retry

When a downstream path clearly rejects another retry, application code or a provider can use `RetrySignal.stop("reason")`. The `reason` is only for local decisions and should not be used as an event tag.

The core event factory records this as `governance.retry.stopped` and buckets attempts into fixed values such as `0`, `1`, `2`, `3_5`, `6_10`, and `gt_10`.

## Event Objects

`GovernanceObservationEvents` only creates event objects. It does not send them to any monitoring backend. Current event names:

- `governance.deadline.exceeded`
- `governance.retry.stopped`
- `governance.rate_limited`
- `governance.degraded`
- `governance.bulkhead.rejected`

Later runtime and observation modules can reuse these names. Events do not include payloads, cache keys, message ids, execution ids, tokens, exception text, or stack traces.

## Not Included Yet

- No rate limiter, bulkhead, or fallback execution logic.
- No built-in console.
- No sidecar, agent, or platform hosting.
- No automatic instance quarantine, automatic root cause analysis, or IDC traffic shifting.
- No Sentinel, Resilience4j, Micrometer, Redis, Kafka, RocketMQ, PowerJob, or ActiveMQ types in application APIs.
