# nexary-sample-cache-spi-redis

Cache 不用 starter 的依赖方式 引入方式样例。它展示业务代码只编译依赖 `nexary-cache-api`，具体 provider 通过单独依赖引入。

这个模块不依赖 `nexary-cache-spring-boot-starter`，不包含 provider 接线类，不手写 RedisTemplate。

## 引入方式

当前可运行样例使用 Spring Boot 3.3.x + Java 17+ 线。它不使用 starter，而是展示 API + Redis provider 依赖模式；下表给出 Cache 能力已经验证的 provider 坐标。

| Spring Boot | JDK | 状态 | API / provider 依赖 |
| --- | --- | --- | --- |
| Spring Boot 3.3.x | Java 17+ | 当前已验证 | `nexary-cache-api` + `nexary-cache-redis` |
| Spring Boot 2.7.x | Java 8+ | Redis 单级缓存已验证；不包含 tiered local cache | `nexary-cache-api` + `nexary-cache-redis-spring-boot2` |
| Spring Boot 4.1.x | Java 21 主验证运行时 | Cache Redis provider 已验证；不是全仓库 Boot4 支持 | `nexary-cache-api` + `nexary-cache-redis-spring-boot4` |

当前开发版本使用 `0.3.0`。发布到 Maven Central 后，把版本替换为最新 release / tag。这里的 Boot4/JDK21 表述只代表 Nexary Cache 的主验证运行时，不是 Spring 官方 JDK 基线说明，也不代表 messaging、job 或整个仓库已经完成 Boot4 支持。

Valkey 是 v0.3 的 Redis 协议兼容部署目标。这个样例不新增 Valkey 专属业务代码；仍使用 `nexary-cache-api` + `nexary-cache-redis`，通过 `NEXARY_SAMPLE_CACHE_PROVIDER=valkey` 与 Valkey 端口切换。

`build.gradle`：

```groovy
// 当前已验证：Spring Boot 3.3.x + Java 17+
implementation project(':nexary-cache:nexary-cache-api')
runtimeOnly project(':nexary-cache:nexary-cache-redis')
```

外部 Spring Boot 3.3.x / Java 17+ 服务复制：

```groovy
def nexaryVersion = "0.3.0"

dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
    implementation "org.nexary:nexary-cache-api"
    runtimeOnly "org.nexary:nexary-cache-redis"
}
```

外部 Spring Boot 2.7.x / Java 8+ Redis 单级缓存服务复制：

```groovy
dependencies {
    implementation "org.nexary:nexary-cache-api:0.3.0"
    runtimeOnly "org.nexary:nexary-cache-redis-spring-boot2:0.3.0"
}
```

外部 Spring Boot 4.1.x / Java 21 主验证运行时服务复制：

```groovy
dependencies {
    implementation "org.nexary:nexary-cache-api:0.3.0"
    runtimeOnly "org.nexary:nexary-cache-redis-spring-boot4:0.3.0"
}
```

切换 provider 时，调整 provider dependency 和 `nexary.cache.provider` 配置，不改 controller/service 业务代码。

## 配置

`application.yml`：

```yaml
nexary:
  cache:
    provider: ${NEXARY_SAMPLE_CACHE_PROVIDER:redis}
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

本地 Redis 默认连接 `127.0.0.1:16379`，可用 `NEXARY_SAMPLE_REDIS_HOST` 和 `NEXARY_SAMPLE_REDIS_PORT` 覆盖。

本地 Valkey 来自同一个 middleware stack，默认端口是 `16380`。业务代码不变，按下面方式切换：

```bash
NEXARY_SAMPLE_CACHE_PROVIDER=valkey \
NEXARY_SAMPLE_REDIS_PORT=16380 \
./gradlew :nexary-samples:nexary-sample-cache-spi-redis:run
```

这里仍使用 Redis 协议 provider dependency 和连接配置；它不要求业务代码导入 Redis、Valkey、Lettuce 或 Spring Data Redis 原生类型。

## 运行

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
./gradlew :nexary-samples:nexary-sample-cache-spi-redis:run
```

