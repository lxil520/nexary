# 本地验证指南

本页说明如何在本地验证 Nexary 的编译、测试、中间件联调和本地发布产物。它不是内部任务流程文档。

## 建议先看

- 验证清单：[verification-acceptance.md](verification-acceptance.md)
- 中间件联调：[integration.md](integration.md)
- 发布清单：[release.md](release.md)

## 基础验证

```bash
./gradlew check
```

这一步覆盖基础编译、单元测试和常规质量门槛。

## 本地中间件验证

启动本地 Redis、Kafka、RocketMQ、MySQL 和 XXL-JOB Admin：

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
```

运行集成测试：

```bash
./scripts/middleware/run-integration-tests.sh
```

如果只验证单个能力，可以运行对应模块的 scoped tests。涉及真实 Redis / Kafka / RocketMQ / XXL-JOB 行为时，应保留命令和结果，方便 PR 评审。

## 本地发布验证

```bash
./gradlew publishToMavenLocal
```

发布相关变更至少应确认 sources、javadoc 和主要 starter / provider 模块可以正常生成并安装到本地 Maven 仓库。

## 验证证据建议

提交 PR 或发布前，建议记录：

- 变更影响的能力：cache、messaging、job 或 boot。
- 运行过的 Gradle 命令。
- 是否启动并通过 middleware smoke。
- 是否运行真实中间件 integration tests。
- 是否运行 `publishToMavenLocal`。
- 已知残余风险和不声明的能力。

## 边界

- 验证命令只证明当前测试覆盖的行为，不等于生产强一致、exactly-once 或平台托管能力。
- 失败用例应说明原因、影响范围和是否阻塞发布。
- 未验证的 provider、配置或部署形态不应写成已支持。
