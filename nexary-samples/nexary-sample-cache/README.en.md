# nexary-sample-cache

Cache starter adoption sample. It shows how a business service uses only the Nexary cache API while the starter aggregates cache capabilities and `nexary.cache.*` selects the underlying provider.

This module does not implement a provider, does not hand-write `RedisTemplate`, and does not expose Redis, Spring Data Redis, or Caffeine native types to business code.

## Module Boundary

```text
org.nexary.samples.cache.app
  CacheSampleApplication        # application entry point
org.nexary.samples.cache.api
  CacheSampleController         # HTTP/test trigger surface
org.nexary.samples.cache.service
  UserProfileService            # user profile service; depends only on CacheClient / CacheCounterClient
org.nexary.samples.cache.common
  Profile / LockResult DTOs
```

## Dependency Mode

This sample belongs to the verified Spring Boot 3.3.x + Java 17+ line.

| Spring Boot | JDK | Status | Cache Starter |
| --- | --- | --- | --- |
| Spring Boot 3.3.x | Java 17+ | currently verified | `nexary-cache-spring-boot-starter` |
| Spring Boot 2.7.x | Java 8+ | v0.2 target, pending verification, unpublished | proposed `nexary-cache-spring-boot2-starter` |
| Spring Boot 4.x | Java 21+ | later v0.2 target, pending verification, unpublished | proposed `nexary-cache-spring-boot4-starter` |

Before publication, the recommended naming cleanup is to publish an explicit `nexary-cache-spring-boot3-starter`, or clearly document `nexary-cache-spring-boot-starter` as Boot3-only. Do not copy unverified combinations as production dependencies.

`build.gradle` uses the starter:

```groovy
// Currently verified: Spring Boot 3.3.x + Java 17+
implementation project(':nexary-boot:nexary-cache-spring-boot-starter')

// Maven Central equivalent after publication:
// implementation "org.nexary:nexary-cache-spring-boot-starter:${nexaryVersion}"
//
// If explicit Boot3 coordinates are adopted before publication, use:
// implementation "org.nexary:nexary-cache-spring-boot3-starter:${nexaryVersion}"
```

The starter aggregates the cache API and optional providers. Business code injects Nexary abstractions such as `CacheClient` and `CacheCounterClient`; switching providers must not change controller/service code.

## Configuration

`application.yml` activates the `redis` profile by default. Redis selector and settings live in `application-redis.yml`:

```yaml
nexary:
  cache:
    provider: redis
    redis:
      default-ttl: 10m
      local-ttl: 30s
      lock-retry-interval: 50ms
      fencing-token-prefix: "nexary:sample:fence:"
      tiered-enabled: false
      invalidation-enabled: true
      invalidation-channel: nexary:sample:cache:invalidation
      invalidation-listener-auto-start: true
      invalidation-listener-recovery-backoff: 5s
```

Local Redis defaults to `127.0.0.1:16379`. Override with `NEXARY_SAMPLE_REDIS_HOST` and `NEXARY_SAMPLE_REDIS_PORT`.

## Run

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
./gradlew :nexary-samples:nexary-sample-cache:run
```

Try the endpoints:

```bash
curl http://localhost:8081/examples/cache/profiles/42
curl -X POST http://localhost:8081/examples/cache/warmup
curl 'http://localhost:8081/examples/cache/batch?ids=101,102'
curl http://localhost:8081/examples/cache/user-counts/42
curl -X POST 'http://localhost:8081/examples/cache/user-counts/42/increments?delta=2'
curl -X POST 'http://localhost:8081/examples/cache/user-counts/42/decrements?delta=1'
curl -X DELETE http://localhost:8081/examples/cache/user-counts/42
curl -X POST http://localhost:8081/examples/cache/locks/starter
curl -X DELETE http://localhost:8081/examples/cache/profiles/42
```

## What to Copy into a Real Service

- ordinary object cache usage with `CacheClient` and `CacheKey` from `UserProfileService`
- atomic counter usage with `CacheCounterClient` and `CacheCounterKey`
- cache-aside read path
- batch warmup and batch read with `putAll` / `getAll`
- current / increment / decrement / clear usage through the `user-counts` endpoints
- explicit invalidation with `delete`
- `nexary.cache.*` comments from `application-redis.yml`

## Counter Boundary

`UserCount` uses `CacheCounterClient`, not `CacheClient#get/put`. A counter is a Redis atomic counter, not an ordinary cache object. It does not enter JVM-local L1 and is not affected by tiered cache local values.

