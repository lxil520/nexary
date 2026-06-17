# 验证清单

每次发布前或重要能力变更后，至少记录以下信息：

- 变更涉及的能力：cache、messaging、job、boot、docs 或 samples。
- 运行过的基础命令，例如 `./gradlew check`。
- 是否启动本地中间件并通过 `./scripts/middleware/smoke.sh`。
- 是否运行真实中间件 integration tests。
- 是否运行 `publishToMavenLocal`。
- 涉及的样例和文档路径。
- 已知残余风险和明确不支持的能力声明。

## 阻塞发布的情况

- 公共 API 暴露了 provider 原生类型。
- 用户可复制样例需要阅读或修改 provider 接线代码才能理解基本用法。
- 中英文文档不一致。
- 中间件联调脚本不可运行，且变更依赖真实中间件行为。
- 失败用例没有解释、没有风险归因，或仍影响主路径。
- 文档声称了未验证能力，例如 strong consistency、exactly-once、分布式事务或平台托管生命周期。
