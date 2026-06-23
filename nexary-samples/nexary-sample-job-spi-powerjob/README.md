# nexary-sample-job-spi-powerjob

这是 Job 能力“不用 starter、只引入一个 provider”的样例，且只展示 PowerJob 触发 provider。

## 引入方式

```groovy
// 当前样例使用仓库内 project 依赖。
implementation project(':nexary-job:nexary-job-api')
runtimeOnly project(':nexary-job:nexary-job-powerjob')
```

发布到 Maven Central 后，把 `nexaryVersion` 替换为最新 release。

Spring Boot 3.3 / Java 17+ PowerJob bridge provider：

```groovy
def nexaryVersion = "0.5.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ PowerJob bridge provider：

```groovy
def nexaryVersion = "0.5.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot2:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 主验证运行时 PowerJob bridge provider：

```groovy
def nexaryVersion = "0.5.1"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot4:${nexaryVersion}"
```

业务代码只使用 `org.nexary.job.*`，不依赖 provider 内部类或 PowerJob 原生类型。

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
    provider: powerjob
```

## 运行

```bash
./gradlew :nexary-samples:nexary-sample-job-spi-powerjob:test
```

测试会启动 Spring Boot sample，并执行 `SampleBusinessJob`。

当前只演示 Nexary job 与 PowerJob 的触发映射和分片上下文，不声明真实 PowerJob Server 调度、worker 注册完整托管、控制台生命周期、完整回调流程、exactly-once 或运行中强取消。
