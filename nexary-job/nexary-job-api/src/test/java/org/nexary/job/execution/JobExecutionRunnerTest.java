package org.nexary.job.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobExecutionListener;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;

class JobExecutionRunnerTest {
    @Test
    void retriesFailedAttemptAndRecordsFinalSuccess() {
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        JobExecutionRunner runner = runner(publisher);
        AtomicInteger attempts = new AtomicInteger();
        NexaryJob job = job(context -> attempts.incrementAndGet() == 1
                ? JobResult.failed("temporary failure")
                : JobResult.success());

        JobExecutionRecord record = runner.execute(job, request(JobExecutionPolicy.defaults()
                .withRetryAttempts(1)
                .withRetryBackoff(Duration.ZERO)));

        assertThat(record.status()).isEqualTo(JobExecutionStatus.SUCCESS);
        assertThat(record.attempts()).isEqualTo(2);
        assertThat(attempts).hasValue(2);
        assertThat(runner.record(record.executionId())).contains(record);
        assertThat(publisher.operations()).contains(
                JobObservationSupport.OPERATION_TRIGGER,
                JobObservationSupport.OPERATION_EXECUTION_START,
                JobObservationSupport.OPERATION_RETRY_ATTEMPT,
                JobObservationSupport.OPERATION_EXECUTION_END,
                JobObservationSupport.OPERATION_STORE_SAVE,
                JobObservationSupport.OPERATION_STORE_FIND);
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_RETRY_ATTEMPT))
                .extracting(event -> event.tags().get("retry_phase"))
                .contains("first", "final");
        assertBoundedTags(publisher.events);
    }

    @Test
    void recordsTimeoutWhenAttemptExceedsPolicy() {
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        JobExecutionRunner runner = runner(publisher);
        NexaryJob job = job(context -> {
            Thread.sleep(200);
            return JobResult.success();
        });

        JobExecutionRecord record = runner.execute(job, request(JobExecutionPolicy.defaults()
                .withTimeout(Duration.ofMillis(20))));

        assertThat(record.status()).isEqualTo(JobExecutionStatus.TIMEOUT);
        assertThat(record.attempts()).isEqualTo(1);
        assertThat(publisher.operations()).contains(JobObservationSupport.OPERATION_TIMEOUT);
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_TIMEOUT))
                .allSatisfy(event -> assertThat(event.tags().get("failure_category")).isEqualTo("timeout"));
        assertBoundedTags(publisher.events);
    }

    @Test
    void skipsScheduledMisfireWhenPolicyRequiresSkip() {
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        JobExecutionRunner runner = runner(publisher);
        JobContext context = new JobContext("sample-job", Instant.now().minus(Duration.ofMinutes(10)), 0, 1, null);
        JobExecutionPolicy policy = JobExecutionPolicy.defaults()
                .withMisfirePolicy(JobMisfirePolicy.SKIP)
                .withMisfireThreshold(Duration.ofSeconds(1));

        JobExecutionRecord record = runner.execute(job(ignored -> JobResult.success()),
                new JobExecutionRequest(null, JobExecutionTrigger.SCHEDULED, context, policy));

        assertThat(record.status()).isEqualTo(JobExecutionStatus.SKIPPED);
        assertThat(record.attempts()).isZero();
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_SKIP))
                .allSatisfy(event -> assertThat(event.tags().get("skip_reason")).isEqualTo("misfire"));
    }

    @Test
    void skipsWhenStartDeadlineIsExpiredBeforeExecution() {
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        JobExecutionRunner runner = runner(publisher);
        AtomicInteger executions = new AtomicInteger();
        JobExecutionPolicy policy = JobExecutionPolicy.defaults().withStartDeadline(Duration.ZERO);

        JobExecutionRecord record = runner.execute(job(context -> {
            executions.incrementAndGet();
            return JobResult.success();
        }), request(policy));

        assertThat(record.status()).isEqualTo(JobExecutionStatus.SKIPPED);
        assertThat(record.message()).isEqualTo("deadline_exceeded");
        assertThat(executions).hasValue(0);
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_SKIP))
                .allSatisfy(event -> assertThat(event.tags().get("skip_reason")).isEqualTo("deadline"));
    }

    @Test
    void skipsWhenBulkheadLimitIsReachedForJobResource() throws Exception {
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        JobExecutionRunner runner = runner(publisher);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        NexaryJob job = job(context -> {
            entered.countDown();
            release.await(1, TimeUnit.SECONDS);
            return JobResult.success();
        });
        JobExecutionPolicy policy = JobExecutionPolicy.defaults().withMaxConcurrentExecutions(1);

        CompletableFuture<JobExecutionRecord> first =
                CompletableFuture.supplyAsync(() -> runner.execute(job, request(policy, 0, 2)));
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        JobExecutionRecord second = runner.execute(job, request(policy, 1, 2));
        release.countDown();
        JobExecutionRecord firstRecord = first.get(1, TimeUnit.SECONDS);

        assertThat(firstRecord.status()).isEqualTo(JobExecutionStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(JobExecutionStatus.SKIPPED);
        assertThat(second.message()).contains("bulkhead");
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_SKIP))
                .allSatisfy(event -> assertThat(event.tags().get("skip_reason")).isEqualTo("bulkhead"));
    }

    @Test
    void skipsConcurrentExecutionForSameJobShardWhenPolicyRequiresIt() throws Exception {
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        JobExecutionRunner runner = runner(publisher);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        NexaryJob job = job(context -> {
            entered.countDown();
            release.await(1, TimeUnit.SECONDS);
            return JobResult.success();
        });
        JobExecutionPolicy policy = JobExecutionPolicy.defaults()
                .withConcurrencyPolicy(JobConcurrencyPolicy.SKIP_IF_RUNNING);

        CompletableFuture<JobExecutionRecord> first = CompletableFuture.supplyAsync(() -> runner.execute(job, request(policy)));
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        JobExecutionRecord second = runner.execute(job, request(policy));
        release.countDown();
        JobExecutionRecord firstRecord = first.get(1, TimeUnit.SECONDS);

        assertThat(firstRecord.status()).isEqualTo(JobExecutionStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(JobExecutionStatus.SKIPPED);
        assertThat(second.message()).contains("already running");
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_SKIP))
                .allSatisfy(event -> assertThat(event.tags().get("skip_reason")).isEqualTo("concurrency"));
    }

    @Test
    void emitsListenerNotificationWithoutListenerIdentityTag() {
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        JobExecutionListener listener = (context, result, error) -> {
        };
        JobExecutionRunner runner = runner(publisher, Collections.singletonList(listener));

        runner.execute(job(context -> JobResult.success()), request(JobExecutionPolicy.defaults()));

        assertThat(publisher.operations()).contains(JobObservationSupport.OPERATION_LISTENER_NOTIFICATION);
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_LISTENER_NOTIFICATION))
                .allSatisfy(event -> assertThat(event.tags()).doesNotContainKeys("listener", "listener_class"));
    }

    private JobExecutionRunner runner() {
        return runner(new RecordingObservationPublisher());
    }

    private JobExecutionRunner runner(RecordingObservationPublisher publisher) {
        return runner(publisher, Collections.emptyList());
    }

    private JobExecutionRunner runner(RecordingObservationPublisher publisher, List<JobExecutionListener> listeners) {
        return new JobExecutionRunner(listeners, Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }), new InMemoryJobExecutionStore(Duration.ofDays(1), publisher), publisher, "local");
    }

    private JobExecutionRequest request(JobExecutionPolicy policy) {
        return request(policy, 0, 1);
    }

    private JobExecutionRequest request(JobExecutionPolicy policy, int shardIndex, int shardTotal) {
        JobContext context = new JobContext("sample-job", Instant.now(), shardIndex, shardTotal, null);
        return new JobExecutionRequest(null, JobExecutionTrigger.DIRECT, context, policy);
    }

    private NexaryJob job(ThrowingJob action) {
        return new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) throws Exception {
                return action.execute(context);
            }
        };
    }

    private static void assertBoundedTags(List<NexaryObservationEvent> events) {
        assertThat(events)
                .allSatisfy(event -> {
                    assertThat(event.tags().keySet()).allMatch(JobExecutionRunnerTest::allowedTag);
                    assertThat(event.tags()).doesNotContainKeys(
                            "execution_id",
                            "parameter",
                            "payload",
                            "exception_message",
                            "stack_trace",
                            "job_name",
                            "cache_key",
                            "message_id",
                            "lock_token",
                            "fencing_token");
                });
    }

    private static boolean allowedTag(String key) {
        return Arrays.asList(
                        "capability",
                        "operation",
                        "provider",
                        "trigger",
                        "status",
                        "skip_reason",
                        "shard_presence",
                        "failure_category",
                        "retry_attempt_bucket",
                        "retry_phase",
                        "store")
                .contains(key);
    }

    private static final class RecordingObservationPublisher implements NexaryObservationPublisher {
        private final List<NexaryObservationEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void publish(NexaryObservationEvent event) {
            events.add(event);
        }

        private List<String> operations() {
            return events.stream().map(NexaryObservationEvent::operation).collect(java.util.stream.Collectors.toList());
        }
    }

    @FunctionalInterface
    private interface ThrowingJob {
        JobResult execute(JobContext context) throws Exception;
    }
}
