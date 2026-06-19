package org.nexary.job.powerjob.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobExecutionListener;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.nexary.job.execution.JobConcurrencyPolicy;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionStatus;
import org.nexary.job.execution.JobExecutionStore;
import org.nexary.job.execution.JobExecutionTrigger;
import org.nexary.job.execution.JobObservationSupport;

class PowerJobBridgeTest {
    @Test
    void triggersNamedJobAndNotifiesListeners() {
        AtomicReference<JobContext> contextRef = new AtomicReference<>();
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                contextRef.set(context);
                return JobResult.success();
            }
        };
        AtomicReference<JobResult> resultRef = new AtomicReference<>();
        JobExecutionListener listener = (context, result, error) -> resultRef.set(result);
        RecordingJobExecutionStore store = new RecordingJobExecutionStore();
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        PowerJobBridge bridge = new PowerJobBridge(
                Collections.singletonList(job),
                runner(Collections.singletonList(listener), store, publisher),
                new PowerJobProperties(),
                publisher);

        JobExecutionRecord record = bridge.triggerExecution(
                new PowerJobBridgeRequest("sample-job", 1, 4, "instance-1", "task-1", "task-a"));

        assertThat(record.trigger()).isEqualTo(JobExecutionTrigger.BRIDGE);
        assertThat(record.status()).isEqualTo(JobExecutionStatus.SUCCESS);
        assertThat(record.result().status()).isEqualTo(JobResult.JobStatus.SUCCESS);
        assertThat(contextRef.get().shardIndex()).isEqualTo(1);
        assertThat(contextRef.get().shardTotal()).isEqualTo(4);
        assertThat(record.providerMetadata()).containsEntry("provider", "powerjob");
        assertThat(record.providerMetadata()).containsEntry("instance_id", "instance-1");
        assertThat(record.providerMetadata()).containsEntry("task_id", "task-1");
        assertThat(record.providerMetadata()).containsEntry("task_name", "task-a");
        assertThat(resultRef.get().status()).isEqualTo(JobResult.JobStatus.SUCCESS);
        assertThat(store.find(record.executionId())).contains(record);
        assertThat(bridge.execution(record.executionId())).contains(record);
        assertThat(publisher.operations()).contains(
                JobObservationSupport.OPERATION_POWERJOB_BRIDGE_TRIGGER,
                JobObservationSupport.OPERATION_TRIGGER,
                JobObservationSupport.OPERATION_EXECUTION_END,
                JobObservationSupport.OPERATION_LISTENER_NOTIFICATION);
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_POWERJOB_BRIDGE_TRIGGER))
                .allSatisfy(event -> {
                    assertThat(event.tags().get("provider")).isEqualTo("powerjob");
                    assertThat(event.tags().get("trigger")).isEqualTo("bridge");
                    assertThat(event.tags().get("shard_presence")).isEqualTo("true");
                });
    }

    @Test
    void operationsTriggerUsesBridgeExecutionPipeline() {
        PowerJobBridge bridge = new PowerJobBridge(
                Collections.singletonList(successJob("direct-job")),
                runner(Collections.emptyList(), new RecordingJobExecutionStore()),
                new PowerJobProperties());
        PowerJobOperations operations = new PowerJobOperations(bridge);

        JobExecutionRecord record = operations.triggerExecution("direct-job", 0, 1);

        assertThat(operations.provider()).isEqualTo("powerjob");
        assertThat(operations.supportsScheduling()).isFalse();
        assertThat(record.status()).isEqualTo(JobExecutionStatus.SUCCESS);
        assertThat(record.trigger()).isEqualTo(JobExecutionTrigger.BRIDGE);
    }

    @Test
    void recordsFailureFromSharedRunner() {
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "failing-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                throw new IllegalStateException("failure is mapped to record");
            }
        };
        RecordingJobExecutionStore store = new RecordingJobExecutionStore();
        PowerJobBridge bridge = new PowerJobBridge(
                Collections.singletonList(job),
                runner(Collections.emptyList(), store),
                new PowerJobProperties());

        JobExecutionRecord record = bridge.triggerExecution("failing-job", 0, 1);

        assertThat(record.status()).isEqualTo(JobExecutionStatus.FAILED);
        assertThat(record.result().status()).isEqualTo(JobResult.JobStatus.FAILED);
        assertThat(store.find(record.executionId())).contains(record);
    }

    @Test
    void recordsTimeoutFromSharedRunner() {
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "slow-job";
            }

            @Override
            public JobResult execute(JobContext context) throws Exception {
                Thread.sleep(200);
                return JobResult.success();
            }
        };
        PowerJobProperties properties = new PowerJobProperties();
        properties.setExecutionTimeout(java.time.Duration.ofMillis(10));
        RecordingJobExecutionStore store = new RecordingJobExecutionStore();
        PowerJobBridge bridge = new PowerJobBridge(
                Collections.singletonList(job),
                runner(Collections.emptyList(), store),
                properties);

        JobExecutionRecord record = bridge.triggerExecution("slow-job", 0, 1);

        assertThat(record.status()).isEqualTo(JobExecutionStatus.TIMEOUT);
        assertThat(record.message()).contains("timed out");
        assertThat(store.find(record.executionId())).contains(record);
    }

    @Test
    void recordsConcurrencySkipFromSharedRunner() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "single-job";
            }

            @Override
            public JobResult execute(JobContext context) throws Exception {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return JobResult.success();
            }
        };
        PowerJobProperties properties = new PowerJobProperties();
        properties.setConcurrencyPolicy(JobConcurrencyPolicy.SKIP_IF_RUNNING);
        RecordingJobExecutionStore store = new RecordingJobExecutionStore();
        PowerJobBridge bridge = new PowerJobBridge(
                Collections.singletonList(job),
                runner(Collections.emptyList(), store),
                properties);

        ExecutorService triggerExecutor = Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<JobExecutionRecord> running =
                    triggerExecutor.submit(() -> bridge.triggerExecution("single-job", 0, 1));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            JobExecutionRecord skipped = bridge.triggerExecution("single-job", 0, 1);
            release.countDown();
            running.get(1, TimeUnit.SECONDS);

            assertThat(skipped.status()).isEqualTo(JobExecutionStatus.SKIPPED);
            assertThat(skipped.message()).contains("already running");
            assertThat(store.find(skipped.executionId())).contains(skipped);
        } finally {
            release.countDown();
            triggerExecutor.shutdownNow();
        }
    }

    private NexaryJob successJob(String name) {
        return new NexaryJob() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public JobResult execute(JobContext context) {
                return JobResult.success();
            }
        };
    }

    private JobExecutionRunner runner(List<JobExecutionListener> listeners, JobExecutionStore store) {
        return runner(listeners, store, NexaryObservationPublisher.noop());
    }

    private JobExecutionRunner runner(
            List<JobExecutionListener> listeners,
            JobExecutionStore store,
            NexaryObservationPublisher publisher) {
        return new JobExecutionRunner(listeners, Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }), store, publisher, "powerjob");
    }

    private static final class RecordingJobExecutionStore implements JobExecutionStore {
        private final List<JobExecutionRecord> records = new ArrayList<>();

        @Override
        public void save(JobExecutionRecord record) {
            records.add(record);
        }

        @Override
        public Optional<JobExecutionRecord> find(JobExecutionId executionId) {
            return records.stream().filter(record -> record.executionId().equals(executionId)).findFirst();
        }
    }

    private static final class RecordingObservationPublisher implements NexaryObservationPublisher {
        private final List<NexaryObservationEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void publish(NexaryObservationEvent event) {
            events.add(event);
        }

        private List<String> operations() {
            List<String> operations = new ArrayList<>();
            for (NexaryObservationEvent event : events) {
                operations.add(event.operation());
            }
            return operations;
        }
    }
}
