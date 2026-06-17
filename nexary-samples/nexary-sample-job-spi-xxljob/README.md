# nexary-sample-job-spi-xxljob

这是 Job 能力的 SPI/provider dependency 样例，且只展示 XXL-JOB bridge provider。

## 引入方式

```groovy
// 当前样例使用仓库内 project 依赖。
implementation project(':nexary-job:nexary-job-api')
runtimeOnly project(':nexary-job:nexary-job-xxljob')

// Maven Central 发布后对应当前已验证 Spring Boot 3.3.x + Java 17+ artifact：
// implementation 'org.nexary:nexary-job-api'
// runtimeOnly 'org.nexary:nexary-job-xxljob'
//
// Boot2 / Java8 兼容目标，待验证，未发布：
// implementation 'org.nexary:nexary-job-api-java8'
// runtimeOnly 'org.nexary:nexary-job-xxljob-spring5'
```

业务代码只使用 `org.nexary.job.*`，不依赖 provider 内部类或 XXL-JOB 原生类型。

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
    provider: xxljob
```

## 运行

```bash
./gradlew :nexary-samples:nexary-sample-job-spi-xxljob:test
```

测试会启动 Spring Boot sample，并执行 `SampleBusinessJob`。

当前只演示 Nexary job 与 XXL-JOB bridge 的触发映射，不声明真实 XXL-JOB Admin 调度、executor 注册、callback lifecycle 或平台触发执行。
