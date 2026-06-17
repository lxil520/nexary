# nexary-sample-job-spi-scheduler

这是 Job 能力的 SPI/provider dependency 样例，且只展示 local scheduler provider。

## 引入方式

```groovy
// 当前样例使用仓库内 project 依赖。
implementation project(':nexary-job:nexary-job-api')
runtimeOnly project(':nexary-job:nexary-job-scheduler')
```

Maven Central 发布后的当前已验证依赖入口：

```groovy
def nexaryVersion = "0.2.0-alpha.2"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-api'
runtimeOnly 'org.nexary:nexary-job-scheduler'
```

Boot2 / Java8 兼容目标仍处于待验证、未发布状态。拟定名称为 `nexary-job-api-java8` 和 `nexary-job-scheduler-spring5`，通过独立验证前不要作为依赖复制使用。

业务代码只使用 `org.nexary.job.*`，不依赖 provider 类型。

业务 job 代码仍然只是普通 Spring 组件：

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
```

## 运行

```bash
./gradlew :nexary-samples:nexary-sample-job-spi-scheduler:test
```

测试会启动 Spring Boot sample，并执行 `SampleBusinessJob`。
