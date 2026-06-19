# Cache Acceptance Checklist

- the API does not expose Redis or Caffeine native types
- TTL, batch, cache-aside, and distributed lock abstractions are covered
- Redis-only is the default path; with `tiered-enabled=false`, no JVM-local L1 or invalidation listener is created
- Redis tiered mode boundaries are clear, Caffeine stays internal as L1 only, and explicit opt-in uses Redis Pub/Sub for best-effort L1 invalidation
- `put` / `delete` / `expire` publish invalidation events only after the Redis L2 mutation succeeds, and publish nothing when the mutation fails
- received invalidation evicts only the matching L1 key and does not clear unrelated keys; self-originated events do not remove the fresh local value
- docs must state that Pub/Sub invalidation is not strong consistency or exactly-once delivery, and local TTL remains the fallback when invalidation is missed
- docs must state that strongly fresh data is not suitable for JVM-local L1
- counters must use a separate counter API, not ordinary `CacheClient#get/put`
- the Redis counter implementation must use atomic counter operations and must not enter `TieredCacheClient` or JVM-local L1
- counter TTL semantics must be explicit and tested: TTL is set only when the counter is first created, and later mutations do not refresh it
- owner-token lock regression must pass, and unlock must remain current-owner safe
- when fencing tokens are implemented, the public API must be Nexary-level and Redis tokens must be monotonic for the same lock resource
- docs must explain how callers obtain, carry, and validate fencing tokens at the protected resource, and must explicitly avoid Redlock or strong distributed coordination claims
- the cache-dedicated sample explains integration clearly
- docs and samples do not treat the demo as the primary validation surface
- when real validation is needed, local validation commands can cover Redis integration and regression
