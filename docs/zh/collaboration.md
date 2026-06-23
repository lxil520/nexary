# 贡献与维护流程

这份文档面向公开贡献者和维护者，说明 Nexary 如何接收需求、评审变更和验证发布质量。它不包含内部任务记录或过程对话。

## 核心原则

1. 先明确问题，再决定是否改 API、实现、样例或文档。
2. Cache、Messaging、Job 按能力独立维护，避免一个能力的实现细节污染其他能力。
3. 每个能力变更都需要同步考虑公共 API、配置、样例、文档和测试。
4. 本地验证通过 Docker、smoke、integration tests 和 `publishToMavenLocal` 收口。
5. 治理能力在范围清楚后独立成模块，不直接堆进现有主 API。

## 贡献类型

| 类型 | 推荐流程 |
| --- | --- |
| Bug fix | 提交 issue 或 PR，附复现方式和测试结果 |
| Provider integration | 先提交 issue 说明场景、provider 语义和测试方案，再决定是否进入路线图 |
| Public API change | 先讨论设计，再提交 PR |
| Docs / sample | 说明目标读者、接入路径和验证命令 |
| Governance capability | 先定义边界，不直接混入 cache / messaging / job |

## 变更要求

- 公共 API 不暴露 Redis、Kafka、RocketMQ、XXL-JOB、Caffeine 等原生类型。
- 配置前缀保持 `nexary.*`。
- 示例配置只能使用本地、demo 或 fake 值，不能出现真实凭证、私有端点或私有仓库配置。
- 新增或明显改写用户文档时，中文和英文版本要同步。
- 样例必须展示用户应复制的业务代码路径，不要求用户阅读 provider 接线代码才能理解用法。
- 新能力必须说明使用条件、非目标和残余风险。

## 验证要求

变更合并前至少应提供：

- 影响模块的单元测试或集成测试结果。
- 必要时的 `./scripts/middleware/smoke.sh` 结果。
- 涉及真实中间件时的 integration test 结果。
- 涉及发布产物时的 `publishToMavenLocal` 结果。
- 文档和样例的路径说明。

## 维护边界

- `0.1.x` 不引入 sidecar、agent、控制面、管理后台。
- JDK 8 / Spring Boot 2 兼容不污染当前主线；如要支持，必须走独立兼容线和验证 gate。
- Provider 新增不应倒逼主 API 变胖。
- 未验证的能力不能在文档中写成已支持。
