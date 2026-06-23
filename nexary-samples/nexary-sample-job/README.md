# nexary-sample-job

这是 Job 能力的 starter selector 样例。

核心目标：业务代码只使用 Nexary job API，不关心底层是本地调度 provider 还是 XXL-JOB bridge provider。切换 provider 只改 `nexary.job.provider` 和对应 profile 配置，不改业务 job handler。

## 用户代码长什么样

用户真正需要写的是一个普通 Spring 组件：

```java
@Component
public class SampleBusinessJob implements NexaryJob {
    public static final String JOB_NAME = "sample-business-job";

    private final SampleBusinessService businessService;

    public SampleBusinessJob(SampleBusinessService businessService) {
        this.businessService = businessService;
    }

    @Override
    public String name() {
        return JOB_NAME;
    }

    @Override
    public JobResult execute(JobContext context) {
        SampleBusinessService.BusinessReceipt receipt = businessService.run(context);
        return new JobResult(JobResult.JobStatus.SUCCESS, receipt.message());
    }
}
```

`SampleBusinessService` 是普通业务 service。真实项目里可以像平时一样注入 RPC、MQ、cache、repository 等协作者。provider 选择、配置绑定、本地调度或 XXL-JOB bridge wiring 都不写在 job 业务类里。

## 引入方式

本模块使用 starter 模式。当前已验证组合是 Spring Boot 3.3.x + Java 17+：

```groovy
// 已验证：Spring Boot 3.3.x + Java 17+
implementation project(':nexary-boot:nexary-job-spring-boot-starter')
```

发布到 Maven Central 后，把 `nexaryVersion` 替换为最新 release。Spring Boot 3.3 / Java 17+ starter 入口：

```groovy
def nexaryVersion = "0.6.0"
implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
implementation 'com.aweimao:nexary-job-spring-boot-starter'
```

Spring Boot 2.7 / Java 8+ starter 入口：

```groovy
def nexaryVersion = "0.6.0"
implementation "com.aweimao:nexary-job-spring-boot2-starter:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 starter 入口：

```groovy
def nexaryVersion = "0.6.0"
implementation "com.aweimao:nexary-job-spring-boot4-starter:${nexaryVersion}"
```

starter 聚合当前 Job API、local provider 和 XXL-JOB bridge provider。使用者通过配置选择 provider：

```yaml
nexary:
  job:
    provider: local
```

切到 XXL-JOB 触发映射 模式：

```yaml
nexary:
  job:
    provider: xxljob
```

执行记录默认保存在 in-memory store。需要跨进程或重启后查询 `JobExecutionRecord` 时，可以启用 Redis durable store；这仍然不需要修改业务 job handler：

```yaml
nexary:
  job:
    execution:
      store:
        redis:
          enabled: true
          retention: 1d
```

可观测性同样不写进业务 job handler。应用侧提供 `NexaryObservationListener` 或 `NexaryObservationPublisher` 后，框架会发出 `job.trigger`、`job.execution.end`、`job.retry.attempt`、`job.execution.skip`、`job.store.save`、`job.store.find`、`job.scheduler.run`、`job.xxljob.bridge.trigger` 等事件。标签只使用有限维度，例如 `provider`、`trigger`、`status`、`skip_reason`、`shard_presence`、`failure_category`；不要把 execution id、参数、payload、异常消息或堆栈作为指标标签。

## 目录说明

```text
org.nexary.samples.job.app
  JobSampleApplication
org.nexary.samples.job.business
  SampleBusinessJob
  SampleBusinessService
org.nexary.samples.job.processor
  JobProcessorSampleApplication
  ProcessorBusinessJob
```

用户主要看 `SampleBusinessJob` 和自己的业务 service。

业务 job 只依赖：

- `NexaryJob`
- `JobContext`
- `JobResult`

需要追踪一次执行时，应用侧可以调用 `NexaryJobOperations.triggerExecution(...)` 获取 `JobExecutionRecord`，里面包含 execution id、触发来源、状态、尝试次数、耗时和错误信息。业务 job handler 本身不需要读取这些配置或记录。

本 starter 样例不包含 provider wiring 包。provider wiring 属于 Nexary starter/provider 模块。

## Local Provider 运行

默认使用 `local`：

```bash
./gradlew :nexary-samples:nexary-sample-job:test
```

测试会启动 Spring Boot sample，并执行 `SampleBusinessJob`。

## XXL-JOB Bridge 运行

```bash
./gradlew :nexary-samples:nexary-sample-job:test --tests org.nexary.samples.job.XxlJobSampleApplicationTest
```

当前只演示 Nexary job 与 XXL-JOB bridge 的触发映射，不声明真实 XXL-JOB Admin 调度、executor 注册、callback lifecycle 或平台触发执行。

## Processor-Style

processor-style 保留为非 Web 任务进程骨架：

```bash
./gradlew :nexary-samples:nexary-sample-job:runProcessor
```

processor 样例只展示非 Web 启动和组件扫描 job。用户参考重点仍然是 `ProcessorBusinessJob`。

## SPI Provider 样例

不用 starter 的依赖方式 模式不放在本模块里。每个 provider 一个独立样例模块：

- `nexary-sample-job-spi-scheduler`：`nexary-job-api` + `nexary-job-scheduler`
- `nexary-sample-job-spi-xxljob`：`nexary-job-api` + `nexary-job-xxljob`
- `nexary-sample-job-spi-powerjob`：`nexary-job-api` + `nexary-job-powerjob`

这样用户可以清楚看到两种引入方式：

- starter selector：引入 starter，通过 `nexary.job.provider` 选择 provider
- SPI/provider：引入 API 和一个具体 provider 模块

版本矩阵：

| Spring Boot | JDK | 状态 | 入口 |
| --- | --- | --- | --- |
| 3.3.x | Java 17+ | 当前已验证 | `nexary-job-spring-boot-starter` |
| 2.7.x | Java 8+ | 已验证受限边界 | `nexary-job-spring-boot2-starter` |
| 4.1.x | 官方最低 JDK 以 Spring 官方文档为准；Java 21 是 Nexary 主验证运行时目标 | 已验证 Boot4 入口 | `nexary-job-spring-boot4-starter` |
