# Governance

Governance adds local protection around business calls: do not start work after the deadline, reject traffic that is too dense, send excess concurrent calls to fallback, and temporarily degrade a downstream path without rewriting business code.

Application code still uses Nexary APIs such as `GovernanceContext`, `GovernanceRuntime`, `CacheClient`, `MessagePublisher`, and `NexaryJob`. Redis, Kafka, RocketMQ, ActiveMQ, XXL-JOB, and PowerJob native types stay out of application-facing interfaces.

## Add the Starter

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

## Configure Policies

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

## Integrated Paths

| Path | v0.4 behavior |
| --- | --- |
| `GovernanceRuntime` | Checks deadline, rate limit, bulkhead, and degradation before the action starts; publishes governance events when it rejects; runs fallback when provided, otherwise throws `GovernanceRejectedException`. |
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

## Current Boundaries

- Deadline is a pre-start check and context propagation. It does not forcibly stop ordinary Java code that has already entered the business method.
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
