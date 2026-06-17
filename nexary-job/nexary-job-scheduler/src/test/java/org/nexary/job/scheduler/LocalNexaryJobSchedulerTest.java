package org.nexary.job.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.JobSchedule;
import org.nexary.job.NexaryJob;
import org.nexary.job.execution.JobExecutionPolicy;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionStatus;
import org.nexary.job.execution.JobExecutionStore;
import org.nexary.job.execution.JobExecutionTrigger;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobObservationSupport;
import org.nexary.job.loadbalance.JobLoadBalanceStrategy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

class LocalNexaryJobSchedulerTest {
    @Test
    void executesOnlyShardsAssignedToCurrentWorker() {
        CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        properties.setWorkerId("node-a");
        properties.setWorkers(List.of("node-a", "node-b"));
        properties.setLoadBalance(JobLoadBalanceStrategy.ROUND_ROBIN);
        List<Integer> executedShards = new ArrayList<>();
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                executedShards.add(context.shardIndex());
                return JobResult.success();
            }
        };
        LocalNexaryJobScheduler scheduler = new LocalNexaryJobScheduler(
                taskScheduler,
                java.util.Optional.empty(),
                runner(),
                properties,
                new CacheBackedLocalJobWorkerRegistry(java.util.Optional.empty(), properties));

        scheduler.schedule(job, new JobSchedule("sample-job", "0 */10 * * * *", false, 4));
        taskScheduler.runCaptured();

        assertThat(executedShards).containsExactly(0, 2);
    }

    @Test
    void keepsSingleProcessBehaviorWhenWorkersAreNotConfigured() {
        CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        List<Integer> executedShards = new ArrayList<>();
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                executedShards.add(context.shardIndex());
                return JobResult.success();
            }
        };
        LocalNexaryJobScheduler scheduler = new LocalNexaryJobScheduler(
                taskScheduler,
                java.util.Optional.empty(),
                runner(),
                properties,
                new CacheBackedLocalJobWorkerRegistry(java.util.Optional.empty(), properties));

        scheduler.schedule(job, new JobSchedule("sample-job", "0 */10 * * * *", false, 3));
        taskScheduler.runCaptured();

        assertThat(executedShards).containsExactly(0, 1, 2);
    }

    @Test
    void usesExecutionPolicyLeaseForSingleInstanceLock() {
        CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
        CapturingCacheClient cacheClient = new CapturingCacheClient();
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        List<Integer> executedShards = new ArrayList<>();
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                executedShards.add(context.shardIndex());
                return JobResult.success();
            }
        };
        LocalNexaryJobScheduler scheduler = new LocalNexaryJobScheduler(
                taskScheduler,
                Optional.of(cacheClient),
                runner(),
                properties,
                new CacheBackedLocalJobWorkerRegistry(Optional.empty(), properties));

        scheduler.schedule(job, JobSchedule.single("sample-job", "0 */10 * * * *")
                .withExecutionPolicy(JobExecutionPolicy.defaults().withLockLeaseTime(Duration.ofSeconds(12))));
        taskScheduler.runCaptured();

        assertThat(executedShards).containsExactly(0);
        assertThat(cacheClient.leaseTime).isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    void storesShardSkipRecordsThroughExecutionStore() {
        CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        properties.setWorkerId("node-a");
        properties.setWorkers(List.of("node-a", "node-b"));
        RecordingJobExecutionStore store = new RecordingJobExecutionStore();
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                return JobResult.success();
            }
        };
        LocalNexaryJobScheduler scheduler = new LocalNexaryJobScheduler(
                taskScheduler,
                Optional.empty(),
                runner(store),
                properties,
                new CacheBackedLocalJobWorkerRegistry(Optional.empty(), properties));

        scheduler.schedule(job, new JobSchedule("sample-job", "0 */10 * * * *", false, 4));
        taskScheduler.runCaptured();

        assertThat(store.records)
                .anySatisfy(record -> {
                    assertThat(record.trigger()).isEqualTo(JobExecutionTrigger.SCHEDULED);
                    assertThat(record.status()).isEqualTo(JobExecutionStatus.SKIPPED);
                    assertThat(record.context().shardIndex()).isEqualTo(1);
                    assertThat(record.message()).contains("shard assigned");
                });
    }

    @Test
    void storesSingleInstanceLockSkipThroughExecutionStore() {
        CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
        CapturingCacheClient cacheClient = new CapturingCacheClient();
        cacheClient.lockGranted = false;
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        RecordingJobExecutionStore store = new RecordingJobExecutionStore();
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                return JobResult.success();
            }
        };
        LocalNexaryJobScheduler scheduler = new LocalNexaryJobScheduler(
                taskScheduler,
                Optional.of(cacheClient),
                runner(store),
                properties,
                new CacheBackedLocalJobWorkerRegistry(Optional.empty(), properties));

        scheduler.schedule(job, JobSchedule.single("sample-job", "0 */10 * * * *"));
        taskScheduler.runCaptured();

        assertThat(store.records).singleElement().satisfies(record -> {
            assertThat(record.trigger()).isEqualTo(JobExecutionTrigger.SCHEDULED);
            assertThat(record.status()).isEqualTo(JobExecutionStatus.SKIPPED);
            assertThat(record.message()).contains("single instance lock");
        });
    }

    @Test
    void emitsSchedulerRunAndShardSkipObservationEvents() {
        CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        properties.setWorkerId("node-a");
        properties.setWorkers(List.of("node-a", "node-b"));
        properties.setLoadBalance(JobLoadBalanceStrategy.ROUND_ROBIN);
        RecordingObservationPublisher publisher = new RecordingObservationPublisher();
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "sample-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                return JobResult.success();
            }
        };
        LocalNexaryJobScheduler scheduler = new LocalNexaryJobScheduler(
                taskScheduler,
                Optional.empty(),
                runner(new RecordingJobExecutionStore(), publisher),
                properties,
                new CacheBackedLocalJobWorkerRegistry(Optional.empty(), properties),
                publisher);

        scheduler.schedule(job, new JobSchedule("sample-job", "0 */10 * * * *", false, 4));
        taskScheduler.runCaptured();

        assertThat(publisher.operations()).contains(
                JobObservationSupport.OPERATION_SCHEDULER_RUN,
                JobObservationSupport.OPERATION_SKIP);
        assertThat(publisher.events)
                .filteredOn(event -> event.operation().equals(JobObservationSupport.OPERATION_SKIP))
                .anySatisfy(event -> {
                    assertThat(event.tags().get("skip_reason")).isEqualTo("shard_assignment");
                    assertThat(event.tags().get("shard_presence")).isEqualTo("true");
                });
    }

    private JobExecutionRunner runner() {
        return runner(new RecordingJobExecutionStore());
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

    private static final class CapturingTaskScheduler implements TaskScheduler {
        private Runnable captured;

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            this.captured = task;
            return new CompletedScheduledFuture();
        }

        void runCaptured() {
            captured.run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CompletedScheduledFuture implements ScheduledFuture<Object> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }

    private static final class CapturingCacheClient implements CacheClient {
        private Duration leaseTime;
        private boolean lockGranted = true;

        @Override
        public <T> Optional<T> get(CacheKey key, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public void put(CacheKey key, Object value, Duration ttl) {
        }

        @Override
        public boolean putIfAbsent(CacheKey key, Object value, Duration ttl) {
            return true;
        }

        @Override
        public Map<CacheKey, Object> getAll(Collection<CacheKey> keys) {
            return Map.of();
        }

        @Override
        public void putAll(Map<CacheKey, ?> values, Duration ttl) {
        }

        @Override
        public boolean delete(CacheKey key) {
            return true;
        }

        @Override
        public boolean expire(CacheKey key, Duration ttl) {
            return true;
        }

        @Override
        public Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime) {
            this.leaseTime = leaseTime;
            if (!lockGranted) {
                return Optional.empty();
            }
            return Optional.of(new LockHandle() {
                @Override
                public CacheKey key() {
                    return key;
                }

                @Override
                public String ownerToken() {
                    return "test";
                }

                @Override
                public boolean renew(Duration leaseTime) {
                    return true;
                }

                @Override
                public void close() {
                }
            });
        }

        @Override
        public <T> T cacheAside(CacheKey key, Class<T> type, Duration ttl, Supplier<T> loader) {
            return loader.get();
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

        private List<String> operations() {
            return events.stream().map(NexaryObservationEvent::operation).toList();
        }
    }
}
