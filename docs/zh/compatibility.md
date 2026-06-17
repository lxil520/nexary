# 兼容性矩阵

## 当前基线

| 项目 | 支持情况 |
| --- | --- |
| Java 编译基线 | 17，已验证 |
| 主要运行目标 | 21，已验证 |
| Spring Boot | 3.3.x，已验证 |
| Spring Boot 2.7 + Java 8+ | `0.2.x` 目标，优先审计和适配，需要独立兼容 gate |
| Spring Boot 4.x + Java 21+ | `0.2.x` 后续验证目标，Boot2 gate 通过后推进；官方最低 JDK 仍以 Spring 文档为准 |

## 兼容线策略

Nexary 希望像成熟框架一样覆盖更多用户，但兼容线必须独立验收：

- Boot 3 主线继续使用 Java 17、Spring Boot 3.3 和当前 starter。
- Boot 2 兼容线的核心目标是 Java 8 用户，必须优先处理公共 API 和实现代码中的 Java 17+ 语法、Java 9+ API、依赖版本和自动配置入口差异。
- Boot 2 兼容线优先评估独立 starter 或独立 BOM，避免把 Boot 2 依赖反向污染 Boot 3 主线。
- Boot 4 兼容线以 Java 21+ 作为 Nexary 主验证目标，但只有在 Boot 2 gate 通过后推进，且官方最低 JDK 仍以 Spring 文档为准。
- JDK 8 支持需要单独检查 `record`、pattern matching、switch expression、`Stream.toList()`、`Map.of/List.of/Set.of`、Gradle toolchain、Caffeine 2/3 差异、Spring Data Redis/Lettuce、Kafka、RocketMQ、XXL-JOB 等兼容性。
- Boot 2 自动配置需要补 `spring.factories` 或等价兼容入口；当前 Boot 3 主线使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
- 正式发布前需要决定 starter artifact 是否显式区分 `spring-boot3`、`spring-boot2` 和 `spring-boot4`，避免用户误选。
- 任何兼容组合只有通过编译、样例运行、文档声明扫描和 provider 集成验证后，才能进入 README 依赖矩阵。

## `0.2.x` 兼容推进顺序

1. 运行 `./gradlew compatibilityAudit`，记录当前 Java 8 / Boot 2 阻断点。
2. 先决定 Java 8 兼容策略：改造当前公共 API，还是拆出 Java 8 兼容 API / adapter。未解决公共 API `record` 前，不能声明 Java 8 用户可用。
3. 建立 Boot 2.7 / Java 8+ 独立 BOM、starter、样例和 CI gate。
4. Boot 2 gate 通过后，再建立 Boot 4.x / Java 21+ BOM、starter、样例和 CI gate。
5. 只有对应 gate 通过后，README 才增加类似成熟框架的 Maven / Gradle 依赖片段。

`compatibilityAudit` 会在控制台输出阻断点，并生成 `build/reports/nexary/compatibility-audit.md`。这个报告只是兼容差距证据，不是支持声明。

`./gradlew check` 还会执行公开文档卫生检查，防止内部协作记录、任务编号和 agent 约定文件误进入用户文档。

## 兼容策略

- `0.2.x` 期间会尽量保守演进公共 API，但在 `1.0.0` 之前不承诺严格兼容。
- provider 适配模块可能随着集成覆盖增加而继续补充行为和配置项。
- 历史内部实现不是兼容目标。
