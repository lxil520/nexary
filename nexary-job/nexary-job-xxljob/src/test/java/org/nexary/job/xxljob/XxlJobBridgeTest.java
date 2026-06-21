package org.nexary.job.xxljob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobExecutionListener;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionStatus;
import org.nexary.job.execution.JobExecutionStore;
import org.nexary.job.execution.JobExecutionTrigger;
import org.nexary.job.execution.JobObservationSupport;

class XxlJobBridgeTest {
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
        XxlJobBridge bridge = new XxlJobBridge(
                List.of(job),
                runner(List.of(listener), store, publisher),
                new XxlJobProperties(),
                publisher);

        JobExecutionRecord record = bridge.triggerExecution("sample-job", 1, 4);

        assertThat(record.trigger()).isEqualTo(JobExecutionTrigger.BRIDGE);
        assertThat(record.status()).isEqualTo(JobExecutionStatus.SUCCESS);
        assertThat(record.result().status()).isEqualTo(JobResult.JobStatus.SUCCESS);
        assertThat(contextRef.get().shardIndex()).isEqualTo(1);
        assertThat(contextRef.get().shardTotal()).isEqualTo(4);
        assertThat(resultRef.get().status()).isEqualTo(JobResult.JobStatus.SUCCESS);
        assertThat(store.find(record.executionId())).contains(record);
        assertThat(bridge.execution(record.executionId())).contains(record);
        assertThat(publisher.operations()).contains(
                JobObservationSupport.OPERATION_XXLJOB_BRIDGE_TRIGGER,
                JobObservationSupport.OPERATION_TRIGGER,
                JobObservationSupport.OPERATION_EXECUTION_END,
                JobObservationSupport.OPERATION_LISTENER_NOTIFICATION);
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_XXLJOB_BRIDGE_TRIGGER))
                .allSatisfy(event -> {
                    assertThat(event.tags().get("provider")).isEqualTo("xxljob");
                    assertThat(event.tags().get("trigger")).isEqualTo("bridge");
                    assertThat(event.tags().get("shard_presence")).isEqualTo("true");
        });
    }

    @Test
    void bridgeTriggerSkipsWhenStartDeadlineIsExpired() {
        AtomicInteger executions = new AtomicInteger();
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                executions.incrementAndGet();
                return JobResult.success();
            }
        };
        RecordingJobExecutionStore store = new RecordingJobExecutionStore();
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        XxlJobProperties properties = new XxlJobProperties();
        properties.setStartDeadline(Duration.ZERO);
        XxlJobBridge bridge = new XxlJobBridge(
                List.of(job),
                runner(List.of(), store, publisher),
                properties,
                publisher);

        JobExecutionRecord record = bridge.triggerExecution("sample-job", 0, 1);

        assertThat(record.status()).isEqualTo(JobExecutionStatus.SKIPPED);
        assertThat(record.message()).isEqualTo("deadline_exceeded");
        assertThat(executions).hasValue(0);
        assertThat(store.find(record.executionId())).contains(record);
        assertThat(publisher.operations()).contains(
                JobObservationSupport.OPERATION_XXLJOB_BRIDGE_TRIGGER,
                JobObservationSupport.OPERATION_TRIGGER,
                JobObservationSupport.OPERATION_SKIP);
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_SKIP))
                .allSatisfy(event -> {
                    assertThat(event.tags().get("provider")).isEqualTo("xxljob");
                    assertThat(event.tags().get("trigger")).isEqualTo("bridge");
                    assertThat(event.tags().get("skip_reason")).isEqualTo("deadline");
                });
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
        }), store, publisher, "xxljob");
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
            return events.stream().map(NexaryObservationEvent::operation).toList();
        }
    }
}
