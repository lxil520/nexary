# 发布清单

在完成命名空间校验、GitHub 仓库地址和签名基础设施前，Nexary 还不能直接发到 Maven Central。

## 先做什么

发布前至少检查：

- 执行 `./gradlew check`
- 执行 `./gradlew verifyReleaseGate`
- 执行 `./gradlew publishToMavenLocal`
- 在 GitHub Actions 中通过 dependency review 和 secret scan
- 校验 `group`、artifact 名称、license、SCM、developer metadata
- 校验 sources 和 Javadoc jar 能正常产出
- 确认仓库中没有真实凭证、私有端点或私有仓库配置
- 按 SemVer 创建 tag，例如 `v0.4.0`

## Maven Central 最小要求

按照 Sonatype Central 的公开要求，至少要满足：

- 拥有并验证 `com.aweimao` namespace
- 发布带签名的产物
- POM 中包含 license、developer、SCM 信息
- 同步提供 sources 和 Javadoc

官方参考：

- [Sonatype Central publishing overview](https://central.sonatype.org/publish/publish-portal/)
- [Sonatype Central requirements](https://central.sonatype.org/publish/requirements/)
- [Gradle publishing notes](https://central.sonatype.org/publish/publish-portal-gradle/)

## 对 Nexary 的发布路径

当前不建议立刻引入复杂发布矩阵。更务实的路径是：

1. 先把 GitHub 仓库公开并稳定当前发布线
2. 完成 namespace 校验
3. 补 GPG 签名和发布凭证托管
4. 固化一个 Gradle 发布流水线
5. 先发 `0.4.0`，同时保留 Boot2 / Boot4 兼容 gate 的报告入口

## Gradle 发布命令

普通开发验证：

```bash
./gradlew check
./gradlew verifyReleaseGate
./gradlew publishToMavenLocal
```

Maven Central 发布前需要设置真实仓库元数据和签名密钥：

```bash
./gradlew mavenCentralBundle \
  -PnexaryVersion=0.4.0 \
  -PprojectWebsite=https://github.com/<owner>/nexary \
  -PprojectScmUrl=https://github.com/<owner>/nexary.git \
  -PprojectScmConnection=scm:git:https://github.com/<owner>/nexary.git \
  -PprojectScmDeveloperConnection=scm:git:ssh://git@github.com:<owner>/nexary.git \
  -PnexarySigningKey="$NEXARY_SIGNING_KEY" \
  -PnexarySigningPassword="$NEXARY_SIGNING_PASSWORD"
```

该命令只生成 `build/distributions/nexary-<version>-central-bundle.zip`，不自动发布到 Central。上传或自动发布前仍需确认 Sonatype namespace、签名、公钥发布和仓库元数据。

## GitHub 发布配置

仓库需要配置：

- Repository variables：`NEXARY_PROJECT_WEBSITE`、`NEXARY_PROJECT_SCM_URL`、`NEXARY_PROJECT_SCM_CONNECTION`、`NEXARY_PROJECT_SCM_DEVELOPER_CONNECTION`
- Repository secrets：`NEXARY_SIGNING_KEY`、`NEXARY_SIGNING_PASSWORD`

CI 会在普通分支运行 `check`、`verifyReleaseGate` 和 `publishToMavenLocal`。tag `v*.*.*` 会生成 Maven Central Portal bundle 作为 GitHub Actions artifact。

## 中央仓库发布模块

Maven Central 只发布框架模块、provider、starter 和 BOM，不发布 `nexary-samples`。样例继续作为源码工程和本地验证入口存在。

## 多版本支持策略

Nexary 需要规划多版本支持来扩大用户面，但不能把未验证组合写成已支持。

发布策略是：

- 当前 `0.4.x` release 只声明已经通过 gate 的组合；当前已验证组合包括 Spring Boot 3.3 / Java 17+ 主线，以及 Spring Boot 2.7 / Java 8+ 的 Cache Redis/Valkey 单级缓存入口、Messaging Redis-only 入口和 Job local/XXL-JOB/PowerJob bridge 入口。
- Spring Boot 2.7 / JDK 8+ 已按 Cache、Messaging、Job 分能力进入独立 gate；未验证 provider 不写成支持。
- Spring Boot 4.1 / Java 21 已按 Cache、Messaging、Job 分能力进入独立 gate；这不等于整个仓库所有模块都完成 Boot4 支持。
- 通过 gate 后，README 才增加类似大型框架的多版本 dependency 矩阵。
- 多版本支持优先通过独立 BOM、starter 或兼容分支实现，避免污染主线 API。

推荐顺序：

- `0.4.x`：Java 17 / Spring Boot 3.3 主线发布，加上 Boot2/JDK8、Boot4/JDK21 和治理策略的可执行 gate。
- `0.4.x` 后续：继续补齐 Boot2/JDK8 Messaging 的 Disruptor/Kafka/RocketMQ/ActiveMQ Classic provider，并按真实样例和集成测试逐项扩展支持声明。
- `1.0.0` 前：固定最终兼容策略和维护成本边界。
