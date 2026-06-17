# nexary-sample-job-spi-scheduler

SPI/provider dependency sample for the Job capability. This module shows only the local scheduler provider.

## Dependency Mode

```groovy
// The sample uses project dependencies inside this repository.
implementation project(':nexary-job:nexary-job-api')
runtimeOnly project(':nexary-job:nexary-job-scheduler')

// Maven Central artifacts for the currently verified Spring Boot 3.3.x + Java 17+ line:
// implementation 'org.nexary:nexary-job-api'
// runtimeOnly 'org.nexary:nexary-job-scheduler'
//
// Boot2 / Java8 compatibility target, pending verification and unpublished:
// implementation 'org.nexary:nexary-job-api-java8'
// runtimeOnly 'org.nexary:nexary-job-scheduler-spring5'
```

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
