package org.nexary.job.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
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

    private NexaryJob job() {
        return new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                return JobResult.success();
            }
        };
    }

    private JobExecutionRunner runner(JobExecutionStore store) {
        return new JobExecutionRunner(List.of(), Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }), store);
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
}
