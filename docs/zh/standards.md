# 编码规范

这份文档是面向 Nexary 维护者和贡献者的工程规范。目标是保证公共 API、样例、文档和测试能够长期保持一致。

## 基线

- Java 编译基线：17
- 主要运行验证目标：Java 21
- Spring Boot 主线：3.3
- 包名前缀：`org.nexary.*`
- 配置前缀：`nexary.*`

## API 设计规则

- 公共 API 要小，不把 Redis、Kafka、RocketMQ、XXL-JOB 等原生类型带进应用层。
- 时间相关字段统一使用 `Duration` 或 `Instant`，不新增裸 `long` 超时或 TTL 参数。
- 状态统一用 `enum`，不引入新的魔法字符串。
- 配置统一用 `@ConfigurationProperties`。
- public API 必须带 Javadoc。
- API 模块不能反向依赖实现模块。

## 模块边界规则

- `nexary-framework` 只承载基础语义和 SPI，不承载具体中间件。
- `nexary-cache-api`、`nexary-messaging-api`、`nexary-job-api` 只承载统一抽象。
- provider 模块只做适配，不重写公共语义。
- starter 只聚合依赖和自动配置，不承担业务逻辑。
- samples 是接入参考，不是平台后台，也不是控制面。

## 文档规则

- 中文文档是主叙述面，英文文档做镜像。
- 新增或明显改写用户文档时，`docs/zh` 和 `docs/en` 要一起更新。
- 用户文档只讲能力、接入方式、限制、验证方式和版本边界。
- 用户文档不包含聊天记录、临时工作记录、私有路径或未发布计划。

## 样例规则

- 样例保持 Spring Boot 工程形态，不做单文件 demo。
- 不再保留聚合 综合演示；每个 capability 的独立 starter / SPI 样例就是用户入口。
- capability sample 要明确说明“应该复制什么”。
- capability sample 必须先展示业务代码如何使用 Nexary 抽象，再展示引入方式；不能让用户先阅读 provider 接线代码。
- 样例必须体现 Nexary 的本质：业务代码不关心 Redis、Kafka、RocketMQ、XXL-JOB 等底层实现。切换底层实现时，业务 controller/facade/job handler/consumer 代码不应修改。
- 每个 capability 至少提供两种引入路径的参考：
  - starter selector 模式：引入对应 starter，由 starter 自动带入可选 provider 能力，通过 `nexary.*` 配置选择具体 provider/mode。
  - SPI provider 模式：业务只依赖 API，通过单独依赖具体 provider 模块和 `ServiceLoader` / Spring bridge 引入实现。
- starter selector 样例的代码里不能声明 provider-specific `@Configuration`、producer factory、consumer factory、Redis client、Kafka/RocketMQ template、XXL-JOB executor 等框架接线 bean；这些必须由 Nexary starter / provider 模块承担。
- SPI provider 样例可以展示 provider 依赖和 SPI 发现方式，但业务代码仍然只能依赖 Nexary API，不能直接调用底层中间件 SDK。
- `build.gradle` 必须用注释说明引入方式：
  - starter selector 样例说明 starter 会聚合哪些能力、如何用配置选择。
  - SPI provider 样例说明 API 与 provider 依赖分别负责什么。
- `application*.yml` 必须用注释说明关键 `nexary.*` 配置项、默认值含义、何时需要修改、哪些是本地联调值。
- demo-only bean 和真实 provider 接线要分清楚；demo-only 只能用于零中间件学习，不得伪装成与真实 provider 平级的生产后端。
- 专项样例必须按“中立入口 + provider/mode 隔离”组织包结构，不能把多个 provider 的配置和中立业务入口堆在同一个包里。
- 推荐包结构：
  - `app`：启动类和启动边界。
  - `api` 或 `web`：HTTP、CLI、测试触发入口。
  - `application` 或 `facade`：用户应该复制的业务用例入口。
  - `common` 或 `support`：样例 DTO、状态仓库、诊断对象等 Nexary 层 支撑代码。
- provider/mode 包只能在 SPI 样例或 provider 验证样例中出现，用于展示 SPI 引入或验证边界；starter selector 样例不应包含 provider 接线包。
- 每个 provider/mode 都要有独立 profile、运行命令和验收说明；用户应该能在不修改业务代码的前提下切换 provider/mode。
- 禁止新增“万能配置类”或把所有 provider 选项塞进一个 controller/facade/configuration 文件。
- 禁止在业务样例代码中直接引入 Kafka、RocketMQ、Redis、XXL-JOB、Disruptor 原生类型；这些只能出现在 provider 模块、starter 自动配置、SPI provider 样例的隔离层或测试验证代码里。
- 样例发布前必须先检查结构门禁；结构不达标时不能进入功能通过态。

## 测试与验收规则

- 基础门：`./gradlew check`
- 真实中间件验证通过 `scripts/middleware/*` 收口
- 每个能力都要有独立验收面，不靠全量 综合演示 代替
- 验收记录应列明命令、环境、结果和已知限制

## 禁止项

- 不提交真实凭证、私有端点、私有仓库配置或非 demo 环境值
- 不为了短期兼容把主线 API 做乱
- 不在当前发布线混入 sidecar、agent、控制面、后台平台类能力
- 不在文档里夸大未验证过的 provider、样例、部署形态或运行结果