试用接口：

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

## 计数器边界

`UserCount` 使用 `CacheCounterClient`，不是 `CacheClient#get/put`。counter 是 Redis 原子计数器，不是普通 cache object，不进入 JVM-local L1，也不受 tiered cache 的本地缓存影响。

counter TTL 是 TTL-on-create：第一次 `increment/decrement` 创建 counter 时可以设置过期时间，后续加减不会刷新 TTL。这个样例只演示单 key 原子计数，不包含多 key 事务、配额系统、fencing token 或 version-checked counter。

## 锁与 fencing token 边界

`POST /examples/cache/locks/{id}` 展示 owner-token lock 的获取、续租、安全释放，并在 provider 支持时返回 `fencingToken`。owner-token 只保护 lock 自身的 release/renew，不保护下游写入。Redis provider 成功获取锁时会为同一 lock resource 发行单调递增的 fencing token。

业务调用方应把 fencing token 传给真正被保护的资源，由该资源保存已接受的最大 token 并拒绝更小 token 的旧写入。这个样例不实现 Redlock，不声明强一致，也不能替代 transactional / linearizable 的受保护资源。没有 fencing token 的 provider 仍可只返回 owner-token lock。

## 多级缓存边界

`tiered-enabled` 默认保持 `false`，此时读写都走 Redis-backed `CacheClient`，不会创建 JVM-local L1，也不会启动失效 listener。这是生产默认路径，更适合需要跨节点读取新值的业务。

如果显式设置 `tiered-enabled: true`，Nexary 会增加 JVM 本地 L1 缓存来提升读性能，并默认通过 Redis Pub/Sub 做 best-effort 跨节点 L1 失效。A 节点写入、删除或更新 TTL 时，只有 Redis L2 mutation 成功后才会发布失效事件；B 节点收到事件后只删除匹配 key 的本地 L1，下一次读取会重新走 Redis L2。

这个失效能力不是强一致、不是 exactly-once，也没有 fencing-token 或 version-checked read。Pub/Sub 不可用、事件丢失或节点短暂离线时，B 节点仍可能在 `local-ttl` 内读到旧值。因此 tiered mode 只适合 read-mostly、短 TTL、允许短暂陈旧的数据；计数、余额、库存、权限、订单状态等强新鲜数据应保持 Redis-only 或使用专门的一致性模型。

## 观测边界

业务代码不需要为了指标修改 controller/service。Nexary cache 实现会发 `NexaryObservationEvent`，默认 no-op；真实服务需要接入指标系统时，可以在应用层注册 `NexaryObservationListener`，把事件转成自己的 metrics。

推荐指标名：`nexary.cache.operation.duration`、`nexary.cache.operation.count`、`nexary.cache.hit.count`、`nexary.cache.miss.count`、`nexary.cache.invalidation.count`、`nexary.cache.lock.count`、`nexary.cache.counter.count`。

允许的 tag 只有有界字段：`capability`、`operation`、`provider`、`tier`、`outcome`、`failure`。不要把 cache key、业务 id、lock token、fencing token、异常消息或 stack trace 放进 metrics tag。

## 本地验证命令

```bash
./gradlew \
  -DNEXARY_RUN_INFRA_TESTS=true \
  -DNEXARY_INFRA_REDIS_HOST=127.0.0.1 \
  -DNEXARY_INFRA_REDIS_PORT=16379 \
  :nexary-samples:nexary-sample-cache-spi-redis:test
```

预期证据：

- `api` / `service` / `common` 只依赖 Nexary cache API 和 DTO。
- 业务代码不 import Redis、Spring Data Redis、Caffeine 或 provider implementation。
- Redis provider 由 runtime dependency 和 `nexary.cache.provider=redis` 配置加载，业务接口不暴露 provider 诊断。
