# Nexary

<p align="center">
  <img src="docs/assets/nexary-social-preview.png" alt="Nexary" width="720">
</p>

英文镜像文档：[README.en.md](README.en.md)

[![build](https://github.com/lxil520/nexary/actions/workflows/build.yml/badge.svg)](https://github.com/lxil520/nexary/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/lxil520/nexary)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B%20%7C%2021-007396)](README.md)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%20mainline-6DB33F)](README.md)

**让业务专注业务，让中间件保持可替换。**

Nexary 在业务代码和中间件 SDK 之间加一层很薄的 Java API。业务服务调用 `CacheClient`、`MessagePublisher`、`NexaryJob` 这些稳定入口；Redis、Valkey、Kafka、RocketMQ、ActiveMQ Classic、XXL-JOB、PowerJob 的接线留在框架模块里。后面中间件升级、替换或绕开瓶颈时，改动尽量收敛在依赖和配置上，不把业务系统从头改到尾。

当前 `0.19.0` 以 Spring Boot 3.3 / Java 17+ 为主线，已验证能力包括缓存、消息、任务调度、可观测性桥接、本地治理运行时、只读治理诊断 Console、Boot3 主线的 Sentinel provider、停止重试传播、按固定流量类型和优先级做隔离、异常实例候选识别，以及当前 JVM 内的本地故障定位 trace。平台方向已包含资产模型、资源/信号上报 API、服务/依赖拓扑查询、Postgres 存储边界、Console Platform Mode，以及 v0.18 的事故证据链：同一服务、集群、机房内的慢调用、错误率、Sentinel block、Gateway 断开、停止重试和异常实例信号会聚合成可读的事故候选。`0.19.0` 把 Platform Mode 从计数卡片页重设计为运维工作台，第一屏优先展示事故队列、拓扑影响、服务健康和证据链。Spring Boot 2.7 / Java 8+ 与 Spring Boot 4.1 / Java 21 仍按模块提供已验证入口。

`0.19.0` 不是 Sentinel、Spring Cloud Gateway、SkyWalking、Prometheus、企业 IM 或 OpenTelemetry 后端的替代品，也不修改外部工具规则。它把 Nexary SDK 资源、治理信号、服务组、集群、机房、中间件依赖和只读连接器状态放到同一个平台视图里，并让事故候选带上首要资源、影响资源数、证据时间线和建议检查项。Local Console 仍用于单 JVM 调试；Platform Mode 用于看汇聚后的服务、依赖、事故队列、拓扑影响和证据入口。payload、URL query、userId、tenant、messageId、cache key、异常全文、stack trace、token 和密码都不能进入平台信号。

## 什么情况下值得用

- 正在维护多个 Spring Boot 服务，希望缓存、消息和任务调度不要把业务代码绑死在某个中间件 SDK 上的团队。
- 需要在 Redis、Kafka、RocketMQ、XXL-JOB 等基础设施之间保留替换空间，但又不想在应用层复制一套复杂平台的开发者。
- 想要一套能直接复制依赖、配置和样例的 Java 中间件接入层，而不是只看到一堆接口定义。

## 10 分钟体验路径

```bash
git clone https://github.com/lxil520/nexary.git
cd nexary
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
```

如果你只想看真实中间件联调，直接运行：

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
./scripts/middleware/run-integration-tests.sh
```

如果你想先看本地治理 Console：

```bash
./scripts/console/up.sh
./scripts/console/smoke.sh
open http://127.0.0.1:18090/nexary/console
```

如果你想看 Boot3 Sentinel provider：

```bash
./gradlew :nexary-samples:nexary-sample-governance-sentinel:run
NEXARY_GOVERNANCE_SENTINEL_BASE_URL=http://localhost:8080 ./scripts/governance-sentinel/smoke.sh
open http://localhost:8080/nexary/console
```

如果你想看治理平台只读视图：

```bash
./gradlew :nexary-samples:nexary-sample-governance-platform:bootRun
open http://localhost:18092/nexary/console/platform
curl -s http://localhost:18092/api/platform/topology
curl -s http://localhost:18092/api/platform/incidents
```

如果你想看异常实例候选识别：

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--spring.profiles.active=instance-health'
NEXARY_GOVERNANCE_INSTANCE_HEALTH_BASE_URL=http://localhost:8080 ./scripts/governance-instance-health/smoke.sh
open http://localhost:8080/nexary/console
```

如果你想看本地故障定位 trace：

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--spring.profiles.active=trace'
NEXARY_GOVERNANCE_TRACE_BASE_URL=http://localhost:8080 ./scripts/governance-trace/smoke.sh
open http://localhost:8080/nexary/console/traces
```

## 当前状态

Nexary 仍处于 `1.0.0` 之前阶段。公共 API 会尽量保持小而稳定，具体实现模块在 `1.0.0` 前仍可能继续调整。

当前已验证主线：

- Spring Boot 3.3 / Java 17+ 主线，覆盖 Cache、Messaging、Job、observation bridge、本地治理、只读 Console 和 Sentinel provider
- Spring Boot 2.7 / Java 8+ 的 Cache Redis 单级缓存入口，覆盖 `nexary-cache-spring-boot2-starter`
- Spring Boot 2.7 / Java 8+ 的 Messaging Redis-only 入口，覆盖 `nexary-messaging-spring-boot2-starter`
- Spring Boot 2.7 / Java 8+ 的 Job 入口，覆盖 `nexary-job-spring-boot2-starter`
- Spring Boot 4.1 / Java 21 主验证运行时的 Cache Redis 入口，覆盖 `nexary-cache-spring-boot4-starter`
- Spring Boot 4.1 / Java 21 主验证运行时的 Messaging 单 provider 入口，覆盖 `nexary-messaging-spring-boot4-starter` 加一个 Boot4 provider artifact
- Spring Boot 4.1 / Java 21 主验证运行时的 Job 受限边界入口，覆盖 `nexary-job-spring-boot4-starter`

这个边界来自 Spring Boot 3 自身的 Java 17+ 要求，不是因为 Nexary 最初用 Java 17 开发。

为了扩大使用人群，Nexary 把 Spring Boot 2.7 / Java 8+ 和 Spring Boot 4.1 / Java 21 纳入 `0.9.x` 验证目标。README 只给已经通过样例和测试的依赖片段；没有通过验证的组合不会写成“支持”。Java 21 是 Nexary 对 Boot4 线的主验证运行时，不代表 Spring Boot 4 的官方最低 JDK；官方最低 JDK 仍以 Spring 官方文档为准。

## 文档入口

- 文档切换页：[docs/README.md](docs/README.md)
- 中文文档：[docs/zh/index.md](docs/zh/index.md)
- English docs: [docs/en/index.md](docs/en/index.md)

按能力阅读：

- Cache：[docs/zh/cache.md](docs/zh/cache.md)
- Messaging：[docs/zh/messaging.md](docs/zh/messaging.md)
- Job：[docs/zh/job.md](docs/zh/job.md)
- Governance：[docs/zh/governance.md](docs/zh/governance.md)
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
- `nexary-messaging/nexary-messaging-activemq-classic`：ActiveMQ Classic queue 适配层，业务代码不接触 JMS 类型
- `nexary-job/nexary-job-api`：任务、调度、执行上下文、执行 ID、执行记录、执行策略和监听器 API
- `nexary-job/nexary-job-scheduler`：本地 `TaskScheduler` 调度器，可选接入 cache 单实例锁、worker topology、分片和执行生命周期
- `nexary-job/nexary-job-xxljob`：XXL-JOB bridge，复用统一执行生命周期
- `nexary-job/nexary-job-powerjob`：PowerJob bridge，复用统一执行生命周期
- `nexary-governance/nexary-governance-runtime`：deadline、traffic、rate limit、bulkhead、degrade、retry-stop、priority isolation、instance health 和本地 fault trace 语义
- `nexary-boot/nexary-bom`：依赖约束
- `nexary-boot/nexary-*-spring-boot-starter`：Starter 聚合模块
- `nexary-samples`：按能力拆开的 starter / SPI 参考工程

## 从哪里开始

### 1. 先看模块入口，再决定从哪条线接入

- 只关心缓存：先看 [nexary-cache/README.md](nexary-cache/README.md)
- 只关心消息：先看 [nexary-messaging/README.md](nexary-messaging/README.md)
- 只关心任务：先看 [nexary-job/README.md](nexary-job/README.md)
- 只关心本地验证：先看 [docs/zh/verification.md](docs/zh/verification.md)

### 2. 运行按能力拆开的参考工程

```bash
./gradlew :nexary-samples:nexary-sample-cache:run
./gradlew :nexary-samples:nexary-sample-messaging:run
./gradlew :nexary-samples:nexary-sample-job:run
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

### 1. 先选择 Nexary 版本

当前源码版本是 `0.19.0`。如果 Maven Central 还没有同步到这个版本，可以先从 GitHub tag `v0.19.0` 构建并安装到本地：

```bash
./gradlew publishToMavenLocal
```

正式发布到 Maven Central 后，用户可以按两种方式选择版本：

- 使用 Maven Central 显示的 Latest Version。
- 使用 GitHub Releases / Tags 中的版本号，例如 `v0.19.0` 对应依赖版本 `0.19.0`。

不要把 `main` 分支提交号当作生产依赖版本。

### 2. 再按 Spring Boot / JDK 选择入口

| Spring Boot | JDK | 状态 | 版本选择 | BOM | Starter artifactId |
| --- | --- | --- | --- | --- | --- |
| Spring Boot 3.3 | Java 17+ | 当前已验证 | `0.19.0`；Maven Central 同步后也可用 Latest Version | `nexary-bom` | `nexary-cache-spring-boot-starter`<br>`nexary-messaging-spring-boot-starter`<br>`nexary-job-spring-boot-starter`<br>`nexary-observation-micrometer-spring-boot-starter`<br>`nexary-governance-spring-boot-starter`<br>`nexary-governance-gateway-spring-boot-starter`<br>`nexary-governance-sentinel-spring-boot-starter` |
| Spring Boot 2.7 | Java 8+ | Cache Redis 单级缓存、Messaging Redis-only、Job local/XXL-JOB/PowerJob bridge、Gateway cancellation 已验证 | `0.19.0`；Maven Central 同步后也可用 Latest Version | 当前入口使用直接版本；专用 BOM 另行发布时再切换 | `nexary-cache-spring-boot2-starter`<br>`nexary-messaging-spring-boot2-starter`<br>`nexary-job-spring-boot2-starter`<br>`nexary-governance-gateway-spring-boot2-starter` |
| Spring Boot 4.1 | Java 21 主验证运行时 | Cache Redis、Messaging 按 provider、Job local/XXL-JOB/PowerJob bridge 已验证；不是全仓库 Boot4 支持 | `0.19.0`；Maven Central 同步后也可用 Latest Version | 当前入口使用直接版本；专用 BOM 另行发布时再切换 | `nexary-cache-spring-boot4-starter`<br>`nexary-messaging-spring-boot4-starter` 加一个 Boot4 provider artifact<br>`nexary-job-spring-boot4-starter` |

只有“已验证”的 artifactId 可以直接复制下面的依赖片段。生产项目不要依赖 `main` 分支快照。

### 3. Spring Boot 3.3 / Java 17+：Maven

先引入 BOM，再按能力选择 starter：

```xml
<properties>
  <!-- Maven Central 同步后也可以替换为 Latest Version。 -->
  <nexary.version>0.19.0</nexary.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.aweimao</groupId>
      <artifactId>nexary-bom</artifactId>
      <version>${nexary.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- 缓存能力：CacheClient、分布式锁、atomic counter、Redis provider 自动配置。 -->
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-cache-spring-boot-starter</artifactId>
  </dependency>
  <!-- 消息能力：统一 publisher/consumer API，provider 通过配置选择。 -->
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-messaging-spring-boot-starter</artifactId>
  </dependency>
  <!-- 任务能力：统一 job API、本地 scheduler、XXL-JOB bridge 扩展点。 -->
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-job-spring-boot-starter</artifactId>
  </dependency>
  <!-- 可选：Micrometer observation bridge。 -->
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-observation-micrometer-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

### 4. Spring Boot 3.3 / Java 17+：Gradle

```groovy
// Maven Central 同步后也可以替换为 Latest Version。
def nexaryVersion = "0.19.0"

dependencies {
    // 使用 BOM 锁定 Nexary 各模块版本；正式发布后把 nexaryVersion 设置为 Latest Version 或 tag 版本。
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")

    // Starter 模式：按能力引入，业务代码只依赖 Nexary API，不直接依赖底层中间件 SDK。
    implementation 'com.aweimao:nexary-cache-spring-boot-starter'
    implementation 'com.aweimao:nexary-messaging-spring-boot-starter'
    implementation 'com.aweimao:nexary-job-spring-boot-starter'

    // 可选：把 Nexary observation event 接到 Micrometer。
    implementation 'com.aweimao:nexary-observation-micrometer-spring-boot-starter'
}
```

### 5. Spring Boot 3.3 / Java 17+：可选接入 Sentinel provider

如果你已有 Sentinel 使用经验，或者想让 Nexary 治理资源交给 Sentinel 执行限流、并发隔离和熔断判断，可以额外引入：

```groovy
dependencies {
    implementation 'com.aweimao:nexary-governance-spring-boot-starter'
    implementation 'com.aweimao:nexary-governance-sentinel-spring-boot-starter'
    implementation 'com.aweimao:nexary-console-spring-boot-starter'
}
```

再显式选择 Sentinel：

```yaml
nexary:
  governance:
    provider: sentinel
    sentinel:
      enabled: true
      transport:
        enabled: false
    diagnostics:
      enabled: true
  console:
    enabled: true
```

本地样例：

```bash
./gradlew :nexary-samples:nexary-sample-governance-sentinel:run
NEXARY_GOVERNANCE_SENTINEL_BASE_URL=http://localhost:8080 ./scripts/governance-sentinel/smoke.sh
```

这个 starter 默认不启动 Sentinel transport，也不需要 Sentinel Dashboard。Dashboard server 只在你显式打开 `nexary.governance.sentinel.transport.enabled=true` 并配置地址时才使用。

### 6. Spring Boot 2.7 / Java 8+：Cache Redis 单级缓存

当前 Boot2 只验证了 Cache Redis 单级缓存入口。它不包含 tiered local cache；如果显式设置 `nexary.cache.redis.tiered-enabled=true`，Boot2 starter 会快速失败并提示该路径尚未支持。

Maven：

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-cache-spring-boot2-starter</artifactId>
    <version>0.19.0</version>
  </dependency>
</dependencies>
```

Gradle：

```groovy
dependencies {
    implementation 'com.aweimao:nexary-cache-spring-boot2-starter:0.19.0'
}
```

推荐配置：

```yaml
nexary:
  cache:
    redis:
      tiered-enabled: false
```

### 7. Spring Boot 2.7 / Java 8+：Messaging Redis-only

当前 Boot2 Messaging 只验证了 Redis-only provider/starter。Disruptor、Kafka、RocketMQ、ActiveMQ Classic 的 Boot2/JDK8 入口仍待独立验证。

Maven：

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-messaging-spring-boot2-starter</artifactId>
    <version>0.19.0</version>
  </dependency>
</dependencies>
```

Gradle：

```groovy
dependencies {
    implementation 'com.aweimao:nexary-messaging-spring-boot2-starter:0.19.0'
}
```

推荐配置：

```yaml
nexary:
  messaging:
    provider: redis
    redis:
      enabled: true
```

### 8. Spring Boot 2.7 / Java 8+：Job

当前 Boot2 Job 验证了 Job API、本地 scheduler、XXL-JOB 触发映射、PowerJob 触发映射，以及可选 Redis completed-record execution store。这个入口不声明真实 XXL-JOB / PowerJob 平台调度、worker/executor 注册生命周期、完整回调流程、平台托管触发、分布式调度控制面、exactly-once 或运行中强取消。

Maven：

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-job-spring-boot2-starter</artifactId>
    <version>0.19.0</version>
  </dependency>
</dependencies>
```

Gradle：

```groovy
dependencies {
    implementation 'com.aweimao:nexary-job-spring-boot2-starter:0.19.0'
}
```

可选 Redis completed-record store：

```yaml
nexary:
  job:
    execution:
      store:
        redis:
          enabled: true
          retention: 1d
```

### 9. Spring Boot 2.7 / Java 8+：Gateway cancellation

这个入口只做 Spring Cloud Gateway 的请求失效传播：写入 deadline / cancellation header，并在客户端断开时通知下游 cancellation receiver。它不提供远程控制台、策略下发或跨实例状态同步。

Maven：

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-governance-gateway-spring-boot2-starter</artifactId>
    <version>0.19.0</version>
  </dependency>
</dependencies>
```

Gradle：

```groovy
dependencies {
    implementation 'com.aweimao:nexary-governance-gateway-spring-boot2-starter:0.19.0'
}
```

Gateway 样例：

```bash
./gradlew :nexary-samples:nexary-sample-governance-gateway-boot2:run
```

### 10. Spring Boot 4.1 / Java 21：Cache

Boot4 Cache 当前验证 Redis provider/starter。这里不声明整个仓库都已完成 Boot4 支持，也不声明 Java 21 是 Spring Boot 4 官方最低 JDK。

```groovy
dependencies {
    implementation 'com.aweimao:nexary-cache-spring-boot4-starter:0.19.0'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-cache-spring-boot4-starter</artifactId>
    <version>0.19.0</version>
  </dependency>
</dependencies>
```

### 11. Spring Boot 4.1 / Java 21：Messaging

Boot4 Messaging 不提供“聚合所有 provider”的 starter。先引入 Nexary 层 starter，再选择恰好一个 Boot4 provider artifact。下面是 Redis provider 示例，可直接复制使用。

```groovy
dependencies {
    implementation 'com.aweimao:nexary-messaging-spring-boot4-starter:0.19.0'
    runtimeOnly 'com.aweimao:nexary-messaging-redis-spring-boot4:0.19.0'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-messaging-spring-boot4-starter</artifactId>
    <version>0.19.0</version>
  </dependency>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-messaging-redis-spring-boot4</artifactId>
    <version>0.19.0</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

Boot4 Messaging provider 选择：

| Provider | Gradle runtime artifactId | Maven runtime artifactId |
| --- | --- | --- |
| Disruptor | `nexary-messaging-disruptor-spring-boot4` | `nexary-messaging-disruptor-spring-boot4` |
| Redis | `nexary-messaging-redis-spring-boot4` | `nexary-messaging-redis-spring-boot4` |
| Kafka | `nexary-messaging-kafka-spring-boot4` | `nexary-messaging-kafka-spring-boot4` |
| RocketMQ | `nexary-messaging-rocketmq-spring-boot4` | `nexary-messaging-rocketmq-spring-boot4` |

### 12. Spring Boot 4.1 / Java 21：Job

Boot4 Job 当前验证 Job API、本地 scheduler、XXL-JOB 触发映射、PowerJob 触发映射，以及可选 Redis completed-record execution store。不声明真实 XXL-JOB / PowerJob 平台调度、worker/executor 注册生命周期、完整回调流程、平台托管触发、分布式调度控制面、exactly-once 或运行中强取消。

```groovy
dependencies {
    implementation 'com.aweimao:nexary-job-spring-boot4-starter:0.19.0'
}
```

```xml
<dependencies>
  <dependency>
    <groupId>com.aweimao</groupId>
    <artifactId>nexary-job-spring-boot4-starter</artifactId>
    <version>0.19.0</version>
  </dependency>
</dependencies>
```

### 13. 单 provider 依赖模式

如果不用 starter，也可以手动选择一个 provider 依赖。业务代码仍只依赖 Nexary API，provider 通过运行时依赖和 `nexary.*` 配置选择：

```groovy
// Maven Central 同步后也可以替换为 Latest Version。
def nexaryVersion = "0.19.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")

    // 业务编译期只依赖 Nexary API。
    implementation 'com.aweimao:nexary-messaging-api'

    // Boot3 / Java17+：这里选择 Kafka provider。
    runtimeOnly 'com.aweimao:nexary-messaging-kafka'
}
```

Boot3 / Java17+ 的 Messaging provider 运行时依赖可替换为 `nexary-messaging-disruptor`、`nexary-messaging-redis`、`nexary-messaging-kafka`、`nexary-messaging-rocketmq` 或 `nexary-messaging-activemq-classic` 中的一个。切换 provider 时只调整依赖和 `nexary.*` 配置，不改业务发送/消费代码。

Boot2 / Java8+ 的 Messaging 当前只验证 Redis-only：

```groovy
dependencies {
    implementation 'com.aweimao:nexary-messaging-api:0.19.0'
    runtimeOnly 'com.aweimao:nexary-messaging-redis-spring-boot2:0.19.0'
}
```

Boot2 / Java8+ 的 Job 不用 starter 时，当前验证本地 scheduler、XXL-JOB 触发映射、PowerJob 触发映射和 Redis completed-record store。只需要本地调度时使用下面这段：

```groovy
dependencies {
    implementation 'com.aweimao:nexary-job-api:0.19.0'
    runtimeOnly 'com.aweimao:nexary-job-scheduler-spring-boot2:0.19.0'
}
```

需要 XXL-JOB、PowerJob 或 Redis completed-record store 时，再分别加入：

```groovy
dependencies {
    runtimeOnly 'com.aweimao:nexary-job-xxljob-spring-boot2:0.19.0'
    runtimeOnly 'com.aweimao:nexary-job-powerjob-spring-boot2:0.19.0'
    runtimeOnly 'com.aweimao:nexary-job-execution-store-redis-spring-boot2:0.19.0'
}
```

Boot4 / Java21 主验证运行时的 Messaging 按 provider 独立引入。下面是 Redis provider 示例：

```groovy
dependencies {
    implementation 'com.aweimao:nexary-messaging-api:0.19.0'
    runtimeOnly 'com.aweimao:nexary-messaging-redis-spring-boot4:0.19.0'
}
```

Boot4 / Java21 主验证运行时的 Messaging provider artifactId：`nexary-messaging-disruptor-spring-boot4`、`nexary-messaging-redis-spring-boot4`、`nexary-messaging-kafka-spring-boot4`、`nexary-messaging-rocketmq-spring-boot4`。每个服务默认只选择一个。

Boot4 / Java21 主验证运行时的 Job 本地调度示例：

```groovy
dependencies {
    implementation 'com.aweimao:nexary-job-api:0.19.0'
    runtimeOnly 'com.aweimao:nexary-job-scheduler-spring-boot4:0.19.0'
}
```

需要 XXL-JOB、PowerJob 或 Redis completed-record store 时，再分别加入：

```groovy
dependencies {
    runtimeOnly 'com.aweimao:nexary-job-xxljob-spring-boot4:0.19.0'
    runtimeOnly 'com.aweimao:nexary-job-powerjob-spring-boot4:0.19.0'
    runtimeOnly 'com.aweimao:nexary-job-execution-store-redis-spring-boot4:0.19.0'
}
```

当前建议是每个服务只启用一个出站消息 provider。若同一服务要同时路由 Kafka 和 RocketMQ，由应用层显式封装自己的路由 facade，比在框架层做隐式选择更清晰。

## 发布与版本策略

- 发布到 Maven Central 前，先完成 namespace 校验、签名、SCM 元数据和 sources/javadoc 产物
- v0.5 发布前按 [发布清单](docs/zh/release.md) 生成并检查 Central Portal bundle；只验证上传包时使用 GitHub Actions 手动触发并保持 `publish_to_central=false`
- 正式 Central 发布必须从 `vX.Y.Z` tag 对应的 commit 运行；Central token 缺失会让发布步骤失败，手动发布时输入版本必须匹配选中的 tag
- Maven Central 同步完成后，再把该版本写成用户可复制的依赖版本
- `0.19.0` 已纳入 Spring Boot 2.7 / Java 8+、Spring Boot 4.1 / Java 21 的已验证模块入口
- Spring Boot 2 / JDK 8 支持走独立 provider / starter 线，不污染当前 Boot3 主线 API
- Spring Boot 4.1 / Java 21 支持走独立 Boot4 provider / starter 线；Messaging 不发布聚合所有 provider 的 Boot4 starter

详细说明见 [docs/zh/release.md](docs/zh/release.md) 和 [docs/zh/roadmap.md](docs/zh/roadmap.md)。

## 贡献与维护

Nexary 按能力维护：

- Cache、Messaging、Job 先保持清晰边界，各自拥有独立样例、测试和文档。
- 本地验证统一通过 Docker、smoke、integration tests 和 `publishToMavenLocal` 收口。
- 治理能力独立在 `nexary-governance`，不反向污染 Cache、Messaging、Job 的业务 API。
- 公开讨论使用 GitHub issue / PR；内部任务记录不作为用户文档入口。

## 开发验证

```bash
./gradlew check
./gradlew publishToMavenLocal
```
