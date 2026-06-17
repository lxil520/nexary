# nexary-sample-job-spi-scheduler

SPI/provider dependency sample for the Job capability. This module shows only the local scheduler provider.

## Dependency Mode

```groovy
// The sample uses project dependencies inside this repository.
implementation project(':nexary-job:nexary-job-api')
runtimeOnly project(':nexary-job:nexary-job-scheduler')
```

Current verified dependency entry after Maven Central publication:

```groovy
def nexaryVersion = "0.2.0-alpha.2"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-api'
runtimeOnly 'org.nexary:nexary-job-scheduler'
```

Boot2 / Java8 compatibility is still a pending, unpublished target. Planned names are `nexary-job-api-java8` and `nexary-job-scheduler-spring5`; do not copy them as dependencies before independent verification.

Business code uses only `org.nexary.job.*` and does not depend on provider types.

The business job is still an ordinary Spring component:

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

## Configuration

```yaml
nexary:
  job:
    provider: local
```

## Run

```bash
./gradlew :nexary-samples:nexary-sample-job-spi-scheduler:test
```

The test starts the Spring Boot sample and executes `SampleBusinessJob`.
