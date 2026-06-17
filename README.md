# Nexary

<p align="center">
  <img src="docs/assets/nexary-logo.svg" alt="Nexary" width="420">
</p>

英文镜像文档：[README.en.md](README.en.md)

Nexary 是面向 Spring Boot 3.3 的 Java 17 中间件框架。当前 `0.2.x` 聚焦缓存、消息、任务调度、SPI、可观测性桥接，以及后续服务治理能力需要的基础扩展点。

Nexary 是新的对外开源框架，不承诺与任何历史内部实现保持源码或二进制兼容。

## 当前状态

Nexary 仍处于 `1.0.0` 之前阶段。公共 API 会尽量保持小而稳定，具体实现模块在 `1.0.0` 前仍可能继续调整。

当前已验证主线：

- Java 17 编译，Java 21 为主要运行验证目标
- Spring Boot 3.3 主线

这个边界来自 Spring Boot 3 自身的 Java 17+ 要求，不是因为 Nexary 最初用 Java 17 开发。

为了扩大使用人群，Nexary 会规划 Spring Boot 2 / JDK 8 兼容线。Boot 2 是覆盖 Java 8 用户的关键路径，不能简单要求用户升级到 Java 17。但这不能只靠 README 声明，必须通过独立 starter、依赖版本锁定、样例和 CI 验收后再标记为支持。当前文档中的依赖片段只代表已经验收的主线。

## 文档入口

- 文档切换页：[docs/README.md](docs/README.md)
- 中文文档：[docs/zh/index.md](docs/zh/index.md)
- English docs: [docs/en/index.md](docs/en/index.md)

按能力阅读：

- Cache：[docs/zh/cache.md](docs/zh/cache.md)
- Messaging：[docs/zh/messaging.md](docs/zh/messaging.md)
- Job：[docs/zh/job.md](docs/zh/job.md)
- 本地验证：[docs/zh/verification.md](docs/zh/verification.md)

通用说明：

- 架构说明：[docs/zh/architecture.md](docs/zh/architecture.md)
- 编码规范：[docs/zh/standards.md](docs/zh/standards.md)
- 贡献与维护流程：[docs/zh/collaboration.md](docs/zh/collaboration.md)
- 版本路线图：[docs/zh/roadmap.md](docs/zh/roadmap.md)
- 发布清单：[docs/zh/release.md](docs/zh/release.md)

## 模块结构

- `nexary-framework/nexary-core`：deadline、traffic tag、retry、fault、observation 等基础语义
- `nexary-framework/nexary-spi`：基于 `ServiceLoader` 的 SPI 注册与组合查询
- `nexary-cache/nexary-cache-api`：统一缓存 API，覆盖 TTL、batch、cache-aside、分布式锁和 atomic counter 抽象
- `nexary-cache/nexary-cache-redis`：Redis 适配实现与 Spring Boot 自动配置，支持内部 Caffeine L1 的多级缓存模式
- `nexary-messaging/nexary-messaging-api`：统一消息 envelope、publisher、consumer、serializer、retry、dead-letter、interceptor、重复消费保护 API
- `nexary-messaging/nexary-messaging-disruptor`：基于官方 LMAX Disruptor 的进程内 ring-buffer 队列
- `nexary-messaging/nexary-messaging-kafka`：基于 Spring `kafkaTemplate` 的 Kafka 适配层
- `nexary-messaging/nexary-messaging-redis`：基于 Redis List 的轻量队列适配层，默认关闭，按需启用
- `nexary-messaging/nexary-messaging-rocketmq`：基于 Spring `rocketMQTemplate` 的 RocketMQ 适配层
- `nexary-job/nexary-job-api`：任务、调度、执行上下文、执行 ID、执行记录、执行策略和监听器 API
- `nexary-job/nexary-job-scheduler`：本地 `TaskScheduler` 调度器，可选接入 cache 单实例锁、worker topology、分片和执行生命周期
- `nexary-job/nexary-job-xxljob`：XXL-JOB bridge，复用统一执行生命周期
- `nexary-boot/nexary-bom`：依赖约束
- `nexary-boot/nexary-*-spring-boot-starter`：Starter 聚合模块
- `nexary-samples`：按能力拆开的 starter / SPI 参考工程

## 从哪里开始

### 1. 先看能力入口，再决定从哪条线接入

