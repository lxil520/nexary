# nexary-sample-job

Starter selector sample for the Job capability.

The goal is to show the Nexary adoption model: business code uses only Nexary job APIs and does not care whether the active provider is local scheduling or the XXL-JOB bridge. Switching providers changes `nexary.job.provider` and profile configuration, not business job handler code.

## What User Code Looks Like

Users write an ordinary Spring component:

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

`SampleBusinessService` is an ordinary business service. In a real project it can inject RPC, MQ, cache, repositories, or other collaborators as usual. Provider selection, configuration binding, local scheduling, and XXL-JOB bridge wiring do not belong in the business job class.

## Dependency Mode

This module uses starter mode. The currently verified combination is Spring Boot 3.3.x + Java 17+:

```groovy
// Verified: Spring Boot 3.3.x + Java 17+
implementation project(':nexary-boot:nexary-job-spring-boot-starter')
```

After Maven Central publication, replace `nexaryVersion` with the latest release. Spring Boot 3.3 / Java 17+ starter entry:

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation platform("org.nexary:nexary-bom:${nexaryVersion}")
implementation 'org.nexary:nexary-job-spring-boot-starter'
```

Spring Boot 2.7 / Java 8+ starter entry:

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-spring-boot2-starter:${nexaryVersion}"
```

Spring Boot 4.1 / Java 21 primary validation runtime starter entry:

```groovy
def nexaryVersion = "0.2.0-SNAPSHOT"
implementation "org.nexary:nexary-job-spring-boot4-starter:${nexaryVersion}"
```

The starter aggregates the current Job API, local provider, and XXL-JOB bridge provider. Users select the provider in configuration:

```yaml
nexary:
  job:
    provider: local
```

Switch to the XXL-JOB bridge-shaped mode:

```yaml
nexary:
  job:
    provider: xxljob
```

Execution records use an in-memory store by default. Enable the Redis durable store when `JobExecutionRecord` lookup must survive process or store object recreation; business job handlers do not change:

```yaml
nexary:
  job:
    execution:
      store:
        redis:
          enabled: true
          retention: 1d
```

Observation is also kept out of business job handlers. When the application provides a `NexaryObservationListener` or `NexaryObservationPublisher`, the framework emits events such as `job.trigger`, `job.execution.end`, `job.retry.attempt`, `job.execution.skip`, `job.store.save`, `job.store.find`, `job.scheduler.run`, and `job.xxljob.bridge.trigger`. Tags use bounded dimensions such as `provider`, `trigger`, `status`, `skip_reason`, `shard_presence`, and `failure_category`; do not use execution id, parameters, payload, exception messages, or stack traces as metric tags.

## Directory Guide

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

Users mainly read `SampleBusinessJob` and their own business service.

The business job depends only on:

- `NexaryJob`
- `JobContext`
- `JobResult`

When an application needs to trace one execution, it can call `NexaryJobOperations.triggerExecution(...)` and read `JobExecutionRecord`, which includes execution id, trigger source, status, attempts, duration, and error details. The business job handler itself does not load these settings or records.

This starter sample does not contain provider wiring packages. Provider wiring belongs to Nexary starter/provider modules.

## Local Provider Run

The default provider is `local`:

```bash
./gradlew :nexary-samples:nexary-sample-job:test
```

The test starts the Spring Boot sample and executes `SampleBusinessJob`.

## XXL-JOB Bridge Run

```bash
./gradlew :nexary-samples:nexary-sample-job:test --tests org.nexary.samples.job.XxlJobSampleApplicationTest
```

This demonstrates only trigger mapping between Nexary job and the XXL-JOB bridge. It does not claim real XXL-JOB Admin scheduling, executor registration, callback lifecycle, or platform-triggered execution.

## Processor-Style

Processor-style remains a non-web job process skeleton:

```bash
./gradlew :nexary-samples:nexary-sample-job:runProcessor
```

The processor sample only shows non-web startup and component-scanned jobs. The first-copy user reference is still `ProcessorBusinessJob`.

## SPI Provider Samples

SPI/provider dependency mode is not mixed into this module. Each provider has its own sample module:

- `nexary-sample-job-spi-scheduler`: `nexary-job-api` + `nexary-job-scheduler`
- `nexary-sample-job-spi-xxljob`: `nexary-job-api` + `nexary-job-xxljob`

This keeps the two adoption modes clear:

- starter selector: depend on the starter and select a provider through `nexary.job.provider`
- SPI/provider: depend on the API and exactly one concrete provider module

Version matrix:

| Spring Boot | JDK | Status | Entry |
| --- | --- | --- | --- |
| 3.3.x | Java 17+ | currently verified | `nexary-job-spring-boot-starter` |
| 2.7.x | Java 8+ | verified bounded scope | `nexary-job-spring-boot2-starter` |
| 4.1.x | Official minimum JDK follows Spring documentation; Java 21 is Nexary's primary validation runtime | verified Boot4 entry | `nexary-job-spring-boot4-starter` |

