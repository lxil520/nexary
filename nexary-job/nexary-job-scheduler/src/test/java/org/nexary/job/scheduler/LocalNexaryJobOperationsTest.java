package org.nexary.job.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.JobSchedule;
import org.nexary.job.NexaryJob;
import org.nexary.job.NexaryJobScheduler;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionStatus;
import org.nexary.job.execution.JobExecutionStore;
import org.nexary.job.execution.JobExecutionTrigger;
import org.nexary.job.execution.JobObservationSupport;

class LocalNexaryJobOperationsTest {
    @Test
    void directTriggerStoresExecutionRecordThroughExecutionStore() {
        RecordingJobExecutionStore store = new RecordingJobExecutionStore();
        LocalNexaryJobOperations operations = new LocalNexaryJobOperations(
                List.of(job()),
                new NoopScheduler(),
                runner(store),
                new LocalJobSchedulerProperties());

        JobExecutionRecord record = operations.triggerExecution("sample-job", 2, 5);

        assertThat(record.trigger()).isEqualTo(JobExecutionTrigger.DIRECT);
        assertThat(record.status()).isEqualTo(JobExecutionStatus.SUCCESS);
        assertThat(record.context().shardIndex()).isEqualTo(2);
        assertThat(record.context().shardTotal()).isEqualTo(5);
        assertThat(store.find(record.executionId())).contains(record);
        assertThat(operations.execution(record.executionId())).contains(record);
    }

    @Test
    void directTriggerSkipsWhenStartDeadlineIsExpired() {
        AtomicInteger executions = new AtomicInteger();
        RecordingJobExecutionStore store = new RecordingJobExecutionStore();
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        properties.setStartDeadline(Duration.ZERO);
        LocalNexaryJobOperations operations = new LocalNexaryJobOperations(
                List.of(job("sample-job", executions)),
                new NoopScheduler(),
                runner(store, publisher),
                properties);

        JobExecutionRecord record = operations.triggerExecution("sample-job", 0, 1);

        assertThat(record.status()).isEqualTo(JobExecutionStatus.SKIPPED);
        assertThat(record.message()).isEqualTo("deadline_exceeded");
        assertThat(executions).hasValue(0);
        assertThat(store.find(record.executionId())).contains(record);
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_SKIP))
                .allSatisfy(event -> assertThat(event.tags().get("skip_reason")).isEqualTo("deadline"));
    }

    private NexaryJob job() {
        return job("sample-job", new AtomicInteger());
    }

    private NexaryJob job(String name, AtomicInteger executions) {
        return new NexaryJob() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public JobResult execute(JobContext context) {
                executions.incrementAndGet();
                return JobResult.success();
            }
        };
    }

    private JobExecutionRunner runner(JobExecutionStore store) {
        return runner(store, NexaryObservationPublisher.noop());
    }

    private JobExecutionRunner runner(JobExecutionStore store, NexaryObservationPublisher publisher) {
        return new JobExecutionRunner(List.of(), Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }), store, publisher, "local");
    }

    private static final class NoopScheduler implements NexaryJobScheduler {
        @Override
        public void schedule(NexaryJob job, JobSchedule schedule) {
        }

        @Override
        public boolean cancel(String jobName) {
            return false;
        }
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
    }
}
