# 常见问题

这页从本地接入最容易卡住的地方开始排查。每一节都给出能直接执行的命令。

## 先确认版本和依赖

先确认你复制的是同一个版本号。GitHub tag `v0.12.0` 对应依赖版本 `0.12.0`。

```bash
./gradlew properties | grep '^version:'
```

Spring Boot 3.3 / Java 17+ 服务优先使用 BOM：

```groovy
def nexaryVersion = "0.12.0"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
}
```

Boot2 / Boot4 入口当前使用直接版本。不要把 Boot3 starter 和 Boot2 / Boot4 provider 混在同一个服务里。

## 样例端口被占用

先看哪个进程占用了端口：

```bash
lsof -nP -iTCP:8081 -sTCP:LISTEN
lsof -nP -iTCP:8082 -sTCP:LISTEN
```

常用端口：

| 样例 | 端口 |
| --- | --- |
| `nexary-sample-cache` | `8081` |
| `nexary-sample-messaging` | `8082` |
| `nexary-sample-job` | `8083` |
| `nexary-sample-cache-spi-redis` | `8091` |
| `nexary-sample-messaging-spi-disruptor` | `8092` |
| `nexary-sample-messaging-spi-redis` | `8093` |
| `nexary-sample-messaging-spi-kafka` | `8094` |
| `nexary-sample-messaging-spi-rocketmq` | `8095` |
| `nexary-sample-job-spi-scheduler` | `8096` |
| `nexary-sample-job-spi-xxljob` | `8097` |
| `nexary-sample-messaging-spi-activemq-classic` | `8098` |
| `nexary-sample-job-spi-powerjob` | 无固定 HTTP 端口 |

如果只想临时换端口，可以给 Spring Boot 传参数：

```bash
./gradlew :nexary-samples:nexary-sample-cache:run --args='--server.port=18081'
```

## 本地中间件没起来

先启动本地栈，再跑 smoke：

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
```

如果 smoke 失败，先看容器状态：

```bash
docker compose -f deploy/middleware/docker-compose.yml ps
docker compose -f deploy/middleware/docker-compose.yml logs --tail=80
```

默认端口：

| 中间件 | 本地端口 |
| --- | --- |
| Redis | `16379` |
| Valkey | `16380` |
| Kafka | `19092` |
| RocketMQ NameServer | `19876` |
| ActiveMQ Classic | `61616` |

## provider 选错

先看你使用的是 starter 还是 API + provider 方式。

starter 方式通过配置选择：

```yaml
nexary:
  messaging:
    provider: redis
```

API + provider 方式必须只放一个对应 provider 的运行时依赖，例如：

```groovy
dependencies {
    implementation "com.aweimao:nexary-messaging-api:0.12.0"
    runtimeOnly "com.aweimao:nexary-messaging-redis:0.12.0"
}
```

不要同时放多个同类 provider 依赖，除非样例文档明确说明可以这样跑。

## Job cron 没触发

local scheduler 的 cron 写在 `nexary.job.scheduler.schedules`，`job-name` 必须等于业务类 `NexaryJob.name()`。

```yaml
nexary:
  job:
    provider: local
    scheduler:
      schedules:
        - job-name: sample-business-job
          cron: "0 */5 * * * *"
```

XXL-JOB 和 PowerJob 的触发时间由外部平台管理。Nexary bridge 只负责把外部触发映射到 `NexaryJob` 执行生命周期。

## 指标看不到

先确认引入了 Micrometer starter：

```groovy
implementation "com.aweimao:nexary-observation-micrometer-spring-boot-starter:0.12.0"
```

再确认业务路径真的执行过。Nexary 只在 cache、messaging、job、governance 的边界事件上报指标，不会凭空生成业务指标。

默认指标名：

| 指标 | 说明 |
| --- | --- |
| `nexary.observation.events.total` | 事件计数 |
| `nexary.observation.events.duration` | 事件耗时 |

按 `category`、`operation`、`provider`、`outcome` 等标签过滤 cache、messaging、job、governance 事件。

标签只保留白名单字段。cache key、message id、execution id、payload、异常文本和堆栈不会进入指标标签。

## 发布前本地检查

准备发布前至少跑：

```bash
./gradlew check
./gradlew verifyReleaseGate
./gradlew compatibilityAudit
```

如果要生成 Maven Central bundle，需要本机或 CI 提供签名 key：

```bash
./gradlew mavenCentralBundle -PnexaryVersion=0.12.0
```

正式发布必须从 Git tag 触发。手动 workflow 默认只做 bundle 检查，不直接发布到 Maven Central。