Counter TTL uses TTL-on-create semantics: the first `increment/decrement` that creates the counter can set expiry, and later mutations do not refresh that TTL. This sample demonstrates single-key atomic counters only; it does not implement multi-key transactions, quota systems, fencing tokens, or version-checked counters.

## Lock and Fencing Token Boundary

`POST /examples/cache/locks/{id}` demonstrates owner-token lock acquisition, renewal, safe release, and returns `fencingToken` when the provider supports it. The owner token protects only lock release/renew, not downstream writes. The Redis provider issues a monotonic fencing token per lock resource when acquisition succeeds.

Business callers should carry the fencing token to the real protected resource. That resource must store the highest accepted token and reject stale writes with lower tokens. This sample does not implement Redlock, does not claim strong consistency, and is not a replacement for a transactional / linearizable protected resource. Providers without fencing-token support can still return owner-token locks only.

## Tiered Cache Boundary

`tiered-enabled` defaults to `false`. In that mode reads and writes use the Redis-backed `CacheClient`; no JVM-local L1 or invalidation listener is created. This is the production default and the safer path when services need fresher cross-node reads.

If `tiered-enabled: true` is explicitly set, Nexary adds a JVM-local L1 cache to improve read performance and enables best-effort cross-node L1 invalidation through Redis Pub/Sub by default. When node A writes, deletes, or updates a TTL, it publishes an invalidation event only after the Redis L2 mutation succeeds. Node B evicts only the matching local key, so the next read loads from Redis L2 again.

This invalidation is not strong consistency, not exactly-once delivery, and does not provide fencing tokens or version-checked reads. When Pub/Sub is unavailable, events are missed, or a node is briefly offline, node B can still serve the old local value until `local-ttl` expires. Use tiered mode only for read-mostly, short-TTL data that can tolerate brief staleness.

## Observation Boundary

Business code does not need controller/service changes for metrics. Nexary cache implementations emit `NexaryObservationEvent`; the default publisher is no-op. Real services can register a `NexaryObservationListener` at the application layer and bridge events to their own metrics system.

Recommended metric names: `nexary.cache.operation.duration`, `nexary.cache.operation.count`, `nexary.cache.hit.count`, `nexary.cache.miss.count`, `nexary.cache.invalidation.count`, `nexary.cache.lock.count`, and `nexary.cache.counter.count`.

Allowed tags are bounded fields only: `capability`, `operation`, `provider`, `tier`, `outcome`, and `failure`. Do not put cache keys, business ids, lock tokens, fencing tokens, exception messages, or stack traces into metric tags.

## What Not to Copy

- Do not introduce Redis, Spring Data Redis, or Caffeine native types into controllers/services.
- Do not hand-write provider beans, `RedisTemplate`, or framework wiring in the business sample.
- Do not read Spring `Environment` in business sample code to infer the active provider; Nexary framework loads selector configuration.
- `POST /examples/cache/locks/{id}` demonstrates owner-token based release, renewal, and optional fencing token only. It is not a complete distributed coordination pattern.
- Do not use tiered cache for counters, balances, inventory, permissions, order status, or other values that require strong cross-node freshness; keep those data paths Redis-only or use a dedicated consistency model.

## SPI / Provider Dependency Mode

SPI/provider dependency adoption lives in a separate module:

```bash
./gradlew :nexary-samples:nexary-sample-cache-spi-redis:run
```

Its production dependency shape is:

```groovy
implementation "org.nexary:nexary-cache-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-cache-redis:${nexaryVersion}"
```

Do not mix the two dependency modes in one business sample.

## Local Validation Commands

```bash
./gradlew :nexary-cache:nexary-cache-api:test \
  :nexary-cache:nexary-cache-tiered-internal:test \
  :nexary-samples:nexary-sample-cache:test

./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
./gradlew \
  -DNEXARY_RUN_INFRA_TESTS=true \
  -DNEXARY_INFRA_REDIS_HOST=127.0.0.1 \
  -DNEXARY_INFRA_REDIS_PORT=16379 \
  :nexary-cache:nexary-cache-redis:test \
  :nexary-samples:nexary-sample-cache:test
```

Expected evidence:

- `api` / `service` / `common` have no Redis, Spring Data Redis, Caffeine, or provider implementation imports.
- starter sample selects the provider through `nexary.cache.provider=redis` in `application-redis.yml`; business endpoints do not expose provider diagnostics.
- Redis lock endpoint returns `acquired=true`; the renewal field proves the real `CacheClient.tryLock` path was exercised.
