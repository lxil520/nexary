# Cache 指南

Cache 是 Nexary 当前最独立的一项能力。

## 你应该先看什么

- 模块入口：[../../nexary-cache/README.md](../../nexary-cache/README.md)
- 模块说明：[modules.md](modules.md)
- 验收清单：[cache-acceptance.md](cache-acceptance.md)
- 样例说明：[samples.md](samples.md)

## 当前范围

- `nexary-cache-api`
- `nexary-cache-redis`
- Redis tiered cache mode
- cache-aside、TTL、batch、分布式锁抽象
- atomic counter 抽象：独立于普通 `CacheClient#get/put`
- owner-token lock 与可选 fencing token

## 当前边界

- Redis 是主实现
- Caffeine 仅作为内部 L1，不是公开平级后端
- Redis 单级缓存是默认推荐模式，适合需要跨节点读取新值的业务
- Redis tiered cache mode 需要显式开启，只适合 read-mostly、短 TTL、允许短暂陈旧的数据
- tiered mode 显式开启后，Redis Pub/Sub 会做 best-effort 跨节点 L1 失效；它只删除匹配 key 的本地 L1，不清空无关 key
- 失效事件只在 Redis L2 写入、删除或过期设置成功后发布；当前节点会忽略自己发布的事件，避免覆盖刚写入的新本地值
- Pub/Sub 失效不是强一致、不是 exactly-once，也没有 fencing-token 或 version-checked read；如果失效事件不可用或丢失，本地 `local-ttl` 仍是最终兜底
- 余额、库存、计数器、权限、订单状态等强新鲜数据不要使用 JVM-local L1；这类数据应保持 Redis-only 或使用专门的一致性模型
- 计数器不是普通 cache object。需要原子加减时使用 `CacheCounterClient` / `CacheCounterKey`，Redis 实现走 Redis 原子 counter 操作，不进入 tiered L1
- counter TTL 语义是 TTL-on-create：带 TTL 的首次 `increment/decrement` 创建 counter 时设置过期时间，后续加减不刷新 TTL
- lock 仍以 owner-token 安全释放和续租为基础；owner-token 只保护 lock 自身的 release/renew，不保护下游写入
- Redis 实现会在成功获取锁时为同一 lock resource 发行单调递增的 fencing token
- fencing token 只是调用方可携带的单调令牌。调用方必须把 token 传给被保护资源，由该资源保存已接受最大 token，并拒绝更低 token 的旧操作
- fencing token 不是 Redlock，不是强一致或完整分布式协调，也不能替代 transactional / linearizable 的受保护资源
- Cache 路径会通过 `NexaryObservationEvent` 发 provider-neutral 观测事件；默认 publisher 是 no-op，不配置 listener 时不改变业务行为
- showcase 不是 cache 的主验证面，主要验证应围绕 cache 专项样例和独立测试

## 观测事件与指标

Cache 当前提供事件面，不把 Micrometer 或 Actuator 类型放进 core/cache public API。Spring 服务可以引入 `nexary-observation-micrometer-spring-boot-starter` 自动桥接到 Micrometer，也可以注册自定义 `NexaryObservationListener` 把事件接入自己的指标系统。

推荐指标名：

- `nexary.cache.operation.duration`：按事件 `duration()` 记录耗时
- `nexary.cache.operation.count`：按事件计数
- `nexary.cache.hit.count` / `nexary.cache.miss.count`：筛选 `outcome=hit|miss`
- `nexary.cache.invalidation.count`：筛选 `operation=cache.invalidation_*`
- `nexary.cache.lock.count`：筛选 `operation=cache.lock_*`
- `nexary.cache.counter.count`：筛选 `operation=cache.counter_*`

固定 tag 规则：

- `capability=cache`
- `operation`：如 `cache.get`、`cache.batch_get`、`cache.invalidation_publish`、`cache.lock_acquire`、`cache.counter_increment`
- `provider`：如 `redis`、`tiered`
- `tier`：`l1`、`l2` 或 `none`
- `outcome`：如 `success`、`failure`、`hit`、`miss`、`acquired`、`not_acquired`、`published`、`received`、`evicted`
- `failure`：粗分类，如 `none`、`invalid_request`、`state`、`runtime`

禁止放进 tag 的内容：

- cache key、namespace 原值、业务 id、payload
- lock owner token、fencing token
- exception message、stack trace
- 未清洗的租户、用户、订单等高基数或敏感字段

Dashboard 建议：

- Redis-only：关注 `cache.get` p95/p99、`hit/miss` 比例、`failure` 数量、lock acquire 成功率、counter mutation 数量
- Tiered：额外关注 `tier=l1|l2` 的 hit/miss 比例、`cache.invalidation_publish/receive/evict` 数量和 failure
- Lock：区分 `acquired`、`not_acquired`、`not_owner`，不要展示 owner token 或 fencing token
- Counter：关注 increment/decrement/current/clear 的失败率和耗时；counter 不进入 L1，所以不要按 L1 命中率解释 counter 性能

非目标：当前不提供内置治理面板，不在 core/cache API 暴露 Micrometer 类型，不声明 event exactly-once，也不把观测事件作为审计日志或一致性机制。

## 推荐接入顺序

1. 读模块入口和 API 边界
2. 看 cache 专项样例
3. 看 cache 验收清单
4. 需要真实中间件验证时，按 [本地验证指南](verification.md) 运行对应命令
