# 样例说明

Nexary 的样例目标不是“把接口跑通”，而是让使用者知道应该复制哪一层代码进入自己的项目。

当前提供按能力拆开的 starter selector 与 SPI provider 参考工程。它们都保持 Spring Boot 工程形态，而不是单文件 demo。

## 样例矩阵

| 模块 | 端口 | 场景 | 你应该带走什么 |
| --- | --- | --- | --- |
| `nexary-sample-cache` | `8081` | 用户资料读写与缓存预热 | 复制 cache-aside、批量取值、失效模式 |
| `nexary-sample-cache-spi-redis` | `8091` | Cache SPI Redis 引入 | 只引入 API + Redis provider |
| `nexary-sample-messaging` | `8082` | 业务事件发布入口 | 复制发送入口和 consumer 结构；通过 `nexary.messaging.provider` 切换 provider |
| `nexary-sample-messaging-spi-disruptor` | `8092` | Messaging SPI Disruptor 引入 | 只引入 API + Disruptor provider |
| `nexary-sample-messaging-spi-redis` | `8093` | Messaging SPI Redis queue 引入 | 只引入 API + Redis provider |
| `nexary-sample-messaging-spi-kafka` | `8094` | Messaging SPI Kafka 引入 | 只引入 API + Kafka provider |
| `nexary-sample-messaging-spi-rocketmq` | `8095` | Messaging SPI RocketMQ 引入 | 只引入 API + RocketMQ provider |
| `nexary-sample-messaging-spi-activemq-classic` | `8098` | Messaging SPI ActiveMQ Classic 引入 | 只引入 API + ActiveMQ Classic provider |
| `nexary-sample-job` | `8083` | 对账/补偿类任务 | 复制 `NexaryJob`、调度注册、执行状态记录 |
| `nexary-sample-job-spi-scheduler` | `8096` | Job SPI local scheduler 引入 | 只引入 API + local scheduler provider |
| `nexary-sample-job-spi-xxljob` | `8097` | Job SPI XXL-JOB bridge 引入 | 只引入 API + XXL-JOB bridge provider |
| `nexary-sample-job-spi-powerjob` | 无固定 HTTP 端口 | Job PowerJob 触发引入 | 只引入 API + PowerJob provider |
| `nexary-sample-governance` | `8080` | 本地治理、熔断、请求取消和只读诊断页面 | 复制稳定资源名、`GovernanceContext`、fallback、本地诊断和协作式取消检查 |
| `nexary-sample-governance-gateway` | `28090` | Spring Cloud Gateway 入口取消通知 | 复制 deadline / cancellation header 传播和 downstream receiver 接法 |

完整说明见 [../../nexary-samples/README.md](../../nexary-samples/README.md)。

## 推荐阅读顺序

1. 先按目标能力选择 cache、messaging、job 或 governance 的 starter selector 样例
2. 再按需要查看对应 SPI provider 样例
3. 最后结合 [integration.md](integration.md) 把中间件 Docker 跑起来做真实联调

## 专项样例

```bash
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
./gradlew :nexary-samples:nexary-sample-governance:run
./gradlew :nexary-samples:nexary-sample-governance-gateway:run
```

它们回答的是“我只想引入某一个能力时，工程应该怎么写”。

## 接口清单

缓存样例：

- `GET /examples/cache/profiles/{id}`
- `POST /examples/cache/warmup`
- `GET /examples/cache/batch?ids=101,102`
- `DELETE /examples/cache/profiles/{id}`

消息样例：

- `POST /examples/messages`
- `POST /examples/messages/replay/{messageId}`
- `GET /examples/messages`
- `GET /examples/messages/provider`

任务样例：

- `POST /examples/jobs/run-once`
- `POST /examples/jobs/schedule`
- `GET /examples/jobs/state`

治理样例：

- `GET /governance/profiles/{id}`
- `GET /governance/degraded/{id}`
- `GET /governance/cancellation/slow/{id}?durationMillis=3000`
- `POST /governance/circuit/reset`
- `GET /governance/circuit/profiles/{id}?mode=success|failure|slow`
- `GET /nexary/governance/summary`
- `GET /nexary/governance/resources`
- `GET /nexary/governance/events`
- `GET /nexary/console`

Gateway 取消样例：

- `GET /gateway/governance/cancellation/slow/{id}?durationMillis=5000`

直接验证 downstream 样例的取消诊断：

```bash
NEXARY_GOVERNANCE_CANCELLATION_BASE_URL=http://localhost:8080 \
  ./scripts/governance-cancellation/smoke.sh
```

## 当前样例边界

- 缓存、消息、任务专项样例更接近可复制结构
- governance 样例只验证当前 JVM 的本地状态和请求取消，不替代 Sentinel，也不提供远程策略下发
- Gateway 样例只覆盖 Spring Boot 3.3 主线；Boot2 / Boot4 Gateway 样例要等对应版本通过后再写入支持矩阵
- 消息样例的主路径是 starter selector：业务代码不直接引入 Kafka/RocketMQ/Redis/Disruptor 原生 SDK
- 真实中间件联调依赖 `scripts/middleware/*` 和对应集成测试，而不是只看 controller 返回值

## 下一步样例演进方向

- 增加更完整的业务型 sample，而不是继续堆更多 toy endpoint
- 消息样例继续补充更细的 broker 验收脚本和故障场景
- 为任务样例补本地调度与 XXL-JOB bridge 的并列示例
- v0.11.1 / v0.11.2 分别补 Boot2 / Boot4 Gateway 样例和 gate，通过后再更新 README 支持矩阵
