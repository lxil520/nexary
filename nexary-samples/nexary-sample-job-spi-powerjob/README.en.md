# nexary-sample-job-spi-powerjob

Job sample for applications that do not use the starter and want exactly one provider dependency. This module shows only the PowerJob trigger provider.

## Dependency Mode

```groovy
// The sample uses project dependencies inside this repository.
implementation project(':nexary-job:nexary-job-api')
runtimeOnly project(':nexary-job:nexary-job-powerjob')
```

After Maven Central publication, replace `nexaryVersion` with the latest release.

Spring Boot 3.3 / Java 17+ PowerJob bridge provider:

```groovy
def nexaryVersion = "0.7.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob:${nexaryVersion}"
```

Spring Boot 2.7 / Java 8+ PowerJob bridge provider:

```groovy
def nexaryVersion = "0.7.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot2:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 primary validation runtime PowerJob bridge provider:

```groovy
def nexaryVersion = "0.7.0"
implementation "com.aweimao:nexary-job-api:${nexaryVersion}"
runtimeOnly "com.aweimao:nexary-job-powerjob-spring-boot4:${nexaryVersion}"
```

Business code uses only `org.nexary.job.*` and does not depend on provider internals or native PowerJob types.

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
    provider: powerjob
```

## Run

```bash
./gradlew :nexary-samples:nexary-sample-job-spi-powerjob:test
```

The test starts the Spring Boot sample and executes `SampleBusinessJob`.

This validates only Nexary job trigger mapping and shard context from PowerJob. It does not claim real PowerJob Server scheduling, fully managed worker registration, console lifecycle, complete callback flow, exactly-once execution, or running cancellation.
