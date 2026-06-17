# nexary-sample-cache-spi-redis

Cache SPI/provider dependency adoption sample. It shows business code compiling against `nexary-cache-api` while the concrete provider is introduced by a separate dependency.

This module does not depend on `nexary-cache-spring-boot-starter`, does not contain provider wiring classes, and does not hand-write `RedisTemplate`.

## Dependency Mode

This sample belongs to the verified Spring Boot 3.3.x + Java 17+ line. It does not use the starter; it shows the API + Redis provider dependency mode.

| Spring Boot | JDK | Status | API / Provider Dependencies |
| --- | --- | --- | --- |
| Spring Boot 3.3.x | Java 17+ | currently verified | `nexary-cache-api` + `nexary-cache-redis` |
| Spring Boot 2.7.x | Java 8+ | v0.2 target, pending verification, unpublished | proposed Java 8 compatible API/provider line |
| Spring Boot 4.x | Java 21+ | later v0.2 target, pending verification, unpublished | proposed Boot4 provider line |

`build.gradle`:

```groovy
// Currently verified: Spring Boot 3.3.x + Java 17+
implementation project(':nexary-cache:nexary-cache-api')
runtimeOnly project(':nexary-cache:nexary-cache-redis')

// Maven Central equivalent after publication:
// implementation "org.nexary:nexary-cache-api:${nexaryVersion}"
// runtimeOnly "org.nexary:nexary-cache-redis:${nexaryVersion}"
```

To switch providers, change the provider dependency and `nexary.cache.provider` configuration, not controller/service business code.

## Configuration

`application.yml`:

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
./gradlew :nexary-samples:nexary-sample-cache-spi-redis:run
```

Try the endpoints:

```bash
curl http://localhost:8082/examples/cache/profiles/42
curl -X POST http://localhost:8082/examples/cache/warmup
curl 'http://localhost:8082/examples/cache/batch?ids=101,102'
curl http://localhost:8082/examples/cache/user-counts/42
curl -X POST 'http://localhost:8082/examples/cache/user-counts/42/increments?delta=2'
curl -X POST 'http://localhost:8082/examples/cache/user-counts/42/decrements?delta=1'
curl -X DELETE http://localhost:8082/examples/cache/user-counts/42
curl -X POST http://localhost:8082/examples/cache/locks/profile-refresh
```

## Counter Boundary

`UserCount` uses `CacheCounterClient`, not `CacheClient#get/put`. A counter is a Redis atomic counter, not an ordinary cache object. It does not enter JVM-local L1 and is not affected by tiered cache local values.

Counter TTL uses TTL-on-create semantics: the first `increment/decrement` that creates the counter can set expiry, and later mutations do not refresh that TTL. This sample demonstrates single-key atomic counters only; it does not implement multi-key transactions, quota systems, fencing tokens, or version-checked counters.

## Lock and Fencing Token Boundary

`POST /examples/cache/locks/{id}` demonstrates owner-token lock acquisition, renewal, safe release, and returns `fencingToken` when the provider supports it. The owner token protects only lock release/renew, not downstream writes. The Redis provider issues a monotonic fencing token per lock resource when acquisition succeeds.

Business callers should carry the fencing token to the real protected resource. That resource must store the highest accepted token and reject stale writes with lower tokens. This sample does not implement Redlock, does not claim strong consistency, and is not a replacement for a transactional / linearizable protected resource. Providers without fencing-token support can still return owner-token locks only.

## Tiered Cache Boundary

`tiered-enabled` defaults to `false`. In that mode reads and writes use the Redis-backed `CacheClient`; no JVM-local L1 or invalidation listener is created. This is the production default and the safer path when services need fresher cross-node reads.

If `tiered-enabled: true` is explicitly set, Nexary adds a JVM-local L1 cache to improve read performance and enables best-effort cross-node L1 invalidation through Redis Pub/Sub by default. When node A writes, deletes, or updates a TTL, it publishes an invalidation event only after the Redis L2 mutation succeeds. Node B evicts only the matching local key, so the next read loads from Redis L2 again.

This invalidation is not strong consistency, not exactly-once delivery, and does not provide fencing tokens or version-checked reads. When Pub/Sub is unavailable, events are missed, or a node is briefly offline, node B can still serve the old local value until `local-ttl` expires. Use tiered mode only for read-mostly, short-TTL data that can tolerate brief staleness; counters, balances, inventory, permissions, order status, and other strongly fresh data should stay Redis-only or use a dedicated consistency model.

## Observation Boundary

Business code does not need controller/service changes for metrics. Nexary cache implementations emit `NexaryObservationEvent`; the default publisher is no-op. Real services can register a `NexaryObservationListener` at the application layer and bridge events to their own metrics system.

Recommended metric names: `nexary.cache.operation.duration`, `nexary.cache.operation.count`, `nexary.cache.hit.count`, `nexary.cache.miss.count`, `nexary.cache.invalidation.count`, `nexary.cache.lock.count`, and `nexary.cache.counter.count`.

Allowed tags are bounded fields only: `capability`, `operation`, `provider`, `tier`, `outcome`, and `failure`. Do not put cache keys, business ids, lock tokens, fencing tokens, exception messages, or stack traces into metric tags.

## Local Validation Commands

```bash
./gradlew \
  -DNEXARY_RUN_INFRA_TESTS=true \
  -DNEXARY_INFRA_REDIS_HOST=127.0.0.1 \
  -DNEXARY_INFRA_REDIS_PORT=16379 \
  :nexary-samples:nexary-sample-cache-spi-redis:test
```

Expected evidence:

- `api` / `service` / `common` depend only on Nexary cache API and DTOs.
- Business code does not import Redis, Spring Data Redis, Caffeine, or provider implementation types.
- The Redis provider is loaded by the runtime dependency and `nexary.cache.provider=redis`; business endpoints do not expose provider diagnostics.
