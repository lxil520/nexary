# Nexary Samples

这组样例的目标是给出可复制的工程骨架，而不是只做几个能返回 200 的 controller。

## 结构

- `nexary-sample-cache`：缓存 starter selector 专项参考工程
- `nexary-sample-cache-spi-redis`：缓存 SPI Redis provider 参考工程
- `nexary-sample-messaging`：消息 starter selector 专项参考工程
- `nexary-sample-messaging-spi-disruptor`：消息 SPI Disruptor provider 参考工程
- `nexary-sample-messaging-spi-redis`：消息 SPI Redis queue provider 参考工程
- `nexary-sample-messaging-spi-kafka`：消息 SPI Kafka provider 参考工程
- `nexary-sample-messaging-spi-rocketmq`：消息 SPI RocketMQ provider 参考工程
- `nexary-sample-messaging-spi-activemq-classic`：消息 SPI ActiveMQ Classic provider 参考工程
- `nexary-sample-job`：任务 starter selector 专项参考工程
- `nexary-sample-job-spi-scheduler`：任务 SPI local scheduler provider 参考工程
- `nexary-sample-job-spi-xxljob`：任务 SPI XXL-JOB bridge provider 参考工程
- `nexary-sample-governance`：治理 runtime 参考工程，演示本地限流、并发隔离和降级 fallback

## 使用顺序

1. 先按目标能力选择 cache、messaging、job 或 governance 的 starter selector 样例
2. 如果你需要精确控制 provider 依赖，再看对应 SPI provider 样例
3. 再结合仓库根目录的 `scripts/middleware/*` 做真实中间件联调

## 你应该怎么看这些样例

- 看 controller 和配置类的边界，不要只看 endpoint
- 看 envelope、cache key、job name 这类领域约束怎么表达
- 看 starter selector 和 不用 starter 的依赖方式 两种引入方式的区别
- 不要再依赖聚合 综合演示；每个 capability 的独立样例才是可复制入口

## 不是最终答案的地方

- 这些样例还不是完整业务系统
- 目前重点是公共 API 和 provider 适配层
- 更完整的业务型 sample 会按路线图逐步补齐，而不是一次性把仓库做成大而全模板站
