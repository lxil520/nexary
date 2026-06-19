# nexary-sample-job-spi-scheduler

这个样例不使用 starter，只手动引入 Job API 和本地 scheduler provider。

## 引入方式

```groovy
// 当前样例使用仓库内 project 依赖。
implementation project(':nexary-job:nexary-job-api')
runtimeOnly project(':nexary-job:nexary-job-scheduler')
```

发布到 Maven Central 后，把 `nexaryVersion` 替换为最新 release。

Spring Boot 3.3 / Java 17+ local scheduler provider：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-scheduler:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ local scheduler provider：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-scheduler-spring-boot2:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 local scheduler provider：

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-scheduler-spring-boot4:${nexaryVersion}"
```

业务代码只使用 `org.nexary.job.*`，不依赖本地 scheduler 类型。

业务 job 仍然只是普通 Spring 组件：

```java
@Component
public class SampleBusinessJob implements NexaryJob {
    public static final String JOB_NAME = "sample-business-job";

    @Override
    public String name() {
        return JOB_NAME;
    }

    @Override
    public JobResult execute(JobContext context) {
        return new JobResult(JobResult.JobStatus.SUCCESS,
                "processed shard " + context.shardIndex() + "/" + context.shardTotal());
    }
}
```

## 配置

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

`job-name` 必须等于 `NexaryJob.name()`。

## 运行

```bash
./gradlew :nexary-samples:nexary-sample-job-spi-scheduler:test
```

测试会启动 Spring Boot sample，并执行 `SampleBusinessJob`。
