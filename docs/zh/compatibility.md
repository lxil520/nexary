# 兼容性矩阵

## 当前基线

| 项目 | 支持情况 |
| --- | --- |
| Java 编译基线 | 17，已验证 |
| 主要运行目标 | 21，已验证 |
| Spring Boot | 3.3.x，已验证 |
| Spring Boot 2.7 + Java 8+ | 计划支持，需要独立兼容 gate |
| Spring Boot 4 | 暂不进入 v0.2 目标，后续按官方稳定线评估 |

## 兼容线策略

Nexary 希望像成熟框架一样覆盖更多用户，但兼容线必须独立验收：

- Boot 3 主线继续使用 Java 17、Spring Boot 3.3 和当前 starter。
- Boot 2 兼容线的核心目标是 Java 8 用户，优先评估独立 starter 或独立 BOM，避免把 Boot 2 依赖反向污染 Boot 3 主线。
- JDK 8 支持需要单独检查 Java 语法、依赖版本、Gradle toolchain、Caffeine 2/3 差异、Spring Data Redis/Lettuce、Kafka、RocketMQ、XXL-JOB 等兼容性。
- 正式发布前需要决定 starter artifact 是否显式区分 `spring-boot3` 和 `spring-boot2`，避免用户误选。
- 任何兼容组合只有通过编译、样例运行、文档声明扫描和 provider 集成验证后，才能进入 README 依赖矩阵。

## 兼容策略

- `0.2.x` 期间会尽量保守演进公共 API，但在 `1.0.0` 之前不承诺严格兼容。
- provider 适配模块可能随着集成覆盖增加而继续补充行为和配置项。
- 历史内部实现不是兼容目标。
