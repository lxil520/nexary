# 核心概念

## Nexary 解决什么问题

Nexary 不是要替代 Redis、Kafka、RocketMQ 或 Spring，而是提供一层更小的中间件抽象，用来：

- 让业务代码不直接绑定 provider 原生类型
- 统一 cache、messaging、job 的常见模式
- 提前引入一套共享的治理语义

## Cache

- `CacheKey`：namespace + key 的统一键模型
- `CacheClient`：get、put、put-if-absent、batch、expire、tryLock
- `cacheAside(...)`：标准读穿模式辅助方法
- `CacheCounterKey`：计数器专用 key，避免把 counter 当普通对象缓存
- `CacheCounterClient`：increment、decrement、current、clear；实现必须走 provider 原子 counter 能力，不进入 JVM-local L1
- `LockHandle`：owner-token lock handle；Redis provider 成功获取锁时可返回 fencing token
- `fencingToken()`：调用方携带到受保护资源的单调令牌；校验由受保护资源完成，Nexary 不替代业务写入侧的版本检查

## Messaging

- `MessageEnvelope`：topic、key、payload、headers、deadline、traffic tag
- `MessagePublisher`：统一 publish 抽象
- `MessagePublishResult`：统一发送结果
- `MessageInterceptor`：发送前后、消费前后的拦截点

## Job

- `NexaryJob`：任务执行单元
- `JobSchedule`：基于 cron 的调度定义
- `NexaryJobScheduler`：schedule / cancel 抽象
- `JobExecutionListener`：任务执行后的监听回调

## 治理语义

- `DeadlineContext`：避免请求超过有效时间后仍继续执行
- `TrafficTag`：携带 tenant、priority、业务标签
- `RetrySignal`：告诉上游是否继续重试
- `FaultSignal`：把 provider 特定失败归一成统一类别
- `NexaryObservationEvent`：为后续指标、trace、根因分析做统一事件模型
