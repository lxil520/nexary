# AGENTS.md

## 目标校准
- 默认目标是交付 Nexary v0.1 中间件框架，不是延续旧项目命名或旧业务封装。
- 遇到需求、方案或实现选择时，先校准真实目标、成功标准、约束和风险；方向错误时直接指出并调整。
- v1 只做 Java 中间件能力和治理扩展点，不做 sidecar、agent、控制面、管理后台、自动封禁平台、混沌平台。

## 项目边界
- 项目名使用 Nexary，仓库名使用 nexary，包名只允许 org.nexary.*。
- 配置前缀统一为 nexary.*。
- 禁止新增旧项目名、个人名、公司名、内部业务词、内网地址、真实 token、真实密码。
- 旧项目中的明文凭证视为泄露，不能复制到新文件；需要在外部系统轮换。

## 工程规范
- Java 最低基线为 17，主验收环境为 Java 21 + Spring Boot 3.3。
- 使用按能力归属的 Gradle 多模块：nexary-framework、nexary-cache、nexary-messaging、nexary-job、nexary-boot、nexary-samples。
- 模块依赖必须单向：API 不依赖实现；starter 只聚合依赖和自动配置；samples 只作为使用示例。
- 公共 API 小而稳定，不暴露 Redis、Kafka、RocketMQ 原生类型。
- SPI 使用 ServiceLoader + Spring bridge，不复制旧项目复杂 loader。
- 用户可见说明文档默认提供中英文两份；中文放 `docs/zh`，英文放 `docs/en`，并提供明确的路径切换入口。
- 新增或明显改写的用户文档时，中文和英文版本必须一起补齐；不能只更新单一语言版本。

## 编码规范
- public API 必须写 Javadoc。
- 时间类型统一用 java.time.Duration 或 Instant，不用裸 long 表达超时和 TTL。
- 状态值统一用 enum，不使用魔法字符串。
- 配置统一用类型安全 @ConfigurationProperties。
- 失败语义通过 FaultSignal、RetrySignal、NexaryObservationEvent 等抽象表达。
- 示例配置只能使用 local/demo/fake 值。
- 不为了兼容旧 API 保留混乱命名；如需迁移兼容，另开 adapter 模块并明确标注。

## 测试与验收
- 至少保证 ./gradlew check 能跑通基础编译和单元测试。
- Cache 覆盖 TTL、批量操作、cache-aside、分布式锁抽象。
- MQ 覆盖发送、消费、序列化、重试、失败回调、拦截器抽象。
- Job 覆盖本地调度、单实例执行、分片、监听、失败处理抽象。
- README 应让新用户 10 分钟内跑通 cache、mq、job 示例。
