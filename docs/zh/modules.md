# 模块说明

## Framework

- `nexary-framework/nexary-core`：所有模块共享的基础语义。
- `nexary-framework/nexary-spi`：基于 JDK `ServiceLoader` 的 SPI 注册与组合查询。

## Cache

- `nexary-cache/nexary-cache-api`：统一缓存 API，覆盖 TTL、batch、cache-aside、lock handle、atomic counter 等抽象。
- `nexary-cache/nexary-cache-redis`：Redis 适配实现与 Spring Boot 自动配置。多级缓存模式内部使用 Caffeine 作为 L1，但不会把 Caffeine 暴露成与 Redis 平级的后端。

## Messaging

- `nexary-messaging/nexary-messaging-api`：envelope、publisher、consumer、serializer、interceptor、retry policy、dead-letter、publish result、consume result、重复消费保护等统一抽象。
- `nexary-messaging/nexary-messaging-disruptor`：基于官方 LMAX Disruptor 的进程内 ring-buffer 队列，面向低延迟本地分发。
- `nexary-messaging/nexary-messaging-kafka`：基于 Spring `kafkaTemplate` 的 Kafka 适配层，并提供统一的重复消费防护桥接。
- `nexary-messaging/nexary-messaging-redis`：基于 Redis List 的轻量队列和 Redis 去重存储，默认关闭，按需显式启用。
- `nexary-messaging/nexary-messaging-rocketmq`：基于 Spring `rocketMQTemplate` 的 RocketMQ 适配层。

## Job

- `nexary-job/nexary-job-api`：任务、调度、执行上下文、结果、监听器、执行 ID、执行记录和执行策略 API。
- `nexary-job/nexary-job-scheduler`：本地 `TaskScheduler` 调度器，可选接入 cache 单实例锁、worker heartbeat、分片负载算法和统一执行生命周期。
- `nexary-job/nexary-job-xxljob`：XXL-JOB bridge，把外部触发和分片元数据映射到 `NexaryJob`，并复用统一执行生命周期。
- `nexary-job/nexary-job-powerjob`：PowerJob 触发映射，把外部触发和分片元数据映射到 `NexaryJob`，并复用统一执行生命周期。
- `nexary-job/nexary-job-execution-store-redis`：可选 Redis durable execution store，按 TTL 保存已完成 execution record。

## Boot

- `nexary-boot/nexary-bom`：依赖约束。
- `nexary-boot/nexary-cache-spring-boot-starter`：缓存 starter。
- `nexary-boot/nexary-messaging-spring-boot-starter`：消息 starter。
- `nexary-boot/nexary-job-spring-boot-starter`：任务 starter。
- `nexary-boot/nexary-observation-micrometer-spring-boot-starter`：把 `NexaryObservationEvent` 桥接到 Micrometer 的独立 Spring Boot 集成模块。

## Samples

- `nexary-samples/nexary-sample-cache`：缓存 starter selector 专项样例。
- `nexary-samples/nexary-sample-cache-spi-redis`：缓存 SPI Redis provider 样例。
- `nexary-samples/nexary-sample-messaging`：消息 starter selector 专项样例。
- `nexary-samples/nexary-sample-messaging-spi-*`：消息 SPI provider 样例。
- `nexary-samples/nexary-sample-job`：任务 starter selector 专项样例。
- `nexary-samples/nexary-sample-job-spi-*`：任务 SPI provider 样例。
