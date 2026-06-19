# nexary-sample-job-spi-scheduler

This sample skips the starter and adds the Job API plus the local scheduler provider by hand.

## Dependency Mode

```groovy
// The sample uses project dependencies inside this repository.
implementation project(':nexary-job:nexary-job-api')
runtimeOnly project(':nexary-job:nexary-job-scheduler')
```

After Maven Central publication, replace `nexaryVersion` with the latest release.

Spring Boot 3.3 / Java 17+ local scheduler provider:

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-scheduler:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ local scheduler provider:

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-scheduler-spring-boot2:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 primary validation runtime local scheduler provider:

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-api:${nexaryVersion}"
runtimeOnly "org.nexary:nexary-job-scheduler-spring-boot4:${nexaryVersion}"
```

Business code uses only `org.nexary.job.*` and does not depend on local scheduler types.

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
    scheduler:
      schedules:
        - job-name: sample-business-job
          cron: "0 */10 * * * *"
          enabled: true
          single-instance: true
          shard-total: 1
```

`job-name` must match `NexaryJob.name()`.

## Run

```bash
./gradlew :nexary-samples:nexary-sample-job-spi-scheduler:test
```

The test starts the Spring Boot sample and executes `SampleBusinessJob`.