- 只关心缓存：先看 [nexary-cache/README.md](nexary-cache/README.md)
- 只关心消息：先看 [nexary-messaging/README.md](nexary-messaging/README.md)
- 只关心任务：先看 [nexary-job/README.md](nexary-job/README.md)
- 只关心本地验证：先看 [docs/zh/verification.md](docs/zh/verification.md)

### 2. 运行按能力拆开的参考工程

```bash
./gradlew :nexary-samples:nexary-sample-cache:bootRun
./gradlew :nexary-samples:nexary-sample-messaging:bootRun
./gradlew :nexary-samples:nexary-sample-job:bootRun
```

样例说明不再只列接口，而是明确每个样例应该复制什么到业务工程，见 [nexary-samples/README.md](nexary-samples/README.md)。

### 3. 再接入真实中间件

本仓库已经提供本地 Docker 联调脚本，可直接验证 Redis、Kafka、RocketMQ、MySQL、XXL-JOB Admin：

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
./scripts/middleware/run-integration-tests.sh
```

## 在 Spring Boot 项目中接入

当前开发版本：`0.2.0-SNAPSHOT`。正式发布到 Maven Central 后，把下面的 `${nexary.version}` 替换为最新 release 版本。

### 版本矩阵

| Spring Boot | JDK | 状态 | 依赖入口 |
| --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | 当前已验证 | 当前 starter / BOM |
| Spring Boot 2.7 | Java 8+ | 计划支持，兼容审计中 | 独立 Boot2 starter / BOM，未发布 |
| Spring Boot 4.x | Java 17+ | 暂不作为 v0.2 目标 | 后续按官方稳定线评估 |

正式发布前会根据兼容审计结果决定 starter artifact 是否显式带 `spring-boot3` / `spring-boot2` 后缀。未通过验证的组合不会出现在“已支持”依赖片段中。

### Maven

先引入 BOM，再按能力选择 starter：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.nexary</groupId>
      <artifactId>nexary-bom</artifactId>
      <version>${nexary.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-cache-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-messaging-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-job-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.nexary</groupId>
    <artifactId>nexary-observation-micrometer-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

### Gradle

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
    implementation 'org.nexary:nexary-cache-spring-boot-starter'
    implementation 'org.nexary:nexary-messaging-spring-boot-starter'
    implementation 'org.nexary:nexary-job-spring-boot-starter'
    implementation 'org.nexary:nexary-observation-micrometer-spring-boot-starter'
}
```

如果不用 starter，也可以使用 SPI/provider 依赖模式。业务代码仍只依赖 Nexary API，provider 通过运行时依赖和 `nexary.*` 配置选择：

```groovy
dependencies {
    implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
    implementation 'org.nexary:nexary-messaging-api'
    runtimeOnly 'org.nexary:nexary-messaging-kafka'
    // 切换到 RocketMQ 时只调整依赖和配置，不改业务发送/消费代码。
    // runtimeOnly 'org.nexary:nexary-messaging-rocketmq'
}
```

当前建议是每个服务只启用一个出站消息 provider。若同一服务要同时路由 Kafka 和 RocketMQ，由应用层显式封装自己的路由 facade，比在框架层做隐式选择更清晰。

## 发布与版本策略

- 发布到 Maven Central 前，先完成 namespace 校验、签名、SCM 元数据和 sources/javadoc 产物
- 先把 `0.2.x` 主线和发布流程打稳，同时启动 Spring Boot 2 / JDK 8 兼容性差距检查
- Spring Boot 2 / JDK 8 支持会走单独兼容线或 adapter，不污染当前主线 API

详细说明见 [docs/zh/release.md](docs/zh/release.md) 和 [docs/zh/roadmap.md](docs/zh/roadmap.md)。

## 贡献与维护

Nexary 按能力维护：

- Cache、Messaging、Job 先保持清晰边界，各自拥有独立样例、测试和文档。
- 本地验证统一通过 Docker、smoke、integration tests 和 `publishToMavenLocal` 收口。
- 治理能力在范围清楚后再独立成模块，避免过早污染当前主线 API。
- 公开讨论使用 GitHub issue / PR；内部任务记录不作为用户文档入口。

## 开发验证

```bash
./gradlew check
./gradlew publishToMavenLocal
```
