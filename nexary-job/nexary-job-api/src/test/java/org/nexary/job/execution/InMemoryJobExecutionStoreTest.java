package org.nexary.job.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;

class InMemoryJobExecutionStoreTest {
    @Test
    void retainsRecordUntilConfiguredRetentionExpires() throws Exception {
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        InMemoryJobExecutionStore store = new InMemoryJobExecutionStore(Duration.ofMillis(60), publisher);
        JobExecutionRecord record = record();

        store.save(record);

        assertThat(store.find(record.executionId())).contains(record);
        Thread.sleep(90);
        assertThat(store.find(record.executionId())).isEmpty();
        assertThat(publisher.operations()).contains(
                JobObservationSupport.OPERATION_STORE_SAVE,
                JobObservationSupport.OPERATION_STORE_FIND,
                JobObservationSupport.OPERATION_STORE_RETENTION_EXPIRY);
        assertThat(publisher.events)
                .allSatisfy(event -> assertThat(event.tags()).doesNotContainKeys(
                        "execution_id", "payload", "exception_message", "stack_trace", "job_name"));
    }

    private JobExecutionRecord record() {
        Instant now = Instant.now();
        return new JobExecutionRecord(
                JobExecutionId.generate(),
                JobExecutionTrigger.DIRECT,
                new JobContext("sample-job", now, 0, 1, null),
                JobExecutionStatus.SUCCESS,
                JobResult.success(),
                1,
                now,
                now,
                Duration.ZERO,
                "ok",
                "");
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
