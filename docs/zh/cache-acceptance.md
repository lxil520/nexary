# Cache 验收清单

- API 不暴露 Redis 或 Caffeine 原生类型
- 覆盖 TTL、batch、cache-aside、分布式锁抽象
- Redis-only 是默认路径；`tiered-enabled=false` 时不创建 JVM-local L1，也不启动失效 listener
- Redis tiered mode 边界清楚，Caffeine 只作为内部 L1；显式开启后通过 Redis Pub/Sub 做 best-effort L1 失效
- `put` / `delete` / `expire` 只在 Redis L2 mutation 成功后发布失效事件，失败时不发布
- 收到失效事件只删除匹配 L1 key，不误清其他 key；自节点事件不会删除刚写入的新本地值
- 必须说明 Pub/Sub 失效不是强一致或 exactly-once，丢失时以 local TTL 兜底
- 必须说明强新鲜数据不适合 JVM-local L1
- 计数器必须使用独立 counter API，不能通过普通 `CacheClient#get/put` 实现
- Redis counter 实现必须使用原子 counter 操作，不能进入 `TieredCacheClient` 或 JVM-local L1
- counter TTL 语义必须明确并测试：TTL 只在首次创建 counter 时设置，后续加减不刷新 TTL
- owner-token lock regression 必须通过，unlock 仍然只能释放当前 owner
- 如果实现 fencing token，public API 必须 provider-neutral，Redis token 必须对同一 lock resource 单调递增
- 文档必须说明 fencing token 的获取、携带、受保护资源侧校验方式，并明确不声明 Redlock 或强分布式协调
- cache 专项样例能说明接入方式
- 文档与样例不把 showcase 当主验证面
- 需要真实验证时，能通过本地验证命令完成 Redis 联调和回归
