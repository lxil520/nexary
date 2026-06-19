# nexary-sample-job-spi-xxljob

non-starter dependency sample for the Job capability. This module shows only the XXL-JOB bridge provider.

## Dependency Mode

```groovy
// The sample uses project dependencies inside this repository.
implementation project(':nexary-job:nexary-job-api')
runtimeOnly project(':nexary-job:nexary-job-xxljob')
```

After Maven Central publication, replace `nexaryVersion` with the latest release.

Spring Boot 3.3 / Java 17+ XXL-JOB bridge provider:

```groovy
def nexaryVersion = "0.3.0"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-xxljob:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ XXL-JOB bridge provider:

```groovy
def nexaryVersion = "0.3.0"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-xxljob-spring-boot2:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 primary validation runtime XXL-JOB bridge provider:

```groovy
def nexaryVersion = "0.3.0"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-xxljob-spring-boot4:${nexaryVersion}"
```

Business code uses only `org.nexary.job.*` and does not depend on provider internals or native XXL-JOB types.

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
    provider: xxljob
```

## Run

```bash
./gradlew :nexary-samples:nexary-sample-job-spi-xxljob:test
```

The test starts the Spring Boot sample and executes `SampleBusinessJob`.

This validates only trigger-mapping trigger mapping. It does not claim real XXL-JOB Admin scheduling, executor registration, callback lifecycle, or platform-triggered execution.
