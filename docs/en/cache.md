# Cache Guide

Cache is one of Nexary's clearest standalone capabilities.

## What to Read First

- module entry: [../../nexary-cache/README.md](../../nexary-cache/README.md)
- module guide: [modules.md](modules.md)
- acceptance checklist: [cache-acceptance.md](cache-acceptance.md)
- sample guide: [samples.md](samples.md)

## Version Selection

Current development version: `0.11.0`. After publication, replace `${nexary.version}` with the latest release / tag version.

| Spring Boot | JDK | Cache Status | Starter artifactId | Single-provider Dependencies |
| --- | --- | --- | --- | --- |
| Spring Boot 3.3.x | Java 17+ | currently verified | `nexary-cache-spring-boot-starter` | `nexary-cache-api` + `nexary-cache-redis` |
| Spring Boot 2.7.x | Java 8+ | Redis single-tier is verified; tiered local cache is not included | `nexary-cache-spring-boot2-starter` | `nexary-cache-api` + `nexary-cache-redis-spring-boot2` |
| Spring Boot 4.1.x | Java 21 primary validation runtime | Cache Redis provider/starter verified; not whole-repository Boot4 support | `nexary-cache-spring-boot4-starter` | `nexary-cache-api` + `nexary-cache-redis-spring-boot4` |

`nexary-cache-spring-boot-starter` represents the verified Spring Boot 3.3 / Java 17+ mainline. `nexary-cache-spring-boot2-starter` represents the verified Spring Boot 2.7 / Java 8+ Redis single-tier entry. `nexary-cache-spring-boot4-starter` represents the verified Cache Redis provider/starter entry for Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime; this is a Nexary validation runtime statement, not a Spring official JDK-floor statement; it does not imply Boot4 support for every module in the repository.

Valkey is a v0.3 Redis-protocol deployment target. Business code still uses `CacheClient`, `CacheCounterClient`, `CacheKey`, and `CacheCounterKey`; the Spring Boot 3.3 / Java17+ line switches by setting `nexary.cache.provider=valkey` and pointing the Redis-protocol connection at Valkey. A support claim must be backed by the Valkey container integration gate.

## Dependency Modes

### Starter Mode

Starter mode is for Spring Boot services that want the cache capability aggregated by the Nexary starter. Business code injects only Nexary abstractions and does not import Redis, Caffeine, or Spring Data Redis native types.

Spring Boot 3.3.x / Java 17+:

```groovy
def nexaryVersion = "0.11.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation "com.aweimao:nexary-cache-spring-boot-starter"
}
```

Spring Boot 2.7.x / Java 8+ Redis single-tier:

```groovy
dependencies {
    implementation "com.aweimao:nexary-cache-spring-boot2-starter:0.11.0"
}
```

Spring Boot 4.1.x / Java 21 primary validation runtime:

```groovy
dependencies {
    implementation "com.aweimao:nexary-cache-spring-boot4-starter:0.11.0"
}
```

```yaml
nexary:
  cache:
    # Loaded by the Nexary cache starter; business code should not branch on provider.
    provider: redis
    redis:
      # Redis-only is the production default; tiered cache must be explicitly enabled.
      tiered-enabled: false
```

Valkey uses the same Redis-protocol connection settings:

```yaml
nexary:
  cache:
    provider: valkey
    redis:
      # This remains the Redis-protocol configuration prefix; business code does not depend on Redis native APIs.
      tiered-enabled: false
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 16380
```

### Single-provider Dependency Mode

Single-provider mode is for services that want explicit provider dependency control. Business code compiles against `nexary-cache-api`; the Redis provider is loaded by the runtime dependency and `nexary.cache.provider` configuration.

Spring Boot 3.3.x / Java 17+:

```groovy
def nexaryVersion = "0.11.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")

    // Business code uses only CacheClient / CacheCounterClient and other Nexary APIs.
    implementation "com.aweimao:nexary-cache-api"

    runtimeOnly "com.aweimao:nexary-cache-redis"
}
```

Spring Boot 2.7.x / Java 8+ Redis single-tier:

```groovy
dependencies {
    implementation "com.aweimao:nexary-cache-api:0.11.0"
    runtimeOnly "com.aweimao:nexary-cache-redis-spring-boot2:0.11.0"
}
```

Spring Boot 4.1.x / Java 21 primary validation runtime:

```groovy
dependencies {
    implementation "com.aweimao:nexary-cache-api:0.11.0"
    runtimeOnly "com.aweimao:nexary-cache-redis-spring-boot4:0.11.0"
}
```

## Supported Pieces

- `nexary-cache-api`
- `nexary-cache-redis`
- `nexary-cache-redis-spring-boot2`: Boot2 / Java8+ Redis single-tier provider
- `nexary-cache-redis-spring-boot4`: Redis provider for Spring Boot 4.1 with Java 21 as Nexary's primary validation runtime
- Valkey Redis-protocol deployment target: business API stays unchanged and selection is done through `nexary.cache.provider=valkey` plus the connection address
- Redis tiered cache mode
- cache-aside, TTL, batch, and distributed lock abstractions
- atomic counter abstractions separate from ordinary `CacheClient#get/put`
- owner-token locks and optional fencing tokens

