# 中间件联调

这个页面只覆盖本仓库当前真正能验证的内容：Redis、Kafka、RocketMQ 的容器化联调，以及 XXL-JOB Admin 的本地依赖准备。

## 先说边界

- `nexary-cache-tiered-internal` 是 Redis 多级缓存的内部实现，不是与 Redis 平级的公开后端。
- Redis、Kafka、RocketMQ 已补到“起容器后可跑 smoke 和 JUnit 联调测试”。
- XXL-JOB 当前仍是 bridge 形态：`nexary-job-xxljob` 负责把外部触发映射到 `NexaryJob`，但还没有 executor 注册和 handler 暴露层。因此现在能验证的是 Admin + MySQL 可启动，不能把它包装成已完成的端到端执行链路。
- `xuxueli/xxl-job-admin` 当前公开标签只有 `linux/amd64`。在 Apple Silicon 上会通过 Docker 的 amd64 仿真运行，不是原生 arm64 镜像。

## 目录

- Compose: `deploy/middleware/docker-compose.yml`
- 环境变量模板: `deploy/middleware/.env.example`
- 启停脚本: `scripts/middleware/up.sh`、`scripts/middleware/down.sh`
- 基础 smoke: `scripts/middleware/smoke.sh`
- JUnit 联调测试: `scripts/middleware/run-integration-tests.sh`

## 启动

```bash
cp deploy/middleware/.env.example deploy/middleware/.env
./scripts/middleware/up.sh
```

## 基础联通验证

```bash
./scripts/middleware/smoke.sh
```

这个脚本会验证：

- Redis `PING`
- Kafka 建 topic、生产、消费
- RocketMQ NameServer/Broker 集群可见
- MySQL 中 `xxl_job` 表已初始化
- XXL-JOB Admin HTTP 入口可访问

## 运行真实 JUnit 联调测试

```bash
./scripts/middleware/run-integration-tests.sh
```

当前会执行：

- `nexary-cache-redis` 对真实 Redis 的 TTL / lock 续租验证
- `nexary-messaging-redis` 对真实 Redis Queue 的发布 / 去重验证
- `nexary-messaging-kafka` 对真实 Kafka 消息头透传与去重桥接验证
- `nexary-messaging-rocketmq` 对真实 RocketMQ 消息头透传与去重桥接验证

## 停止并清理

```bash
./scripts/middleware/down.sh
```
