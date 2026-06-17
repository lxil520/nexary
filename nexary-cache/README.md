# Nexary Cache

- 中文入口：[../docs/zh/cache.md](../docs/zh/cache.md)
- English entry: [../docs/en/cache.md](../docs/en/cache.md)

本目录是 Nexary 的缓存能力入口，不是全仓库总览。

当前关注：

- `nexary-cache-api`
- `nexary-cache-redis`
- Redis tiered cache mode（显式开启；Redis Pub/Sub best-effort 失效；不提供跨节点 L1 强一致）
- Atomic counter API（独立于普通 cache object；Redis 实现走原子 counter 操作）
- Lock fencing token（保持 owner-token lock 兼容；Redis 成功获取锁时发行同资源单调 token）

默认生产路径是 Redis 单级缓存。`nexary.cache.redis.tiered-enabled=false` 时不会创建 JVM-local L1，也不会启动失效 listener。

显式开启 tiered mode 后，写入、删除、更新 TTL 会在 Redis L2 mutation 成功后发布失效事件，其他节点收到事件后只删除匹配 key 的本地 L1。该能力是 best-effort：不是强一致，不是 exactly-once，失效不可用或丢失时仍依赖 `local-ttl` 兜底。

计数器不要用普通 `CacheClient#get/put` 建模。需要原子加减时使用 `CacheCounterClient`，Redis provider 直接执行原子 counter 操作，不进入 JVM-local L1。counter TTL 是 TTL-on-create：首次创建时设置，后续加减不刷新。

lock 默认仍是 owner-token 语义：释放和续租只对当前 owner 生效，owner-token 不保护下游写入。Redis provider 成功获取锁时会为同一 lock resource 返回单调递增的 fencing token。调用方可以把 token 写入或传递给受保护资源，由该资源保存已接受最大 token，并拒绝更低 token 的旧操作。该能力不是 Redlock，不声明强一致或完整分布式协调，也不能替代 transactional / linearizable 的受保护资源。

Cache 会通过 `NexaryObservationEvent` 发 provider-neutral 观测事件。默认 publisher 是 no-op，不配置 listener 时不会改变行为。推荐指标名包括：

- `nexary.cache.operation.duration`
- `nexary.cache.operation.count`
- `nexary.cache.hit.count`
- `nexary.cache.miss.count`
- `nexary.cache.invalidation.count`
- `nexary.cache.lock.count`
- `nexary.cache.counter.count`

事件 tags 只允许有界字段：`capability`、`operation`、`provider`、`tier`、`outcome`、`failure`。不要把 cache key、namespace 原值、业务 id、lock token、fencing token、exception message 或 stack trace 放进指标标签。Micrometer/Actuator bridge 不属于 core/cache public API；需要时应在 Spring Boot 集成层或业务侧 listener 中完成桥接。

验收清单：

- 中文：[../docs/zh/cache-acceptance.md](../docs/zh/cache-acceptance.md)
- English: [../docs/en/cache-acceptance.md](../docs/en/cache-acceptance.md)