## Limits

- Redis is the primary implementation
- Valkey reuses the Redis-protocol provider path; it does not add business APIs or expose Valkey, Redis, Lettuce, or Spring Data Redis native types to business code
- Caffeine is only an internal L1, not a public peer backend
- single-tier Redis cache is the recommended default when services need fresher cross-node reads
- Boot2 / Java8+ currently supports only Redis single-tier mode; explicitly enabling `nexary.cache.redis.tiered-enabled=true` fails fast
- Boot4.1 / Java21 primary validation currently applies to the Cache Redis provider/starter only; it is not a whole-repository Boot4 support claim
- Valkey support claims require real Valkey container tests; Redis tests alone are not a substitute
- Redis tiered cache mode must be explicitly enabled and is only suitable for read-mostly, short-TTL data that tolerates brief staleness
- when tiered mode is explicitly enabled, Redis Pub/Sub provides best-effort cross-node L1 invalidation; it evicts only the matching local key and does not clear unrelated keys
- invalidation events are published only after a Redis L2 put, delete, or expire mutation succeeds; the writing node ignores its own events so the fresh local value is not removed by self-delivery
- Pub/Sub invalidation is not strong consistency, not exactly-once delivery, and does not provide fencing tokens or version-checked reads; when invalidation is unavailable or missed, `local-ttl` remains the fallback boundary
- balances, inventory, counters, permissions, order status, and other strongly fresh data must not use JVM-local L1; keep those data paths Redis-only or use a dedicated consistency model
- counters are not ordinary cache objects. Use `CacheCounterClient` / `CacheCounterKey` for atomic increments and decrements; the Redis implementation uses Redis atomic counter operations and never enters tiered L1
- counter TTL uses TTL-on-create semantics: the first `increment/decrement` with a TTL sets expiry when the counter is created, and later mutations do not refresh that TTL
- locks still rely on owner-token safe release and renewal; the owner token protects only lock release/renew, not downstream writes
- the Redis implementation issues a monotonic fencing token per lock resource when acquisition succeeds
- a fencing token is only a monotonic token the caller can carry. The caller must pass it to the protected resource, and that resource must store the highest accepted token and reject older operations with lower tokens
- fencing tokens are not Redlock, not strong consistency or complete distributed coordination, and not a replacement for a transactional / linearizable protected resource
- Cache paths emit Nexary-level `NexaryObservationEvent` events; the default publisher is no-op, so behavior is unchanged when no listener is configured
- the demo is not the primary cache validation surface; cache-dedicated samples and tests are

## Observation Events and Metrics

Cache currently provides an event surface. Micrometer and Actuator types are not part of the core/cache public API. Spring services can add `nexary-observation-micrometer-spring-boot-starter` to bridge events to Micrometer, or register a custom `NexaryObservationListener` for another metrics system.

Recommended metric names:

- `nexary.cache.operation.duration`: record event `duration()`
- `nexary.cache.operation.count`: count events
- `nexary.cache.hit.count` / `nexary.cache.miss.count`: filter `outcome=hit|miss`
- `nexary.cache.invalidation.count`: filter `operation=cache.invalidation_*`
- `nexary.cache.lock.count`: filter `operation=cache.lock_*`
- `nexary.cache.counter.count`: filter `operation=cache.counter_*`

Bounded tag rules:

- `capability=cache`
- `operation`: for example `cache.get`, `cache.batch_get`, `cache.invalidation_publish`, `cache.lock_acquire`, `cache.counter_increment`
- `provider`: for example `redis` or `tiered`
- `tier`: `l1`, `l2`, or `none`
- `outcome`: for example `success`, `failure`, `hit`, `miss`, `acquired`, `not_acquired`, `published`, `received`, `evicted`
- `failure`: coarse categories such as `none`, `invalid_request`, `state`, or `runtime`

Never put these values into tags:

- cache keys, raw namespaces, business ids, payloads
- lock owner tokens or fencing tokens
- exception messages or stack traces
- unsanitized tenant, user, order, or other high-cardinality / sensitive fields

Dashboard suggestions:

- Redis-only: track `cache.get` p95/p99, hit/miss ratio, failure count, lock acquisition success rate, and counter mutation volume
- Tiered: also track `tier=l1|l2` hit/miss ratio and `cache.invalidation_publish/receive/evict` counts and failures
- Lock: split `acquired`, `not_acquired`, and `not_owner`; never display owner tokens or fencing tokens
- Counter: track increment/decrement/current/clear latency and failure rate; counters do not enter L1, so do not interpret counter performance through L1 hit ratio

Non-goals: Nexary does not ship a built-in governance dashboard here, does not expose Micrometer types in the core/cache API, does not claim exactly-once events, and does not use observation events as audit logs or consistency mechanisms.

## Recommended Order

1. read the module entry and API boundaries
2. inspect the cache-dedicated sample
3. review the cache acceptance checklist
4. use the [local validation guide](verification.md) when you need real middleware evidence
