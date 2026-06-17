package org.nexary.job.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.internal.JobCompatibilityCollections;

/** In-memory execution store used by local development and tests. */
public class InMemoryJobExecutionStore implements JobExecutionStore {
    private final Map<JobExecutionId, StoredRecord> records = new ConcurrentHashMap<>();
    private final Duration retention;
    private final NexaryObservationPublisher observationPublisher;

    /** Creates an in-memory store with the default one-day retention. */
    public InMemoryJobExecutionStore() {
        this(Duration.ofDays(1));
    }

    /** Creates an in-memory store with explicit retention. */
    public InMemoryJobExecutionStore(Duration retention) {
        this(retention, NexaryObservationPublisher.noop());
    }

    /** Creates an in-memory store with explicit retention and observation publisher. */
    public InMemoryJobExecutionStore(Duration retention, NexaryObservationPublisher observationPublisher) {
        this.retention = normalize(retention);
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public void save(JobExecutionRecord record) {
        if (record == null) {
            return;
        }
        records.put(record.executionId(), new StoredRecord(record, Instant.now().plus(retention)));
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_STORE_SAVE,
                "memory",
                record.trigger(),
                JobObservationSupport.status(record.status()),
                JobCompatibilityCollections.tags(
                        "store", "memory",
                        "shard_presence", record.context().shardTotal() > 1 ? "true" : "false"),
                null);
    }

    @Override
    public Optional<JobExecutionRecord> find(JobExecutionId executionId) {
        if (executionId == null) {
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_FIND,
                    "memory",
                    null,
                    "miss",
                    JobCompatibilityCollections.tags("store", "memory"),
                    null);
            return Optional.empty();
        }
        StoredRecord stored = records.get(executionId);
        if (stored == null) {
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_FIND,
                    "memory",
                    null,
                    "miss",
                    JobCompatibilityCollections.tags("store", "memory"),
                    null);
            return Optional.empty();
        }
        if (stored.expiresAt().isBefore(Instant.now())) {
            records.remove(executionId, stored);
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_RETENTION_EXPIRY,
                    "memory",
                    stored.record().trigger(),
                    "expired",
                    JobCompatibilityCollections.tags("store", "memory"),
                    null);
            return Optional.empty();
        }
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_STORE_FIND,
                "memory",
                stored.record().trigger(),
                JobObservationSupport.status(stored.record().status()),
                JobCompatibilityCollections.tags(
                        "store", "memory",
                        "shard_presence", stored.record().context().shardTotal() > 1 ? "true" : "false"),
                null);
        return Optional.of(stored.record());
    }

    private static Duration normalize(Duration retention) {
        return retention == null || retention.isNegative() || retention.isZero()
                ? Duration.ofDays(1)
                : retention;
    }

    private static final class StoredRecord {
        private final JobExecutionRecord record;
        private final Instant expiresAt;

        private StoredRecord(JobExecutionRecord record, Instant expiresAt) {
            this.record = record;
            this.expiresAt = expiresAt;
        }

        private JobExecutionRecord record() {
            return record;
        }

        private Instant expiresAt() {
            return expiresAt;
        }
    }
}
