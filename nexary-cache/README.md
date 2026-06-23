# Nexary Cache

- 中文入口：[../docs/zh/cache.md](../docs/zh/cache.md)
- English entry: [../docs/en/cache.md](../docs/en/cache.md)

本目录是 Nexary 的缓存模块入口，不是全仓库总览。

## 版本与接入入口

当前开发版本：`0.5.0`。发布到 Maven Central 后，请把 `${nexary.version}` 替换为最新 release / tag 版本。

| Spring Boot | JDK | Cache 状态 | Starter artifactId | SPI/provider 依赖 |
| --- | --- | --- | --- | --- |
| Spring Boot 3.3.x | Java 17+ | 当前已验证 | `nexary-cache-spring-boot-starter` | `nexary-cache-api` + `nexary-cache-redis` |
| Spring Boot 2.7.x | Java 8+ | Redis 单级缓存已验证；不包含 tiered local cache | `nexary-cache-spring-boot2-starter` | `nexary-cache-api` + `nexary-cache-redis-spring-boot2` |
| Spring Boot 4.1.x | Java 21 主验证运行时 | Cache Redis provider/starter 已验证；不是全仓库 Boot4 支持 | `nexary-cache-spring-boot4-starter` | `nexary-cache-api` + `nexary-cache-redis-spring-boot4` |

当前 `nexary-cache-spring-boot-starter` 是 Boot3 / Java17+ 已验证入口。`nexary-cache-spring-boot2-starter` 是 Boot2 / Java8+ 已验证入口，但只覆盖 Redis 单级缓存。`nexary-cache-spring-boot4-starter` 是 Boot4.1 / Java21 主验证运行时下的 Cache Redis provider/starter 入口；这是 Nexary 的验证运行时说明，不是 Spring 官方 JDK 基线说明，也不代表 messaging、job 或整个仓库已经完成 Boot4 支持。

Valkey 是 v0.3 的 Redis 协议兼容部署目标。业务代码仍使用同一套 Nexary Cache API；Spring Boot 3.3 / Java17+ 线使用 `nexary-cache-spring-boot-starter` 或 `nexary-cache-api` + `nexary-cache-redis`，通过 `nexary.cache.provider=valkey` 和 Valkey 连接地址切换。这个路径必须通过 Valkey 容器集成测试后才能作为发布支持声明。

### Starter 模式

适合希望由 Nexary starter 聚合 cache API 与 provider，并通过配置选择底层实现的 Spring Boot 服务。业务代码只使用 `CacheClient`、`CacheCounterClient`、`CacheKey`、`CacheCounterKey` 等 Nexary 抽象。

Spring Boot 3.3.x / Java 17+：

```groovy
def nexaryVersion = "0.5.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
    implementation "com.aweimao:nexary-cache-spring-boot-starter"
}
```

Spring Boot 2.7.x / Java 8+ Redis 单级缓存：

```groovy
dependencies {
    implementation "com.aweimao:nexary-cache-spring-boot2-starter:0.5.0"
}
```

Spring Boot 4.1.x / Java 21 主验证运行时：

```groovy
dependencies {
    implementation "com.aweimao:nexary-cache-spring-boot4-starter:0.5.0"
}
```

```yaml
nexary:
  cache:
    provider: redis
    redis:
      # Redis-only 是生产默认路径；tiered cache 需要显式开启。
      tiered-enabled: false
```

Valkey 使用同一 Redis 协议连接配置，业务代码不变：

```yaml
nexary:
  cache:
    provider: valkey
    redis:
      # 这里仍是 Redis 协议配置前缀，不表示业务代码依赖 Redis 原生 API。
      tiered-enabled: false
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 16380
```

### SPI/provider 依赖模式

适合希望显式控制 provider 依赖的服务。业务代码仍只依赖 Nexary cache API；底层 Redis provider 由运行时依赖和 `nexary.cache.provider` 选择。

Spring Boot 3.3.x / Java 17+：

```groovy
def nexaryVersion = "0.5.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")

    // 业务代码编译期只依赖 Nexary cache API。
    implementation "com.aweimao:nexary-cache-api"

    runtimeOnly "com.aweimao:nexary-cache-redis"
}
```

Spring Boot 2.7.x / Java 8+ Redis 单级缓存：

```groovy
dependencies {
    implementation "com.aweimao:nexary-cache-api:0.5.0"
    runtimeOnly "com.aweimao:nexary-cache-redis-spring-boot2:0.5.0"
}
```

Spring Boot 4.1.x / Java 21 主验证运行时：

```groovy
dependencies {
    implementation "com.aweimao:nexary-cache-api:0.5.0"
    runtimeOnly "com.aweimao:nexary-cache-redis-spring-boot4:0.5.0"
}
```

当前关注：

- `nexary-cache-api`
- `nexary-cache-redis`
- `nexary-cache-redis-spring-boot2`（Boot2 / Java8+ Redis 单级缓存）
- `nexary-cache-redis-spring-boot4`（Boot4.1 / Java21 主验证运行时下的 Redis provider）
- Valkey Redis-protocol deployment target（业务 API 不变；通过 `nexary.cache.provider=valkey` 与连接地址选择）
- Redis tiered cache mode（显式开启；Redis Pub/Sub best-effort 失效；不提供跨节点 L1 强一致）
- Atomic counter API（独立于普通 cache object；Redis 实现走原子 counter 操作）
- Lock fencing token（保持 owner-token lock 兼容；Redis 成功获取锁时发行同资源单调 token）

默认生产路径是 Redis 单级缓存。`nexary.cache.redis.tiered-enabled=false` 时不会创建 JVM-local L1，也不会启动失效 listener。

Valkey 复用 Redis 协议 provider 路径。当前不会新增 `CacheClient`、`CacheCounterClient`、lock API 或一套重复实现；Valkey 的差异在部署目标、连接地址和 `nexary.cache.provider=valkey`。该能力不声明 Redlock、强一致、exactly-once invalidation、事务资源替代或跨 provider 语义增强。

Boot2 / Java8+ 路径只支持 Redis 单级缓存。显式设置 `nexary.cache.redis.tiered-enabled=true` 会快速失败；如果未来要支持 Boot2 tiered cache，需要单独的 Caffeine2-backed 实现和独立验证。

显式开启 tiered mode 后，写入、删除、更新 TTL 会在 Redis L2 mutation 成功后发布失效事件，其他节点收到事件后只删除匹配 key 的本地 L1。该能力是 best-effort：不是强一致，不是 exactly-once，失效不可用或丢失时仍依赖 `local-ttl` 兜底。

计数器不要用普通 `CacheClient#get/put` 建模。需要原子加减时使用 `CacheCounterClient`，Redis provider 直接执行原子 counter 操作，不进入 JVM-local L1。counter TTL 是 TTL-on-create：首次创建时设置，后续加减不刷新。

lock 默认仍是 owner-token 语义：释放和续租只对当前 owner 生效，owner-token 不保护下游写入。Redis provider 成功获取锁时会为同一 lock resource 返回单调递增的 fencing token。调用方可以把 token 写入或传递给受保护资源，由该资源保存已接受最大 token，并拒绝更低 token 的旧操作。该能力不是 Redlock，不声明强一致或完整分布式协调，也不能替代 transactional / linearizable 的受保护资源。

Cache 会通过 `NexaryObservationEvent` 发 Nexary 层 观测事件。默认 publisher 是 no-op，不配置 listener 时不会改变行为。推荐指标名包括：

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
