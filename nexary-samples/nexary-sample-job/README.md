# nexary-sample-job

这个样例演示 starter 模式下怎么写一个 Job。

业务 job 只实现 `NexaryJob`。用本地 scheduler 还是 XXL-JOB bridge，由 `nexary.job.provider` 和 profile 配置决定，不改 job handler。

## 用户代码长什么样

你真正需要写的是一个普通 Spring 组件：

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

`SampleBusinessService` 就是普通业务 service。真实项目里照常注入 RPC、MQ、cache、repository。provider 选择、配置绑定、本地调度和 XXL-JOB 接入都不写进业务 job 类。

## 引入方式

本模块使用 starter 模式。Spring Boot 3.3.x + Java 17+ 已验证：

```groovy
// 已验证：Spring Boot 3.3.x + Java 17+
implementation project(':nexary-boot:nexary-job-spring-boot-starter')
```

发布到 Maven Central 后，把 `nexaryVersion` 替换为最新 release。Spring Boot 3.3 / Java 17+ starter 入口：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-spring-boot-starter'
```

Spring Boot 2.7 / Java 8+ starter 入口：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-spring-boot2-starter:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 starter 入口：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-spring-boot4-starter:${nexaryVersion}"
```

starter 带上 Job API、本地 scheduler 和 XXL-JOB bridge。运行时用配置选择：

```yaml
nexary:
  job:
    provider: local
```

本地 scheduler 的 cron 写在 `nexary.job.scheduler.schedules`。`job-name` 必须等于 `NexaryJob.name()`：

```yaml
nexary:
  job:
    provider: local
    scheduler:
      schedules:
        - job-name: sample-business-job
          cron: "0 */10 * * * *"
          enabled: true
          single-instance: true
          shard-total: 1
```

切到 XXL-JOB bridge：

```yaml
nexary:
  job:
    provider: xxljob
```

执行记录默认放在内存里。需要跨进程或重启后还能查 `JobExecutionRecord` 时，打开 Redis store；业务 job handler 仍然不用改：

```yaml
nexary:
  job:
    execution:
      store:
        redis:
          enabled: true
          retention: 1d
```

可观测性也不写进业务 job handler。应用侧提供 `NexaryObservationListener` 或 `NexaryObservationPublisher` 后，框架会发出 `job.trigger`、`job.execution.end`、`job.retry.attempt`、`job.execution.skip`、`job.store.save`、`job.store.find`、`job.scheduler.run`、`job.xxljob.bridge.trigger` 等事件。标签只用有限维度，例如 `provider`、`trigger`、`status`、`skip_reason`、`shard_presence`、`failure_category`；不要把 execution id、参数、payload、异常消息或堆栈作为指标标签。

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

## 不用 starter 的样例

这个 starter 样例不混入手动依赖接法。需要自己选择依赖时，看下面两个独立样例：

- `nexary-sample-job-spi-scheduler`：`nexary-job-api` + `nexary-job-scheduler`
- `nexary-sample-job-spi-xxljob`：`nexary-job-api` + `nexary-job-xxljob`

这样两种接入方式不会混在一起：

- starter：引入 starter，通过 `nexary.job.provider` 选择 provider
- 手动依赖：引入 API 和一个具体 provider 模块

版本矩阵：

| Spring Boot | JDK | 状态 | 入口 |
| --- | --- | --- | --- |
| 3.3.x | Java 17+ | 当前已验证 | `nexary-job-spring-boot-starter` |
| 2.7.x | Java 8+ | 已验证受限边界 | `nexary-job-spring-boot2-starter` |
| 4.1.x | 官方最低 JDK 以 Spring 官方文档为准；Java 21 是 Nexary 主验证运行时目标 | 已验证 Boot4 入口 | `nexary-job-spring-boot4-starter` |
